/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.account.api.Account;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.util.config.catalog.XMLLoader;
import com.ning.billing.junction.DefaultBlockingState;

public class TestOverdueWrapper extends OverdueTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testWrapperBasic() throws Exception {
        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        Account account;
        OverdueWrapper wrapper;
        OverdueState state;

        state = config.getBundleStateSet().findState("OD1");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account);
        wrapper.refresh(internalCallContext);
        testOverdueHelper.checkStateApplied(state);

        state = config.getBundleStateSet().findState("OD2");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(41));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account);
        wrapper.refresh(internalCallContext);
        testOverdueHelper.checkStateApplied(state);

        state = config.getBundleStateSet().findState("OD3");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(51));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account);
        wrapper.refresh(internalCallContext);
        testOverdueHelper.checkStateApplied(state);
    }

    @Test(groups = "slow")
    public void testWrapperNoConfig() throws Exception {
        overdueWrapperFactory.setOverdueConfig(null);

        final Account account;
        final OverdueWrapper wrapper;
        final OverdueState state;

        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        state = config.getBundleStateSet().findState(DefaultBlockingState.CLEAR_STATE_NAME);
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account);
        final OverdueState result = wrapper.refresh(internalCallContext);

        Assert.assertEquals(result.getName(), state.getName());
        Assert.assertEquals(result.blockChanges(), state.blockChanges());
        Assert.assertEquals(result.disableEntitlementAndChangesBlocked(), state.disableEntitlementAndChangesBlocked());
    }
}
