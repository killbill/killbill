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

package com.ning.billing.entitlement.block;

import java.util.UUID;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.dao.MockBlockingStateDao;
import com.ning.billing.entitlement.api.BlockingApiException;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.Type;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestBlockingChecker extends EntitlementTestSuiteNoDB {

    private Account account;
    private SubscriptionBundle bundle;
    private Subscription subscription;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final UUID accountId = UUID.randomUUID();
        account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);

        bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);
        final UUID bundleId = UUID.randomUUID();
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getExternalKey()).thenReturn("key");

        subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);

        try {
            Mockito.when(subscriptionInternalApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundle);
        } catch (SubscriptionUserApiException e) {
            Assert.fail(e.toString());
        }

        // Cleanup mock daos
        ((MockBlockingStateDao) blockingStateDao).clear();
    }

    private void setStateBundle(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState bundleState = new DefaultBlockingState(bundle.getId(), "state", Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE, bB);
        Mockito.when(bundle.getBlockingState()).thenReturn(bundleState);
        blockingStateDao.setBlockingState(bundleState, clock, internalCallContext);
    }

    private void setStateAccount(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState accountState = new DefaultBlockingState(account.getId(), "state", Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE, bB);
        blockingStateDao.setBlockingState(accountState, clock, internalCallContext);
    }

    private void setStateSubscription(final boolean bC, final boolean bE, final boolean bB) {
        final BlockingState subscriptionState = new DefaultBlockingState(subscription.getId(), "state", Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE, bB);
        Mockito.when(subscription.getBlockingState()).thenReturn(subscriptionState);
        blockingStateDao.setBlockingState(subscriptionState, clock, internalCallContext);
    }

    @Test(groups = "fast")
    public void testSubscriptionChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);

        //BLOCKED SUBSCRIPTION
        setStateSubscription(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateSubscription(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateSubscription(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED BUNDLE
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateBundle(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateBundle(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedBilling(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(subscription, internalCallContext);
        blockingChecker.checkBlockedEntitlement(subscription, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(subscription, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }

    @Test(groups = "fast")
    public void testBundleChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);

        //BLOCKED BUNDLE
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateBundle(false, true, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateBundle(false, false, true);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedBilling(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(bundle, internalCallContext);
        blockingChecker.checkBlockedEntitlement(bundle, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(bundle, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }

    @Test(groups = "fast")
    public void testAccountChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);

        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);
        try {
            blockingChecker.checkBlockedChange(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, true, false);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedBilling(account, internalCallContext);
        try {
            blockingChecker.checkBlockedEntitlement(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        setStateAccount(false, false, true);
        blockingChecker.checkBlockedChange(account, internalCallContext);
        blockingChecker.checkBlockedEntitlement(account, internalCallContext);
        try {
            blockingChecker.checkBlockedBilling(account, internalCallContext);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
    }
}
