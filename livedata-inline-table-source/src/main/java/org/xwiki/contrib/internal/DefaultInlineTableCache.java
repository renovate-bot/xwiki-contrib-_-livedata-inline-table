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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheException;
import org.xwiki.cache.CacheManager;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.cache.eviction.LRUEvictionConfiguration;
import org.xwiki.component.annotation.Component;

/**
 * The cache for livedata-inline-table.
 * 
 * @version $Id$
 */
@Component
@Singleton
public class DefaultInlineTableCache implements InlineTableCache
{

    @Inject
    private CacheManager cacheManager;

    @Inject
    private Logger logger;

    private Cache<String> cache;

    @Override
    public Cache<String> getCache() throws CacheException
    {
        if (this.cache == null) {
            initCache(0);
        }
        return this.cache;
    }

    /**
     * Initialize the cache with a new id if it already exists.
     * 
     * @param id
     * @throws CacheException
     */
    private void initCache(int id) throws CacheException
    {
        try {
            String cacheId = "";
            if (id != 0) {
                cacheId = "." + id;
            }
            logger.debug("Trying to create a cache with id: " + id);
            this.cache = this.cacheManager.createNewCache(this.buildCacheConfiguration("cache" + cacheId));
            logger.debug("Successfully created cache.");
        } catch (CacheException e) {
            logger.debug("Failed to create cache.");
            if (id < 100) {
                this.initCache(id + 1);
            } else {
                throw e;
            }
        }
    }

    /**
     * Build the cache configuration for livedata-inline-table.
     * 
     * @param id the id of the cache
     * @return the cache configuration
     */
    private CacheConfiguration buildCacheConfiguration(String id)
    {
        CacheConfiguration cacheConfiguration = new CacheConfiguration();
        cacheConfiguration.setConfigurationId("xwiki.contrib.livedata-inline-table." + id);
        LRUEvictionConfiguration lru = new LRUEvictionConfiguration();
        lru.setMaxEntries(100000000);
        lru.setMaxIdle(3600);
        return cacheConfiguration;
    }
}
