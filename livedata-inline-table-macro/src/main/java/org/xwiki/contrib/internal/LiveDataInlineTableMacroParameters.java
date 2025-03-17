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

import org.xwiki.properties.annotation.PropertyDescription;

/**
 * Inline Table Livedata macro parameters.
 * 
 * @version $Id$
 */
public class LiveDataInlineTableMacroParameters
{
    private String id;

    private LiveDataInlineTableMacroHeaderDetectionParameter headerDetection =
        LiveDataInlineTableMacroHeaderDetectionParameter.REMOVE;

    private String header;

    private String dateFormats;

    private String dateFormatsSeparator = "\\|\\|";
    
    private Boolean sorting = true;
    
    private Boolean filtering = true;

    /**
     * Gets the id Parameter.
     * 
     * @return The id parameter.
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * Sets the id parameter.
     * 
     * @param id
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * Gets the header detection parameter.
     * 
     * @return the header detection parameter.
     */
    public LiveDataInlineTableMacroHeaderDetectionParameter getHeaderDetection()
    {
        return this.headerDetection;
    }

    /**
     * Sets the header detection parameter.
     * 
     * @param headerDetection
     */
    public void setHeaderDetection(LiveDataInlineTableMacroHeaderDetectionParameter headerDetection)
    {
        this.headerDetection = headerDetection;
    }

    /**
     * Gets the header parameter.
     * 
     * @return the header parameter.
     */
    public String getHeader()
    {
        return this.header;
    }

    /**
     * Sets the header parameter.
     * 
     * @param header
     */
    public void setHeader(String header)
    {
        this.header = header;
    }

    /**
     * Gets the dateFormat parameter.
     * 
     * @return the dateFormat parameter
     */
    public String getDateFormats()
    {
        return this.dateFormats;
    }

    /**
     * Sets the dateFormat parameter.
     * 
     * @param dateFormats the dateFormat parameter
     */
    @PropertyDescription("List of date formats using dateFormatsSeparator as a separator regex.")
    public void setDateFormats(String dateFormats)
    {
        this.dateFormats = dateFormats;
    }

    /**
     * Gets the dateFormatsSeparator parameter.
     * 
     * @return the dateFormatsSeparator parameter.
     */
    public String getDateFormatsSeparator()
    {
        return this.dateFormatsSeparator;
    }

    /**
     * Sets the dateFormatsSeparator parameter.
     * 
     * @param dateFormatSeparator
     */
    public void setDateFormatsSeparator(String dateFormatSeparator)
    {
        this.dateFormatsSeparator = dateFormatSeparator;
    }

}
