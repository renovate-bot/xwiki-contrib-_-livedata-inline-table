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

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.livedata.LiveDataConfiguration;
import org.xwiki.livedata.LiveDataEntryDescriptor;
import org.xwiki.livedata.LiveDataMeta;
import org.xwiki.livedata.LiveDataPaginationConfiguration;
import org.xwiki.livedata.LiveDataPropertyDescriptor;

/**
 * Configuration of the {@link InlineTableLiveDataSource}.
 * 
 * @version $Id$
 * @since 0.0.1
 */
@Component
@Singleton
@Named(InlineTableLiveDataSource.ID)
public class InlineTableLiveDataConfigurationProvider implements Provider<LiveDataConfiguration>
{

    @Override
    public LiveDataConfiguration get()
    {
        LiveDataConfiguration input = new LiveDataConfiguration();
        LiveDataMeta meta = new LiveDataMeta();
        LiveDataPaginationConfiguration pagination = new LiveDataPaginationConfiguration();
        pagination.setShowPageSizeDropdown(true);
        meta.setPagination(pagination);
        LiveDataEntryDescriptor entryDescriptor = new LiveDataEntryDescriptor();
        entryDescriptor.setIdProperty("_inline_id");
        input.setMeta(meta);
        meta.setEntryDescriptor(entryDescriptor);

        List<LiveDataPropertyDescriptor> propertyDescriptors = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            propertyDescriptors.add(buildLiveDataPropertyDescriptor("" + i));
        }

        meta.setPropertyDescriptors(propertyDescriptors);

        return input;
    }

    /**
     * a.
     * 
     * @param field
     * @return
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