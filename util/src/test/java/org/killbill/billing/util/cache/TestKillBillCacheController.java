/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.cache;

import javax.cache.Cache;
import javax.cache.CacheException;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestKillBillCacheController extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testWithBrokenCache() {
        final Cache cache = Mockito.mock(Cache.class, new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                // e.g. Redis connection broken
                throw new CacheException("Exception for testing");
            }
        });

        final BaseCacheLoader<String, Long> baseCacheLoader = new BaseCacheLoader<String, Long>() {
            @Override
            public CacheType getCacheType() {
                return CacheType.RECORD_ID;
            }

            @Override
            public Long compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
                return Long.valueOf(key);
            }
        };

        final KillBillCacheController<String, Long> killBillCacheController = new KillBillCacheController<String, Long>(cache, baseCacheLoader);

        try {
            killBillCacheController.getKeys();
            Assert.fail();
        } catch (final CacheException e) {
            // Nothing we can do
        }

        // This will go back to the cache loader
        Assert.assertEquals(killBillCacheController.get("12", null), new Long(12));
    }
}
