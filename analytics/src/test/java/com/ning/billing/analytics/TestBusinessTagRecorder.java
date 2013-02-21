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

package com.ning.billing.analytics;

import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.callcontext.InternalCallContext;

public class TestBusinessTagRecorder extends AnalyticsTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForAccount() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final String accountKey = UUID.randomUUID().toString();

        final Account accountData = new MockAccountBuilder(UUID.randomUUID())
                .externalKey(accountKey)
                .currency(Currency.MXN)
                .build();
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(accountData.getId()), Mockito.<InternalCallContext>any())).thenReturn(accountData);
        Mockito.when(accountInternalApi.getAccountByKey(Mockito.eq(accountData.getExternalKey()), Mockito.<InternalCallContext>any())).thenReturn(accountData);
        final UUID accountId = accountData.getId();

        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
        tagDao.tagAdded(ObjectType.ACCOUNT, accountId, name, internalCallContext);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 1);
        tagDao.tagRemoved(ObjectType.ACCOUNT, accountId, name, internalCallContext);
        Assert.assertEquals(accountTagSqlDao.getTagsForAccountByKey(accountKey, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testAddAndRemoveTagsForBundle() throws Exception {
        final String name = UUID.randomUUID().toString().substring(0, 20);
        final String externalKey = UUID.randomUUID().toString();

        final Account accountData = new MockAccountBuilder()
                .currency(Currency.MXN)
                .build();
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(accountData.getId()), Mockito.<InternalCallContext>any())).thenReturn(accountData);
        Mockito.when(accountInternalApi.getAccountByKey(Mockito.eq(accountData.getExternalKey()), Mockito.<InternalCallContext>any())).thenReturn(accountData);

        final UUID bundleId = UUID.randomUUID();
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getAccountId()).thenReturn(accountData.getId());
        Mockito.when(bundle.getExternalKey()).thenReturn(externalKey);
        Mockito.when(entitlementInternalApi.getBundleFromId(Mockito.eq(bundleId), Mockito.<InternalCallContext>any())).thenReturn(bundle);

        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
        tagDao.tagAdded(ObjectType.BUNDLE, bundleId, name, internalCallContext);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 1);
        tagDao.tagRemoved(ObjectType.BUNDLE, bundleId, name, internalCallContext);
        Assert.assertEquals(subscriptionTransitionTagSqlDao.getTagsForBusinessSubscriptionTransitionByKey(externalKey, internalCallContext).size(), 0);
    }
}
