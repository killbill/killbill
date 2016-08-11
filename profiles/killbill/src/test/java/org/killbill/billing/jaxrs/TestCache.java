/*
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import org.killbill.billing.util.cache.Cachable.CacheType;
import org.testng.Assert;
import org.testng.annotations.Test;

import net.sf.ehcache.Ehcache;

public class TestCache extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can Invalidate (clear) a Cache by name")
    public void testInvalidateCacheByName() throws Exception {
        // get Ehcache item with name "record-id"
        final Ehcache cache = cacheManager.getEhcache(CacheType.RECORD_ID.getCacheName());
        // verify that it is not null and has one stored key (the default tenant created for all integration tests)
        Assert.assertNotNull(cache);
        Assert.assertEquals(cache.getSize(), 1);

        // invalidate the specified cache
        killBillClient.invalidateCache(cache.getName(), requestOptions);

        // verify that now the cache is empty and has no keys stored
        Assert.assertEquals(cache.getSize(), 0);
    }
}
