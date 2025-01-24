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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.math.IntRange;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.BlockFilter;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.transformation.MacroTransformationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convert a Table to LiveData.
 * 
 * @version $Id$
 * @since 0.0.4
 */
public class LiveDataInlineTableMacroBlockFilter implements BlockFilter
{

    private static final String ID = "id";

    private MacroTransformationContext context;

    private BlockRenderer plainTextRenderer;

    private BlockRenderer richTextRenderer;

    private LiveDataInlineTableMacroParameters parameters;

    /**
     * Constructor.
     */
    LiveDataInlineTableMacroBlockFilter(LiveDataInlineTableMacroParameters parameters,
        MacroTransformationContext context, BlockRenderer plainTextRenderer, BlockRenderer richTextRenderer)
    {
        this.parameters = parameters;
        this.context = context;
        this.plainTextRenderer = plainTextRenderer;
        this.richTextRenderer = richTextRenderer;
    }

    @Override
    public List<Block> filter(Block block)
    {
        if (block instanceof TableBlock) {
            return transformTable((TableBlock) block);
        }

        return Collections.singletonList(block);
    }

    /**
     * The parsed representation of a table.
     * 
     * @version $Id$
     */
    private static class ParsedTable
    {
        private List<String> fields;

        private List<Map<String, Object>> entries;

        /**
         * Constructor.
         * 
         * @param fields The fields of the table.
         * @param entries The entries of table.
         */
        ParsedTable(List<String> fields, List<Map<String, Object>> entries)
        {
            this.setFields(fields);
            this.setEntries(entries);
        }

        /**
         * Gets the fields of the table.
         * 
         * @return the fields of the table.
         */
        public List<String> getFields()
        {
            return this.fields;
        }

        /**
         * Sets the fields of the table.
         * 
         * @param fields the fields of the table.
         */
        public void setFields(List<String> fields)
        {
            this.fields = fields;
        }

        /**
         * Gets the entries of the table.
         * 
         * @return the entries of the table.
         */
        public List<Map<String, Object>> getEntries()
        {
            return this.entries;
        }

        /**
         * Sets the entries of the table.
         * 
         * @param entries The entries of the table.
         */
        public void setEntries(List<Map<String, Object>> entries)
        {
            this.entries = entries;
        }
    }

    /**
     * Transform a table to LiveData.
     * 
     * @param table the Table to convert to LiveData
     * @return the new XDOM containing a LiveData.
     */
    public List<Block> transformTable(TableBlock table)
    {
        // Parse the table.
        ParsedTable parsedTable = tableToMap(table, parameters);
        List<String> fields = parsedTable.getFields();
        List<Map<String, Object>> entries = parsedTable.getEntries();

        // Convert the entries and fields to JSON in order to pass them to LiveData.
        String entriesJson = "";
        String fieldsJson = "";
        try {
            entriesJson = buildJSON(entries);
            fieldsJson = buildJSON(fields);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new LiveDataInlineTableMacroRuntimeException("Failed to serialize the table.");
        }

        // Encode the JSON to URLBase64 because it is passed to LiveData as a query parameter.
        String entriesB64 = "";
        try {
            entriesB64 = Base64.getUrlEncoder().encodeToString(compressString(entriesJson));
        } catch (IOException e) {
            throw new LiveDataInlineTableMacroRuntimeException("Failed to compress the table entries.");
        }

        // Build the LiveData JSON.
        String ldJson = "";
        try {
            ldJson = buildJSON(Map.of("query",
                Map.of("properties", (new IntRange(0, fields.size() - 1)).toArray(), "source",
                    Map.of(ID, InlineTableLiveDataSource.ID, "entries", entriesB64), "offset", 0, "limit", 10),
                "meta", Map.of("propertyDescriptors", getPropertyDescriptors(fields), "defaultDisplayer", "html")));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new LiveDataInlineTableMacroRuntimeException("Failed to serialize the LiveData parameters.");
        }

        // Call LiveData with the computed parameters.
        String id = parameters.getId();
        return Collections.singletonList(new MacroBlock("liveData",
            id == null ? Collections.emptyMap() : Map.of(ID, parameters.getId()), ldJson, context.isInline()));
    }

    /**
     * Compress a string using GZIP.
     * 
     * @param str the string to compress
     * @return the compressed string as bytes.
     * @throws IOException
     */
    private static byte[] compressString(String str) throws IOException
    {
        if (str == null || str.length() == 0) {
            return "".getBytes(StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(str.length());
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        gzip.write(str.getBytes(StandardCharsets.UTF_8));
        gzip.close();

        return out.toByteArray();
    }

    /**
     * Generate the list of property descriptors for the given fields.
     * 
     * @param fields the name of the fields
     * @return the list of property descriptor for the given fields
     */
    private List<Object> getPropertyDescriptors(List<String> fields)
    {
        List<Object> result = new ArrayList<>();

        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            result.add(Map.of(ID, "" + i, "name", field, "sortable", true, "filterable", true));
        }

        return result;
    }

    /**
     * Convert a Java object to a JSON.
     * 
     * @param object
     * @return the newly constructed JSON string.
     * @throws JsonProcessingException
     */
    private String buildJSON(Object object) throws JsonProcessingException
    {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Read a table and extract entries from it.
     * 
     * @param table The root of the table to read.
     * @param parameters The macro parameters.
     * @return The extracted list of properties and entries.
     */
    private ParsedTable tableToMap(TableBlock table, LiveDataInlineTableMacroParameters parameters)
    {
        // Extract the rows from the table while counting the number of properties.
        List<TableRowBlock> rows = new ArrayList<>();
        int propertiesCount = 0;

        for (Block child : table.getChildren()) {
            if (child instanceof TableRowBlock) {
                TableRowBlock row = (TableRowBlock) child;
                rows.add(row);
                propertiesCount = Math.max(propertiesCount, row.getChildren().size());
            }
        }

        // Define the properties, i.e. the header of the table.
        List<String> properties = new ArrayList<>();
        for (int i = 0; i < propertiesCount; i++) {
            properties.add("" + i);
        }

        // Extract the entries from the rows.
        List<Map<String, Object>> entries = new ArrayList<>();
        boolean inlineHeading = false;
        for (TableRowBlock row : rows) {
            Map<String, Object> entry = new HashMap<>();
            int i = 0;
            for (Block child : row.getChildren()) {
                if (child instanceof TableCellBlock) {
                    TableCellBlock cell = (TableCellBlock) child;
                    WikiPrinter textPrinter = new DefaultWikiPrinter();
                    plainTextRenderer.render(cell, textPrinter);
                    if (entries.isEmpty() && child instanceof TableHeadCellBlock) {
                        properties.set(i, textPrinter.toString());
                        inlineHeading = true;
                    }
                    // We need to render the content of the cell as a string so that we can pass it to LiveData.
                    WikiPrinter cellPrinter = new DefaultWikiPrinter();
                    richTextRenderer.render(new GroupBlock(cell.getChildren(), cell.getParameters()), cellPrinter);
                    entry.put("" + i, cellPrinter.toString());
                    entry.put("text." + i, textPrinter.toString());
                    i++;
                }
            }
            entries.add(entry);
        }
        if (inlineHeading) {
            entries.remove(0);
        }

        return new ParsedTable(properties, entries);
    }

}
