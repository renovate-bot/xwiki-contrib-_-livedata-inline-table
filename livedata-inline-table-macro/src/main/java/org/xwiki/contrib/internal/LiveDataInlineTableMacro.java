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
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang.math.IntRange;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableHeadCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.match.AnyBlockMatcher;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.stability.Unstable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.XWikiContext;

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

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private Logger logger;

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
     * Constructor.
     */
    public LiveDataInlineTableMacro()
    {
        super("Live Data Table Source",
            "Contains a table that can be displayed in live data. Making it filterable and sortable.",
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
        // When in WYSIWYG edit mode, it should be possible to edit underlying table using the WYSIWYG.
        // When in view mode, we should use liveData to display the table.
        // Check if we are in edit mode.
        // See https://www.xwiki.org/xwiki/bin/view/FAQ/How%20to%20write%20Macro%20code%20for%20the%20edit%20mode
        Syntax targetSyntax = context.getTransformationContext().getTargetSyntax();
        XWikiContext xcontext = xcontextProvider.get();

        if (targetSyntax != null) {
            SyntaxType targetSyntaxType = targetSyntax.getType();
            if (SyntaxType.ANNOTATED_HTML.equals(targetSyntaxType)
                || SyntaxType.ANNOTATED_XHTML.equals(targetSyntaxType)) {
                return parseContent(content, context);
            }
        }
        if ("get".equals(xcontext.getAction()) || "edit".equals(xcontext.getAction())) {
            return parseContent(content, context);
        }

        // Parse the table.
        List<Block> parsedContent = parseReadOnlyContent(content, context);
        ParsedTable parsedTable = tableToMap(findTable(parsedContent), parameters);
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
                Map.of("properties", (new IntRange(0, fields.size() - 1)).toArray(), "source",
                    Map.of(ID, InlineTableLiveDataSource.ID, "entries", entriesB64, "fields", fieldsB64), "offset", 0,
                    "limit", 10),
                "meta", Map.of("propertyDescriptors", getPropertyDescriptors(fields))));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MacroExecutionException("Failed to serialize the LiveData parameters.");
        }

        // Call LiveData with the computed parameters.
        String id = parameters.getId();
        logger.info("ldJson: " + ldJson);
        return Collections.singletonList(new MacroBlock("liveData",
            id == null ? Collections.emptyMap() : Map.of(ID, parameters.getId()), ldJson, context.isInline()));
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
                    // We need to render the content of the cell as a string so that we can pass it to LiveData.
                    WikiPrinter printer = new DefaultWikiPrinter();
                    plainTextRenderer.render(cell, printer);
                    if (entries.isEmpty() && child instanceof TableHeadCellBlock) {
                        properties.set(i, printer.toString());
                        inlineHeading = true;
                    }
                    entry.put("" + i, printer.toString());
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

    /**
     * Parse the content string to XDOM. This wraps the content in a MetaDataBlock.
     * 
     * @param content The string to parse to XDOM.
     * @param context The current transformation context.
     * @return The parsed XDOM.
     * @throws MacroExecutionException
     */
    private List<Block> parseContent(String content, MacroTransformationContext context) throws MacroExecutionException
    {
        // Don't execute transformations explicitly. They'll be executed on the generated content later on.
        List<Block> children = contentParser.parse(content, context, false, context.isInline()).getChildren();

        return Collections.singletonList(new MetaDataBlock(children, this.getNonGeneratedContentMetaData()));
    }

}
