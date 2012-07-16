/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.overdue.wrapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueTestBase;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.util.config.XMLLoader;

public class TestOverdueWrapper extends OverdueTestBase {
    @Test(groups = "slow")
    public void testWrapperBasic() throws Exception {
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        SubscriptionBundle bundle;
        OverdueWrapper<SubscriptionBundle> wrapper;
        OverdueState<SubscriptionBundle> state;

        state = config.getBundleStateSet().findState("OD1");
        bundle = createBundle(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(bundle);
        wrapper.refresh();
        checkStateApplied(state);

        state = config.getBundleStateSet().findState("OD2");
        bundle = createBundle(clock.getUTCToday().minusDays(41));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(bundle);
        wrapper.refresh();
        checkStateApplied(state);

        state = config.getBundleStateSet().findState("OD3");
        bundle = createBundle(clock.getUTCToday().minusDays(51));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(bundle);
        wrapper.refresh();
        checkStateApplied(state);
    }

    @Test(groups = "slow")
    public void testWrapperNoConfig() throws Exception {
        overdueWrapperFactory.setOverdueConfig(null);

        final SubscriptionBundle bundle;
        final OverdueWrapper<SubscriptionBundle> wrapper;
        final OverdueState<SubscriptionBundle> state;

        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        state = config.getBundleStateSet().findState(BlockingApi.CLEAR_STATE_NAME);
        bundle = createBundle(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(bundle);
        final OverdueState<SubscriptionBundle> result = wrapper.refresh();

        Assert.assertEquals(result.getName(), state.getName());
        Assert.assertEquals(result.blockChanges(), state.blockChanges());
        Assert.assertEquals(result.disableEntitlementAndChangesBlocked(), state.disableEntitlementAndChangesBlocked());
    }
}
