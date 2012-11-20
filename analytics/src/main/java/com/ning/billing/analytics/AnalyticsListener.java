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

package com.ning.billing.analytics;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.BusInternalEvent;
import com.ning.billing.util.events.ControlTagCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.ControlTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.ControlTagDeletionInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceAdjustmentInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.NullInvoiceInternalEvent;
import com.ning.billing.util.events.OverdueChangeInternalEvent;
import com.ning.billing.util.events.PaymentErrorInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;
import com.ning.billing.util.events.RepairEntitlementInternalEvent;
import com.ning.billing.util.events.RequestedSubscriptionInternalEvent;
import com.ning.billing.util.events.UserTagCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionCreationInternalEvent;
import com.ning.billing.util.events.UserTagDefinitionDeletionInternalEvent;
import com.ning.billing.util.events.UserTagDeletionInternalEvent;
import com.ning.billing.util.globallocker.GlobalLock;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.GlobalLocker.LockerType;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class AnalyticsListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsListener.class);
    private static final int NB_LOCK_TRY = 5;

    private final BusinessSubscriptionTransitionDao bstDao;
    private final BusinessAccountDao bacDao;
    private final BusinessInvoiceDao invoiceDao;
    private final BusinessOverdueStatusDao bosDao;
    private final BusinessInvoicePaymentDao bipDao;
    private final BusinessTagDao tagDao;
    private final GlobalLocker locker;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public AnalyticsListener(final BusinessSubscriptionTransitionDao bstDao,
                             final BusinessAccountDao bacDao,
                             final BusinessInvoiceDao invoiceDao,
                             final BusinessOverdueStatusDao bosDao,
                             final BusinessInvoicePaymentDao bipDao,
                             final BusinessTagDao tagDao,
                             final GlobalLocker locker,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.bstDao = bstDao;
        this.bacDao = bacDao;
        this.invoiceDao = invoiceDao;
        this.bosDao = bosDao;
        this.bipDao = bipDao;
        this.tagDao = tagDao;
        this.locker = locker;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handleEffectiveSubscriptionTransitionChange(final EffectiveSubscriptionInternalEvent eventEffective) {
        updateWithAccountLock(eventEffective.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // The event is used as a trigger to rebuild all transitions for this bundle
                bstDao.rebuildTransitionsForBundle(eventEffective.getBundleId(), createCallContext(eventEffective));
                return null;
            }
        });
    }

    @Subscribe
    public void handleRequestedSubscriptionTransitionChange(final RequestedSubscriptionInternalEvent eventRequested) {
        updateWithAccountLock(eventRequested.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // The event is used as a trigger to rebuild all transitions for this bundle
                bstDao.rebuildTransitionsForBundle(eventRequested.getBundleId(), createCallContext(eventRequested));
                return null;
            }
        });
    }

    @Subscribe
    public void handleRepairEntitlement(final RepairEntitlementInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // In case of repair, just rebuild all transitions
                bstDao.rebuildTransitionsForBundle(event.getBundleId(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleAccountCreation(final AccountCreationInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bacDao.accountUpdated(event.getId(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleAccountChange(final AccountChangeInternalEvent event) {
        if (!event.hasChanges()) {
            return;
        }

        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bacDao.accountUpdated(event.getAccountId(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleInvoiceCreation(final InvoiceCreationInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // The event is used as a trigger to rebuild all invoices and invoice items for this account
                invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleNullInvoice(final NullInvoiceInternalEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleInvoiceAdjustment(final InvoiceAdjustmentInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // The event is used as a trigger to rebuild all invoices and invoice items for this account
                invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handlePaymentInfo(final PaymentInfoInternalEvent paymentInfo) {
        updateWithAccountLock(paymentInfo.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bipDao.invoicePaymentPosted(paymentInfo.getAccountId(),
                                            paymentInfo.getPaymentId(),
                                            paymentInfo.getExtFirstPaymentRefId(),
                                            paymentInfo.getExtSecondPaymentRefId(),
                                            paymentInfo.getStatus().toString(),
                                            createCallContext(paymentInfo));
                return null;
            }
        });
    }

    @Subscribe
    public void handlePaymentError(final PaymentErrorInternalEvent paymentError) {
        updateWithAccountLock(paymentError.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bipDao.invoicePaymentPosted(paymentError.getAccountId(),
                                            paymentError.getPaymentId(),
                                            null,
                                            null,
                                            paymentError.getMessage(),
                                            createCallContext(paymentError));
                return null;
            }
        });
    }

    @Subscribe
    public void handleOverdueChange(final OverdueChangeInternalEvent changeEvent) {
        updateWithAccountLock(changeEvent.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                bosDao.overdueStatusChanged(changeEvent.getOverdueObjectType(), changeEvent.getOverdueObjectId(), createCallContext(changeEvent));
                return null;
            }
        });
    }

    @Subscribe
    public void handleControlTagCreation(final ControlTagCreationInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                tagDao.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleControlTagDeletion(final ControlTagDeletionInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                tagDao.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleUserTagCreation(final UserTagCreationInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                tagDao.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleUserTagDeletion(final UserTagDeletionInternalEvent event) {
        updateWithAccountLock(event.getAccountRecordId(), new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                tagDao.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
                return null;
            }
        });
    }

    @Subscribe
    public void handleControlTagDefinitionCreation(final ControlTagDefinitionCreationInternalEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleControlTagDefinitionDeletion(final ControlTagDefinitionDeletionInternalEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleUserTagDefinitionCreation(final UserTagDefinitionCreationInternalEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleUserTagDefinitionDeletion(final UserTagDefinitionDeletionInternalEvent event) {
        // Ignored for now
    }

    private InternalCallContext createCallContext(final BusInternalEvent event) {
        return internalCallContextFactory.createInternalCallContext(event.getTenantRecordId(), event.getAccountRecordId(),
                                                                    "AnalyticsService", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
    }

    private <T> T updateWithAccountLock(final Long accountRecordId, final Callable<T> task) {
        GlobalLock lock = null;
        try {
            final String lockKey = accountRecordId == null ? "0" : accountRecordId.toString();
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_ANALYTICS, lockKey, NB_LOCK_TRY);
            return task.call();
        } catch (Exception e) {
            log.warn("Exception while refreshing analytics tables", e);
        } finally {
            if (lock != null) {
                lock.release();
            }
        }

        return null;
    }
}
