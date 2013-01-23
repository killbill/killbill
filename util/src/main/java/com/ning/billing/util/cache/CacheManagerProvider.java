/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.cache;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.config.CacheConfig;

import net.sf.ehcache.CacheManager;

public class CacheManagerProvider implements Provider<CacheManager> {

    private static final Logger log = LoggerFactory.getLogger(CacheManagerProvider.class);

    private final CacheConfig cacheConfig;

    private CacheManager cacheManager = null;

    @Inject
    public CacheManagerProvider(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    @Override
    public CacheManager get() {
        synchronized (this) {
            if (this.cacheManager == null) {
                log.debug("Loading EHCache config from '%s'", cacheConfig.getCacheConfigLocation());

                try {
                    // Creates a singleton instance of the CacheManager
                    this.cacheManager = CacheManager.create(CacheManagerProvider.class.getResource(cacheConfig.getCacheConfigLocation()).openStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return this.cacheManager;
        }
    }
}
