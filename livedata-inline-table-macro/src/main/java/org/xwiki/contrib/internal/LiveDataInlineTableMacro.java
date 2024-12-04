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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.match.AnyBlockMatcher;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.stability.Unstable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Macro to display a simple XWiki Syntax table with LiveData.
 * 
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Named("livedata-inline-table")
@Singleton
@Unstable
public class LiveDataInlineTableMacro extends AbstractMacro<LiveDataInlineTableMacroParameters>
{

    private static final String ID = "id";

    @Inject
    private MacroContentParser contentParser;

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    /**
     * Constructor.
     */
    public LiveDataInlineTableMacro()
    {
        super("Live Data Table Source", "Contains a table that can be displayed in live data.",
            new DefaultContentDescriptor("Content", true, Block.LIST_BLOCK_TYPE),
            LiveDataInlineTableMacroParameters.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    public List<Block> execute(LiveDataInlineTableMacroParameters parameters, String content,
        MacroTransformationContext context) throws MacroExecutionException
    {

        // Parse the table.
        List<Block> parsedContent = parseReadOnlyContent(content, context);
        List<Object> fieldsAndEntries = tableToMap(findTable(parsedContent));
        List<String> fields = (List<String>) fieldsAndEntries.get(0);
        List<Map<String, Object>> entries = (List<Map<String, Object>>) fieldsAndEntries.get(1);

        // Convert the entries and fields to JSON in order to pass them to LiveData.
        String entriesJson = "";
        String fieldsJson = "";
        try {
            entriesJson = buildJSON(entries);
            fieldsJson = buildJSON(fields);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MacroExecutionException("Failed to serialize the table.");
        }

        // Encode the JSON to URLBase64 because it is passed to LiveData as a query parameter.
        String entriesB64 = "";
        entriesB64 = Base64.getUrlEncoder().encodeToString(entriesJson.getBytes(StandardCharsets.UTF_8));
        String fieldsB64 = "";
        fieldsB64 = Base64.getUrlEncoder().encodeToString(fieldsJson.getBytes(StandardCharsets.UTF_8));

        // Build the LiveData JSON.
        String ldJson = "";
        try {
            ldJson = buildJSON(Map.of("query",
                Map.of("properties", fields, "source",
                    Map.of(ID, InlineTableLiveDataSource.ID, "entries", entriesB64, "fields", fieldsB64), "offset", 0,
                    "limit", 10)));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MacroExecutionException("Failed to serialize the LiveData parameters.");
        }

        // Call LiveData with the computed parameters.
        String id = parameters.getId();
        return Collections.singletonList(new MacroBlock("liveData",
            id == null ? Collections.emptyMap() : Map.of(ID, parameters.getId()), ldJson, context.isInline()));
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
     * Finds a TableBlock in the given XDOM.
     * 
     * @param body The rendering block to search for a table in.
     * @return The block holding the table or {@code null} if none were found.
     */
    private TableBlock findTable(List<Block> body)
    {
        for (Block block : body) {
            if (block instanceof TableBlock) {
                return (TableBlock) block;
            }
            for (Block b : block.getBlocks(new AnyBlockMatcher(), Block.Axes.DESCENDANT)) {
                if (b instanceof TableBlock) {
                    return (TableBlock) b;
                }
            }
        }

        return null;
    }

    /**
     * Read a table and extract entries from it.
     * 
     * @param table The root of the table to read.
     * @return The extracted list of properties and entries.
     */
    private List<Object> tableToMap(TableBlock table)
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
        // TODO: Optionally extract the header from the first row.
        List<String> properties = new ArrayList<>();
        for (int i = 0; i < propertiesCount; i++) {
            properties.add("" + i);
        }

        // Extract the entries from the rows.
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TableRowBlock row : rows) {
            Map<String, Object> entry = new HashMap<>();
            int i = 0;
            for (Block child : row.getChildren()) {
                if (child instanceof TableCellBlock) {
                    TableCellBlock cell = (TableCellBlock) child;
                    // We need to render the content of the cell as a string so that we can pass it to LiveData.
                    WikiPrinter printer = new DefaultWikiPrinter();
                    plainTextRenderer.render(cell, printer);
                    entry.put(properties.get(i), printer.toString());
                    i++;
                }
            }
            entries.add(entry);
        }

        return List.of(properties, entries);
    }

    /**
     * Parse the content string to XDOM. This does not wrap the content in a MetaDataBlock.
     * 
     * @param content The string to parse to XDOM.
     * @param context The current transformation context.
     * @return The parsed XDOM.
     * @throws MacroExecutionException
     */
    private List<Block> parseReadOnlyContent(String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        return contentParser.parse(content, context, true, context.isInline()).getChildren();
    }

}
