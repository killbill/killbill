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

package com.ning.billing.analytics;

import javax.inject.Inject;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.dao.ObjectType;

public class BusinessTagRecorder {
    private static final Logger log = LoggerFactory.getLogger(BusinessTagRecorder.class);

    private final BusinessAccountTagSqlDao accountTagSqlDao;
    private final BusinessInvoiceTagSqlDao invoiceTagSqlDao;
    private final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao;
    private final BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao;
    private final AccountUserApi accountApi;
    private final EntitlementUserApi entitlementUserApi;

    @Inject
    public BusinessTagRecorder(final BusinessAccountTagSqlDao accountTagSqlDao,
                               final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao,
                               final BusinessInvoiceTagSqlDao invoiceTagSqlDao,
                               final BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao,
                               final AccountUserApi accountApi,
                               final EntitlementUserApi entitlementUserApi) {
        this.accountTagSqlDao = accountTagSqlDao;
        this.invoicePaymentTagSqlDao = invoicePaymentTagSqlDao;
        this.invoiceTagSqlDao = invoiceTagSqlDao;
        this.subscriptionTransitionTagSqlDao = subscriptionTransitionTagSqlDao;
        this.accountApi = accountApi;
        this.entitlementUserApi = entitlementUserApi;
    }

    public void tagAdded(final ObjectType objectType, final UUID objectId, final String name) {
        if (objectType.equals(ObjectType.ACCOUNT)) {
            tagAddedForAccount(objectId, name);
        } else if (objectType.equals(ObjectType.BUNDLE)) {
            tagAddedForBundle(objectId, name);
        } else if (objectType.equals(ObjectType.INVOICE)) {
            tagAddedForInvoice(objectId, name);
        } else if (objectType.equals(ObjectType.PAYMENT)) {
            tagAddedForPayment(objectId, name);
        } else {
            log.info("Ignoring tag addition of {} for object id {} (type {})", new Object[]{name, objectId.toString(), objectType.toString()});
        }
    }

    public void tagRemoved(final ObjectType objectType, final UUID objectId, final String name) {
        if (objectType.equals(ObjectType.ACCOUNT)) {
            tagRemovedForAccount(objectId, name);
        } else if (objectType.equals(ObjectType.BUNDLE)) {
            tagRemovedForBundle(objectId, name);
        } else if (objectType.equals(ObjectType.INVOICE)) {
            tagRemovedForInvoice(objectId, name);
        } else if (objectType.equals(ObjectType.PAYMENT)) {
            tagRemovedForPayment(objectId, name);
        } else {
            log.info("Ignoring tag removal of {} for object id {} (type {})", new Object[]{name, objectId.toString(), objectType.toString()});
        }
    }

    private void tagAddedForAccount(final UUID accountId, final String name) {
        final Account account;
        try {
            account = accountApi.getAccountById(accountId);
        } catch (AccountApiException e) {
            log.warn("Ignoring tag addition of {} for account id {} (account does not exist)", name, accountId.toString());
            return;
        }

        final String accountKey = account.getExternalKey();
        accountTagSqlDao.addTag(accountId.toString(), accountKey, name);
    }

    private void tagRemovedForAccount(final UUID accountId, final String name) {
        accountTagSqlDao.removeTag(accountId.toString(), name);
    }

    private void tagAddedForBundle(final UUID bundleId, final String name) {
        final SubscriptionBundle bundle;
        try {
            bundle = entitlementUserApi.getBundleFromId(bundleId);
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring tag addition of {} for bundle id {} (bundle does not exist)", name, bundleId.toString());
            return;
        }

        final Account account;
        try {
            account = accountApi.getAccountById(bundle.getAccountId());
        } catch (AccountApiException e) {
            log.warn("Ignoring tag addition of {} for bundle id {} and account id {} (account does not exist)", new Object[]{name, bundleId.toString(), bundle.getAccountId()});
            return;
        }

        /*
         * Note: we store tags associated to bundles, not to subscriptions.
         */
        final String accountKey = account.getExternalKey();
        final String externalKey = bundle.getKey();
        subscriptionTransitionTagSqlDao.addTag(accountKey, bundleId.toString(), externalKey, name);
    }

    private void tagRemovedForBundle(final UUID bundleId, final String name) {
        subscriptionTransitionTagSqlDao.removeTag(bundleId.toString(), name);
    }

    private void tagAddedForInvoice(final UUID objectId, final String name) {
        invoiceTagSqlDao.addTag(objectId.toString(), name);
    }

    private void tagRemovedForInvoice(final UUID objectId, final String name) {
        invoiceTagSqlDao.removeTag(objectId.toString(), name);
    }

    private void tagAddedForPayment(final UUID objectId, final String name) {
        invoicePaymentTagSqlDao.addTag(objectId.toString(), name);
    }

    private void tagRemovedForPayment(final UUID objectId, final String name) {
        invoicePaymentTagSqlDao.removeTag(objectId.toString(), name);
    }
}
