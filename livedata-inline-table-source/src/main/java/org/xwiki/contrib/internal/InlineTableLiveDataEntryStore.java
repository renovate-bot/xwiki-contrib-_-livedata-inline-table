/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.cache.CacheException;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.livedata.LiveData;
import org.xwiki.livedata.LiveDataEntryStore;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.LiveDataQuery;
import org.xwiki.livedata.LiveDataQuery.Constraint;
import org.xwiki.livedata.LiveDataQuery.Filter;
import org.xwiki.livedata.LiveDataQuery.SortEntry;
import org.xwiki.livedata.LiveDataSource;
import org.xwiki.text.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dedicated {@link LiveDataEntryStore} for the {@link InlineTableLiveDataSource}. This component reads the JSON sent by
 * the LiveData user and sends its contents back through LiveData.
 * 
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
@Named(InlineTableLiveDataSource.ID)
public class InlineTableLiveDataEntryStore implements LiveDataEntryStore
{

    private static final String TEXT_ID = "text.";

    private static final String DATE_ID = "date.";

    @Inject
    private ComponentManager componentManager;

    @Inject
    private InlineTableCache inlineTableCache;

    @Inject
    private Logger logger;

    @Override
    public Optional<Map<String, Object>> get(Object entryId) throws LiveDataException
    {
        return Optional.empty();
    }

