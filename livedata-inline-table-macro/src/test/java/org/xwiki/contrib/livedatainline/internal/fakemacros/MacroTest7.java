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
package org.xwiki.contrib.livedatainline.internal.fakemacros;

import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.CompositeBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.TableBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Fake macro for the test to return a specific list of block that we want to test if they can be handled correctly by
 * the macro
 */
@Singleton
@Component
@Named("macro1")
public class MacroTest7 extends AbstractMacro<MacroTest7Param>
{
    public MacroTest7()
    {
        super("Test macro1",
            "Just a simple macro to generate some specific block to test with livedata inline table macro",
            MacroTest7Param.class);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(MacroTest7Param parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        return List.of(
            new TableBlock(List.of(
                new TableRowBlock(List.of(
                    new TableCellBlock(List.of(
                        new WordBlock("A")
                    )),
                    new MetaDataBlock(List.of(
                        new TableCellBlock(List.of(
                            new WordBlock("B")
                        ))
                    ))
                )),
                new MetaDataBlock(List.of(
                    new TableRowBlock(List.of(
                        new TableCellBlock(List.of(
                            new WordBlock("C")
                        )),
                        new MetaDataBlock(List.of(
                            new TableCellBlock(List.of(
                                new WordBlock("D"),
                                new MetaDataBlock(List.of(
                                    new WordBlock("E")
                                ))
                            ))
                        ))
                    ))
                )),
                new MetaDataBlock(List.of(
                    new TableRowBlock(List.of(
                        new MetaDataBlock(List.of(
                            new TableCellBlock(List.of(
                                new WordBlock("F")
                            )),
                            new TableCellBlock(List.of(
                                new MetaDataBlock(List.of(
                                    new WordBlock("G")
                                ))
                            ))
                        ))
                    ))
                )),
                new MetaDataBlock(List.of(
                    new TableRowBlock(List.of(
                        new MetaDataBlock(List.of(
                            new TableCellBlock(List.of(
                                new CompositeBlock(List.of(
                                    new WordBlock("H")
                                ))
                            ))
                        )),
                        new MetaDataBlock(List.of(
                            new TableCellBlock(List.of(
                                new WordBlock("I")
                            ))
                        ))
                    ))
                )),
                new CompositeBlock(List.of(
                    new TableRowBlock(List.of(
                        new TableCellBlock(List.of(
                            new WordBlock("J")
                        )),
                        new CompositeBlock(List.of(
                            new TableCellBlock(List.of(
                                new WordBlock("K")
                            ))
                        ))
                    ))
                ))
            ))
        );
    }
}
