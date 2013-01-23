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

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.event.CacheEventListenerFactory;

public class ExpirationListenerFactory extends CacheEventListenerFactory {

    private static final Logger log = LoggerFactory.getLogger(ExpirationListenerFactory.class);

    @Override
    public CacheEventListener createCacheEventListener(final Properties properties)
    {
        return new ExpirationListener();
    }

    private static class ExpirationListener implements CacheEventListener
    {
        @Override
        public Object clone() throws CloneNotSupportedException
        {
            throw new CloneNotSupportedException("No cloning!");
        }

        @Override
        public void dispose()
        {
        }

        @Override
        public void notifyElementEvicted(final Ehcache cache, final Element element)
        {
            if (log.isDebugEnabled()) {
                log.debug("Cache Element " + element + " evicted!");
            }
        }

        @Override
        public void notifyElementExpired(final Ehcache cache, final Element element)
        {
            if (log.isDebugEnabled()) {
                log.debug("Cache Element " + element + " expired!");
            }
        }

        @Override
        public void notifyElementPut(final Ehcache cache, final Element element) throws CacheException
        {
        }

        @Override
        public void notifyElementRemoved(final Ehcache cache, final Element element) throws CacheException
        {
        }

        @Override
        public void notifyElementUpdated(final Ehcache cache, final Element element) throws CacheException
        {
        }

        @Override
        public void notifyRemoveAll(final Ehcache cache)
        {
        }
    }
}
