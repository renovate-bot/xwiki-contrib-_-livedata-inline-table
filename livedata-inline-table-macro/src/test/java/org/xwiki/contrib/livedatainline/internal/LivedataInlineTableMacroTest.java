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
package org.xwiki.contrib.livedatainline.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.inject.Provider;

import org.xwiki.cache.Cache;
import org.xwiki.cache.internal.MapCache;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.context.ExecutionContextInitializer;
import org.xwiki.contrib.internal.InlineTableCache;
import org.xwiki.environment.Environment;
import org.xwiki.livedata.LiveDataException;
import org.xwiki.livedata.macro.LiveDataMacroParameters;
import org.xwiki.rendering.block.RawBlock;
import org.xwiki.rendering.macro.Macro;
import org.xwiki.rendering.macro.descriptor.MacroDescriptor;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.test.integration.junit5.RenderingTests;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.XWikiTempDirUtil;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rendering tests to validate that the table parsing (provided to the livedata inline table macro) are handled in a
 * correct way.
 */
@AllComponents
public class LivedataInlineTableMacroTest implements RenderingTests
{
    private Cache<String> cache = new MapCache<>();

    @RenderingTests.Initialized
    public void initialize(MockitoComponentManager componentManager) throws Exception
    {
        // Register all mock component required to run the tests
        Environment environment = componentManager.registerMockComponent(Environment.class);
        when(environment.getTemporaryDirectory()).thenReturn(XWikiTempDirUtil.createTemporaryDirectory());

        componentManager.registerMockComponent(ExecutionContextInitializer.class, "threadclassloader");
        componentManager.registerMockComponent(SkinExtension.class, "ssrx");
        componentManager.registerMockComponent(SkinExtension.class, "jsrx");

        Provider<XWikiContext> xcontextProvider = componentManager.registerMockComponent(
            new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        XWikiContext xWikiContext = mock();
        when(xcontextProvider.get()).thenReturn(xWikiContext);
        when(xWikiContext.getAction()).thenReturn("view");
        when(xWikiContext.getLocale()).thenReturn(Locale.ENGLISH);
        XWiki xwiki = mock();
        when(xWikiContext.getWiki()).thenReturn(xwiki);
        when(xwiki.getXWikiPreference("dateformat", "yyyy/MM/dd HH:mm", xWikiContext)).thenReturn("yyyy/MM/dd HH:mm");

        InlineTableCache inlineTableCache = componentManager.registerMockComponent(InlineTableCache.class);
        when(inlineTableCache.getCache()).thenReturn(cache);

        Macro livedataMacro = componentManager.registerMockComponent(Macro.class, "liveData/xwiki/2.0");

        MacroDescriptor macroDescriptor = mock();
        when(livedataMacro.getDescriptor()).thenReturn(macroDescriptor);
        doReturn(LiveDataMacroParameters.class).when(macroDescriptor).getParametersBeanClass();

        when(livedataMacro.execute(any(), any(), any())).thenAnswer(invocation -> {

            String content = invocation.getArgument(1);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            Map<String, Map<String, Map>> json = objectMapper.readValue(content, Map.class);
            String entriesB64 = (String) json.get("query").get("source").get("entries");

            // Apply the same rule than in the livedata-inline-table-source. We check if the data is in the cache.
            // If not we consider that it's directly the base64 data.
            String entriesStr = cache.get(entriesB64);
            if (entriesStr == null) {
                entriesStr = entriesB64;
            }
            String decodedJson = decompressString(Base64.getUrlDecoder().decode(entriesStr));

            // We just reserilize the data to indent the data. It's much easier to check when the content
            // is cut in multiple line than a big block in single line.
            decodedJson = objectMapper.writeValueAsString(objectMapper.readTree(decodedJson));

            // We return what was parsed and what will be send to the livedata-inline-table source component.
            // So we can test in the test result that this result is what we expect and it will be the correct thing
            // that will be sent to the livedata-inline-table-source.
            return List.of(new RawBlock(decodedJson, Syntax.PLAIN_1_0));
        });
    }

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
}
