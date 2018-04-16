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

package org.killbill.billing.util.audit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.api.AuditLevel;

import com.google.common.base.Predicate;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class DefaultAccountAuditLogs implements AccountAuditLogs {

    private final UUID accountId;
    private final AuditLevel auditLevel;
    private final List<AuditLog> accountAuditLogs;

    private final Map<ObjectType, DefaultAccountAuditLogsForObjectType> auditLogsCache = new HashMap<ObjectType, DefaultAccountAuditLogsForObjectType>();

    public DefaultAccountAuditLogs(final UUID accountId) {
        this(accountId, AuditLevel.NONE, ImmutableSet.<AuditLog>of().iterator());
    }

    public DefaultAccountAuditLogs(final UUID accountId, final AuditLevel auditLevel, final Iterator<AuditLog> accountAuditLogsOrderedByTableName) {
        this.accountId = accountId;
        this.auditLevel = auditLevel;
        // TODO pierre - lame, we should be smarter to avoid loading all entries in memory. It's a bit tricky though...
        this.accountAuditLogs = ImmutableList.<AuditLog>copyOf(accountAuditLogsOrderedByTableName);
    }

    public void close() {
        // Make sure to go through the results to close the connection
        // no-op for now, see TODO above
    }

    @Override
    public List<AuditLog> getAuditLogsForAccount() {
        return getAuditLogs(ObjectType.ACCOUNT).getAuditLogs(accountId);
    }

    @Override
    public List<AuditLog> getAuditLogsForBundle(final UUID bundleId) {
        return getAuditLogs(ObjectType.BUNDLE).getAuditLogs(bundleId);
    }

    @Override
    public List<AuditLog> getAuditLogsForSubscription(final UUID subscriptionId) {
        return getAuditLogs(ObjectType.SUBSCRIPTION).getAuditLogs(subscriptionId);
    }

    @Override
    public List<AuditLog> getAuditLogsForSubscriptionEvent(final UUID subscriptionEventId) {
        return getAuditLogs(ObjectType.SUBSCRIPTION_EVENT).getAuditLogs(subscriptionEventId);
    }

    @Override
    public List<AuditLog> getAuditLogsForInvoice(final UUID invoiceId) {
        return getAuditLogs(ObjectType.INVOICE).getAuditLogs(invoiceId);
    }

    @Override
    public List<AuditLog> getAuditLogsForInvoiceItem(final UUID invoiceItemId) {
        return getAuditLogs(ObjectType.INVOICE_ITEM).getAuditLogs(invoiceItemId);
    }

    @Override
    public List<AuditLog> getAuditLogsForPayment(final UUID paymentId) {
        return getAuditLogs(ObjectType.PAYMENT).getAuditLogs(paymentId);
    }

    @Override
    public List<AuditLog> getAuditLogsForPaymentTransaction(final UUID paymentTransactionId) {
        return getAuditLogs(ObjectType.TRANSACTION).getAuditLogs(paymentTransactionId);
    }

    @Override
    public List<AuditLog> getAuditLogsForPaymentAttempt(final UUID paymentAttemptId) {
        return getAuditLogs(ObjectType.PAYMENT_ATTEMPT).getAuditLogs(paymentAttemptId);
    }

    @Override
    public List<AuditLog> getAuditLogsForPaymentMethod(final UUID paymentMethodId) {
        return getAuditLogs(ObjectType.PAYMENT_METHOD).getAuditLogs(paymentMethodId);
    }

    @Override
    public List<AuditLog> getAuditLogsForBlockingState(final UUID blockingStateId) {
        return getAuditLogs(ObjectType.BLOCKING_STATES).getAuditLogs(blockingStateId);
    }

    @Override
    public List<AuditLog> getAuditLogsForInvoicePayment(final UUID invoicePaymentId) {
        return getAuditLogs(ObjectType.INVOICE_PAYMENT).getAuditLogs(invoicePaymentId);
    }

    @Override
    public List<AuditLog> getAuditLogsForTag(final UUID tagId) {
        return getAuditLogs(ObjectType.TAG).getAuditLogs(tagId);
    }

    @Override
    public List<AuditLog> getAuditLogsForCustomField(final UUID customFieldId) {
        return getAuditLogs(ObjectType.CUSTOM_FIELD).getAuditLogs(customFieldId);
    }

    @Override
    public AccountAuditLogsForObjectType getAuditLogs(final ObjectType objectType) {
        if (auditLogsCache.get(objectType) == null) {
            auditLogsCache.put(objectType, new DefaultAccountAuditLogsForObjectType(auditLevel, new ObjectTypeFilter(objectType, accountAuditLogs.iterator())));
        }

        // Should never be null
        return auditLogsCache.get(objectType);
    }

    @Override
    public List<AuditLog> getAuditLogs() {
        return accountAuditLogs;
    }

    private final class ObjectTypeFilter extends AbstractIterator<AuditLog> {

        private boolean hasSeenObjectType = false;

        private final ObjectType objectType;
        private final Iterator<AuditLog> accountAuditLogs;

        private ObjectTypeFilter(final ObjectType objectType, final Iterator<AuditLog> accountAuditLogs) {
            this.objectType = objectType;
            this.accountAuditLogs = accountAuditLogs;
        }

        @Override
        protected AuditLog computeNext() {
            while (accountAuditLogs.hasNext()) {
                final AuditLog element = accountAuditLogs.next();
                if (predicate.apply(element)) {
                    hasSeenObjectType = true;
                    return element;
                } else if (hasSeenObjectType) {
                    // Optimization trick: audit log records are ordered first by table name
                    // (hence object type) - when we are done and we switch to another ObjectType,
                    // we are guaranteed there is nothing left to do
                    return endOfData();
                }
            }

            return endOfData();
        }

        private final Predicate<AuditLog> predicate = new Predicate<AuditLog>() {
            @Override
            public boolean apply(final AuditLog auditLog) {
                return objectType.equals(auditLog.getAuditedObjectType());
            }
        };
    }
}