    @Override
    public LiveData get(LiveDataQuery query) throws LiveDataException
    {
        LiveData liveData = new LiveData();
        List<Map<String, Object>> liveDataEntries = liveData.getEntries();

        LiveDataSource liveDataSource;
        try {
            liveDataSource = componentManager.getInstance(LiveDataSource.class, InlineTableLiveDataSource.ID);
        } catch (ComponentLookupException e) {
            throw new LiveDataException("Could not find InlineTableLiveDataSource component.");
        }

        // Decode the received entries.
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode entriesNode = null;
        String entriesParameter =
            ((InlineTableLiveDataSource) liveDataSource).getParameters().get("entries").toString();
        logger.debug("Received entries parameter: " + entriesParameter);
        String entriesB64 = getEntriesB64(entriesParameter);
        logger.debug("Attemtping to decode and decompress the entries.");
        try {
            String decodedJson = decompressString(Base64.getUrlDecoder().decode(entriesB64));
            logger.debug("Decoded entries json: " + decodedJson);
            entriesNode = objectMapper.readTree(decodedJson);
        } catch (IOException e) {
            if (entriesParameter.equals(entriesB64)) {
                throw new LiveDataException(
                    "Failed to retrieve entries. Received entries parameter is not in cache or is not valid.", e);
            }
            throw new LiveDataException("Failed to retrieve entries. The data was found in cache but is not valid.", e);
        }

        // Organize the filter so we can access them by field.
        Map<String, Filter> filters = new HashMap<>();
        for (Filter filter : query.getFilters()) {
            filters.put(filter.getProperty(), filter);
        }

        // Parse the decoded JSON.
        logger.debug("Parsing entries JSON.");
        int i = 0;
        for (JsonNode entry : entriesNode) {
            Map<String, Object> ldEntry = new HashMap<>();

            logger.debug("[" + i + "] Parsing entry.");
            // Add our generated ID. Filtering or sorting should not change this.
            ldEntry.put("_inline_id", i);

            // Keep track of whether we should reject this entry due to filtering.
            boolean filtered = false;

            // Iterate through the fields of this entry.
            for (Iterator<String> it = entry.fieldNames(); it.hasNext();) {
                String field = it.next();

                String textValue = entry.get(field).asText();
                // Check the filters for this field.

                logger.debug("[" + i + "] Processing field " + field + " of value: " + textValue);

                if (field.contains(DATE_ID)) {
                    logger.debug(
                        "[" + i + " - " + field + "] Date marker found, trying to parse value as unix timestamp.");
                    Filter filter = filters.get(StringUtils.substringAfter(field, DATE_ID));
                    Long numericValue = null;

                    try {
                        numericValue = Long.parseLong(textValue);
                        logger.debug("[" + i + " - " + field + "] Parsed timestamp: " + numericValue);
                    } catch (NumberFormatException e) {
                        logger.debug("[" + i + " - " + field + "] Failed to parse timestamp.");
                    }

                    if (filter != null && numericValue != null && field.startsWith(DATE_ID)) {
                        logger.debug("[" + i + " - " + field + "] Checking for filtering.");
                        for (Constraint constraint : filter.getConstraints()) {
                            switch (constraint.getOperator()) {
                                // We consider "between" to be the default operator.
                                case "between":
                                    logger.debug(
                                        "[" + i + " - " + field + "] Found a 'between' filter constraint with value: "
                                            + constraint.getValue().toString());
                                    logger
                                        .debug("[" + i + " - " + field + "] Splitting value with '/' as a delimiter.");
                                    String[] dates = StringUtils.split(constraint.getValue().toString(), "/");

                                    logger.debug("[" + i + " - " + field + "] Found " + dates.length + " parts.");
                                    if (dates.length == 2) {
                                        String beginDateString = dates[0];
                                        String endDateString = dates[1];

                                        logger.debug("[" + i + " - " + field + "] Parsing " + beginDateString + " and "
                                            + endDateString + " as ISO8601 dates.");

                                        Long beginDate =
                                            Instant.from(DateTimeFormatter.ISO_INSTANT.parse(beginDateString))
                                                .getEpochSecond();
                                        Long endDate = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(endDateString))
                                            .getEpochSecond();

                                        logger
                                            .debug("[" + i + " - " + field + "] Parsed date range as unix timestamps: "
                                                + beginDate + " - " + endDate + ".");

                                        if (beginDate != null && endDate != null) {

                                            logger.debug("[" + i + " - " + field
                                                + "] Checking if field value is contained in range.");
                                            if (beginDate > numericValue || numericValue > endDate) {
                                                logger.debug("[" + i + " - " + field
                                                    + "] Field value is not contained in range, filtering this entry.");
                                                filtered = true;
                                            }
                                        }
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                } else if (field.contains(TEXT_ID)) {
                    logger.debug("[" + i + " - " + field + "] Text marker found, checking for text filters.");
                    Filter filter = filters.get(StringUtils.substringAfter(field, TEXT_ID));
                    if (filter != null && field.startsWith(TEXT_ID)) {
                        for (Constraint constraint : filter.getConstraints()) {
                            switch (constraint.getOperator()) {
                                case "startsWith":
                                    logger.debug("[" + i + " - " + field
                                        + "] Found a 'startsWith' filter constraint with value: "
                                        + constraint.getValue().toString());
                                    if (!StringUtils.startsWithIgnoreCase(textValue,
                                        constraint.getValue().toString())) {
                                        logger.debug("[" + i + " - " + field
                                            + "] Entry's field value does not start with given filter value. Filtering entry.");
                                        filtered = true;
                                    }
                                    break;
                                case "equals":
                                    logger.debug(
                                        "[" + i + " - " + field + "] Found a 'equals' filter constraint with value: "
                                            + constraint.getValue().toString());
                                    if (!textValue.equals(constraint.getValue().toString())) {
                                        logger.debug("[" + i + " - " + field
                                            + "] Entry's field is not the same as the given filter value. Filtering entry.");
                                        filtered = true;
                                    }
                                    break;
                                case "contains":
                                    logger.debug(
                                        "[" + i + " - " + field + "] Found a 'contains' filter constraint with value: "
                                            + constraint.getValue().toString());
                                    if (!StringUtils.containsIgnoreCase(textValue, constraint.getValue().toString())) {
                                        logger.debug("[" + i + " - " + field
                                            + "] Entry's field value does contain the given filter value. Filtering entry.");
                                        filtered = true;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }

                Number numericValue = entry.get(field).numberValue();

                Object value = textValue;
                if (numericValue != null) {
                    logger.debug("[" + i + " - " + field + "] Numeric value found, returning as a number.");
                    value = numericValue;
                }

                // Add the field to the entry.
                ldEntry.put(field, value);
            }

            // Add the entry to the response.
            if (!filtered) {
                liveDataEntries.add(ldEntry);
            }

            i += 1;
        }

        // Sorting support.
        logger.debug("Sorting entries.");
        Collections.sort(liveData.getEntries(), (Map<String, Object> arg0, Map<String, Object> arg1) -> {
            logger.debug("Comparing entries " + arg0.get("_inline_id") + " and " + arg1.get("_inline_id") + ".");
            for (SortEntry sort : query.getSort()) {
                logger.debug("Comparing along field: " + sort.getProperty());

                if (!(arg0.containsKey(sort.getProperty()) && arg1.containsKey(sort.getProperty()))) {

                    int c = 0;
                    if (!arg0.containsKey(sort.getProperty()) && !arg1.containsKey(sort.getProperty())) {
                        logger.debug("Field absent on both objects, skipping.");
                        continue;
                    }

                    if (arg0.containsKey(sort.getProperty())) {
                        logger.debug("Field is present on first object but absent on second.");
                        c = 1;
                        logger.debug("Comparison result: " + c);
                    }

                    if (arg1.containsKey(sort.getProperty())) {
                        logger.debug("Field is present on second object but absent on first.");
                        c = -1;
                        logger.debug("Comparison result: " + c);
                    }

                    if (sort.isDescending()) {
                        c = -c;
                    }

                    return c;
                }

                int c = 0;

                if (arg0.containsKey(DATE_ID + sort.getProperty()) && arg1.containsKey(DATE_ID + sort.getProperty())) {
                    logger.debug("Field is a date, comparing numeric values of date field.");
                    Object o0 = arg0.get(DATE_ID + sort.getProperty());
                    Object o1 = arg1.get(DATE_ID + sort.getProperty());

                    Long t0;
                    Long t1;

                    try {
                        t0 = Long.parseLong(o0.toString());
                        t1 = Long.parseLong(o1.toString());
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    c = t0.compareTo(t1);
                } else {
                    logger.debug("Field is not a date, comparing text values.");
                    Object o0 = arg0.get(TEXT_ID + sort.getProperty());
                    Object o1 = arg1.get(TEXT_ID + sort.getProperty());

                    c = o0.toString().compareTo(o1.toString());
                }

                if (sort.isDescending()) {
                    c = -c;
                }

                if (c != 0) {
                    logger.debug("Comparison result: " + c);
                    return c;
                }

                logger.debug("Objects are equivalent along this field.");
            }
            logger.debug("Failed to compare objects. Comparison result: 0");
            return 0;
        });

        liveData.setCount(liveDataEntries.size());
        return liveData;
    }

    /**
     * Compress a string using GZIP.
     * 
     * @param str the string to compress
     * @return the compressed string as bytes.
     * @throws LiveDataException
     */
    private static String decompressString(byte[] bytes) throws LiveDataException
    {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            GZIPInputStream gzip = new GZIPInputStream(in);
            byte[] out = gzip.readAllBytes();
            gzip.close();
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LiveDataException("Failed to decompress the entries parameter.", e);
        }
    }

    /**
     * Find the cached entries and get its base64 representation.
     * 
     * @param entries the received entries query parameter
     * @return the entries base64 representation
     */
    private String getEntriesB64(String entries) throws LiveDataException
    {
        String result;

        logger.debug("Trying to retrieve entry from cache.");
        try {
            result = this.inlineTableCache.getCache().get(entries);
        } catch (CacheException e) {
            throw new LiveDataException("Failed to retrieve cache.", e);
        }

        if (result == null) {
            logger.debug("Entries could not be found in cache. Assuming " + entries
                + " is not a hash but the entries Base64 itself.");
            return entries;
        }

        logger.debug("Found entries Base64 in cache: " + result);
        return result;
    }

}
