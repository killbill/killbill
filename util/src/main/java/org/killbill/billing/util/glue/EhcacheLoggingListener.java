/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.glue;

import org.ehcache.Cache;
import org.ehcache.Status;
import org.ehcache.core.events.CacheManagerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EhcacheLoggingListener implements CacheManagerListener {

    private static final Logger logger = LoggerFactory.getLogger(EhcacheLoggingListener.class);

    @Override
    public void cacheAdded(final String alias, final Cache<?, ?> cache) {
        logger.info("Added Ehcache '{}'", alias);
    }

    @Override
    public void cacheRemoved(final String alias, final Cache<?, ?> cache) {
        logger.info("Removed Ehcache '{}'", alias);
    }

    @Override
    public void stateTransition(final Status from, final Status to) {
        logger.info("Transitioning Ehcache from '{}' to '{}'", from, to);
    }
}
