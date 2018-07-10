/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.overdue.wrapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.caching.MockOverdueConfigCache;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestOverdueWrapper extends OverdueTestSuiteWithEmbeddedDB {

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        ((MockOverdueConfigCache) overdueConfigCache).loadOverwriteDefaultOverdueConfig(null);
    }

    @Test(groups = "slow")
    public void testWrapperBasic() throws Exception {
        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        ((MockOverdueConfigCache) overdueConfigCache).loadOverwriteDefaultOverdueConfig(config);

        Account account;
        OverdueWrapper wrapper;
        OverdueState state;

        state = config.getOverdueStatesAccount().findState("OD1");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account, internalCallContext);
        wrapper.refresh(clock.getUTCNow(), internalCallContext);
        testOverdueHelper.checkStateApplied(state);

        state = config.getOverdueStatesAccount().findState("OD2");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(41));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account, internalCallContext);
        wrapper.refresh(clock.getUTCNow(), internalCallContext);
        testOverdueHelper.checkStateApplied(state);

        state = config.getOverdueStatesAccount().findState("OD3");
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(51));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account, internalCallContext);
        wrapper.refresh(clock.getUTCNow(), internalCallContext);
        testOverdueHelper.checkStateApplied(state);
    }

    @Test(groups = "slow")
    public void testWrapperNoConfig() throws Exception {

        final Account account;
        final OverdueWrapper wrapper;
        final OverdueState state;

        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        state = config.getOverdueStatesAccount().findState(OverdueWrapper.CLEAR_STATE_NAME);
        account = testOverdueHelper.createAccount(clock.getUTCToday().minusDays(31));
        wrapper = overdueWrapperFactory.createOverdueWrapperFor(account, internalCallContext);
        final OverdueState result = wrapper.refresh(clock.getUTCNow(), internalCallContext);

        Assert.assertEquals(result.getName(), state.getName());
        Assert.assertEquals(result.isBlockChanges(), state.isBlockChanges());
        Assert.assertEquals(result.isDisableEntitlementAndChangesBlocked(), state.isDisableEntitlementAndChangesBlocked());
    }
}
