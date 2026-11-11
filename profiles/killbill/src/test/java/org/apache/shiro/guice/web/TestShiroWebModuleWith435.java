/*
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.apache.shiro.guice.web;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.Filter;

import org.apache.shiro.web.filter.PathMatchingFilter;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.apache.shiro.web.filter.authc.BearerHttpAuthenticationFilter;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Key;

public class TestShiroWebModuleWith435 {

    @Test(groups = "fast")
    @SuppressWarnings("unchecked")
    public void testFilterInSeveralChainsKeepsAllPatterns() {
        final Key<BearerHttpAuthenticationFilter> bearer = Key.get(BearerHttpAuthenticationFilter.class);
        final Key<BasicHttpAuthenticationFilter> basic = Key.get(BasicHttpAuthenticationFilter.class);

        // Same shape as KillBillShiroWebModule: the Bearer filter is part of both chains
        final Map<String, Key<? extends Filter>[]> chains = new LinkedHashMap<>();
        chains.put("/1.0/kb/**", new Key[]{bearer, basic});
        chains.put("/plugins/**", new Key[]{bearer});

        final Map<Key<? extends PathMatchingFilter>, Map<String, String>> configs = ShiroWebModuleWith435.buildFilterChainConfigs(chains);

        Assert.assertEquals(configs.get(bearer), Map.of("/1.0/kb/**", "", "/plugins/**", ""));
        Assert.assertEquals(configs.get(basic), Map.of("/1.0/kb/**", ""));
    }
}
