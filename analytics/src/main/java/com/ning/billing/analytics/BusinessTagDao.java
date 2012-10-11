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

import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.analytics.dao.BusinessAccountTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoicePaymentTagSqlDao;
import com.ning.billing.analytics.dao.BusinessInvoiceTagSqlDao;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionTagSqlDao;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;

public class BusinessTagDao {

    private static final Logger log = LoggerFactory.getLogger(BusinessTagDao.class);

    private final BusinessAccountTagSqlDao accountTagSqlDao;
    private final BusinessInvoiceTagSqlDao invoiceTagSqlDao;
    private final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao;
    private final BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao;
    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementApi;

    @Inject
    public BusinessTagDao(final BusinessAccountTagSqlDao accountTagSqlDao,
                          final BusinessInvoicePaymentTagSqlDao invoicePaymentTagSqlDao,
                          final BusinessInvoiceTagSqlDao invoiceTagSqlDao,
                          final BusinessSubscriptionTransitionTagSqlDao subscriptionTransitionTagSqlDao,
                          final AccountInternalApi accountApi,
                          final EntitlementInternalApi entitlementApi) {
        this.accountTagSqlDao = accountTagSqlDao;
        this.invoicePaymentTagSqlDao = invoicePaymentTagSqlDao;
        this.invoiceTagSqlDao = invoiceTagSqlDao;
        this.subscriptionTransitionTagSqlDao = subscriptionTransitionTagSqlDao;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
    }

    public void tagAdded(final ObjectType objectType, final UUID objectId, final String name, final InternalCallContext context) {
        if (objectType.equals(ObjectType.ACCOUNT)) {
            tagAddedForAccount(objectId, name, context);
        } else if (objectType.equals(ObjectType.BUNDLE)) {
            tagAddedForBundle(objectId, name, context);
        } else if (objectType.equals(ObjectType.INVOICE)) {
            tagAddedForInvoice(objectId, name, context);
        } else if (objectType.equals(ObjectType.PAYMENT)) {
            tagAddedForPayment(objectId, name, context);
        } else {
            log.info("Ignoring tag addition of {} for object id {} (type {})", new Object[]{name, objectId.toString(), objectType.toString()});
        }
    }

    public void tagRemoved(final ObjectType objectType, final UUID objectId, final String name, final InternalCallContext context) {
        if (objectType.equals(ObjectType.ACCOUNT)) {
            tagRemovedForAccount(objectId, name, context);
        } else if (objectType.equals(ObjectType.BUNDLE)) {
            tagRemovedForBundle(objectId, name, context);
        } else if (objectType.equals(ObjectType.INVOICE)) {
            tagRemovedForInvoice(objectId, name, context);
        } else if (objectType.equals(ObjectType.PAYMENT)) {
            tagRemovedForPayment(objectId, name, context);
        } else {
            log.info("Ignoring tag removal of {} for object id {} (type {})", new Object[]{name, objectId.toString(), objectType.toString()});
        }
    }

    private void tagAddedForAccount(final UUID accountId, final String name, final InternalCallContext context) {
        final Account account;
        try {
            account = accountApi.getAccountById(accountId, context);
        } catch (AccountApiException e) {
            log.warn("Ignoring tag addition of {} for account id {} (account does not exist)", name, accountId.toString());
            return;
        }

        final String accountKey = account.getExternalKey();
        accountTagSqlDao.addTag(accountId.toString(), accountKey, name, context);
    }

    private void tagRemovedForAccount(final UUID accountId, final String name, final InternalCallContext context) {
        accountTagSqlDao.removeTag(accountId.toString(), name, context);
    }

    private void tagAddedForBundle(final UUID bundleId, final String name, final InternalCallContext context) {
        final SubscriptionBundle bundle;
        try {
            bundle = entitlementApi.getBundleFromId(bundleId, context);
        } catch (EntitlementUserApiException e) {
            log.warn("Ignoring tag addition of {} for bundle id {} (bundle does not exist)", name, bundleId.toString());
            return;
        }

        final Account account;
        try {
            account = accountApi.getAccountById(bundle.getAccountId(), context);
        } catch (AccountApiException e) {
            log.warn("Ignoring tag addition of {} for bundle id {} and account id {} (account does not exist)", new Object[]{name, bundleId.toString(), bundle.getAccountId()});
            return;
        }

        /*
         * Note: we store tags associated to bundles, not to subscriptions.
         */
        final String accountKey = account.getExternalKey();
        final String externalKey = bundle.getKey();
        subscriptionTransitionTagSqlDao.addTag(accountKey, bundleId.toString(), externalKey, name, context);
    }

    private void tagRemovedForBundle(final UUID bundleId, final String name, final InternalCallContext context) {
        subscriptionTransitionTagSqlDao.removeTag(bundleId.toString(), name, context);
    }

    private void tagAddedForInvoice(final UUID objectId, final String name, final InternalCallContext context) {
        invoiceTagSqlDao.addTag(objectId.toString(), name, context);
    }

    private void tagRemovedForInvoice(final UUID objectId, final String name, final InternalCallContext context) {
        invoiceTagSqlDao.removeTag(objectId.toString(), name, context);
    }

    private void tagAddedForPayment(final UUID objectId, final String name, final InternalCallContext context) {
        invoicePaymentTagSqlDao.addTag(objectId.toString(), name, context);
    }

    private void tagRemovedForPayment(final UUID objectId, final String name, final InternalCallContext context) {
        invoicePaymentTagSqlDao.removeTag(objectId.toString(), name, context);
    }
}
