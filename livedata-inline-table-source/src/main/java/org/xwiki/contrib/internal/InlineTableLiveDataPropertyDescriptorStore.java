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
import java.util.Collection;
import java.util.List;

import org.xwiki.livedata.LiveDataPropertyDescriptor;
import org.xwiki.livedata.LiveDataPropertyDescriptorStore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Descriptor for the {@link InlineTableLiveDataSource}.
 * 
 * @version $Id$
 * @since 0.0.1
 */
public class InlineTableLiveDataPropertyDescriptorStore implements LiveDataPropertyDescriptorStore
{

    private InlineTableLiveDataSource liveDataTableSource;

    /**
     * Constructor.
     * 
     * @param liveDataTableSource The calling {@link InlineTableLiveDataSource} used for context dependent properties.
     */
    public InlineTableLiveDataPropertyDescriptorStore(InlineTableLiveDataSource liveDataTableSource)
    {
        this.liveDataTableSource = liveDataTableSource;
    }

    @Override
    public Collection<LiveDataPropertyDescriptor> get()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode fieldsNode = null;
        try {
            String decodedJson = new String(
                Base64.getUrlDecoder().decode(this.liveDataTableSource.getParameters().get("fields").toString()),
                StandardCharsets.UTF_8);
            fieldsNode = objectMapper.readTree(decodedJson);
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        List<LiveDataPropertyDescriptor> propertyDescriptors = new ArrayList<>();

        for (JsonNode field : fieldsNode) {
            propertyDescriptors.add(buildLiveDataPropertyDescriptor(field.toString()));
        }

        return propertyDescriptors;
    }

    /**
     * Build a property from a field name.
     * 
     * @param field The field name.
     * @return The built property.
     */
    private LiveDataPropertyDescriptor buildLiveDataPropertyDescriptor(String field)
    {
        LiveDataPropertyDescriptor pd = new LiveDataPropertyDescriptor();
        pd.setId(field);
        pd.setName(field);
        pd.setDescription("");
        pd.setType("String");
        pd.setSortable(false);
        pd.setEditable(false);
        pd.setFilterable(true);
        pd.setVisible(true);
        return pd;
    }
}
