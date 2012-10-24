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

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
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
        // TODO: use accountRecordId when switching to internal events and acquire the lock for all refreshes
        this.locker = locker;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handleEffectiveSubscriptionTransitionChange(final EffectiveSubscriptionInternalEvent eventEffective) throws AccountApiException, EntitlementUserApiException {
        // The event is used as a trigger to rebuild all transitions for this bundle
        bstDao.rebuildTransitionsForBundle(eventEffective.getBundleId(), createCallContext(eventEffective));
    }

    @Subscribe
    public void handleRequestedSubscriptionTransitionChange(final RequestedSubscriptionInternalEvent eventRequested) throws AccountApiException, EntitlementUserApiException {
        // The event is used as a trigger to rebuild all transitions for this bundle
        bstDao.rebuildTransitionsForBundle(eventRequested.getBundleId(), createCallContext(eventRequested));
    }

    @Subscribe
    public void handleRepairEntitlement(final RepairEntitlementInternalEvent event) {
        // In case of repair, just rebuild all transitions
        bstDao.rebuildTransitionsForBundle(event.getBundleId(), createCallContext(event));
    }

    @Subscribe
    public void handleAccountCreation(final AccountCreationInternalEvent event) {
        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_ANALYTICS, event.getId().toString(), NB_LOCK_TRY);
            bacDao.accountUpdated(event.getId(), createCallContext(event));
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    @Subscribe
    public void handleAccountChange(final AccountChangeInternalEvent event) {
        if (!event.hasChanges()) {
            return;
        }

        GlobalLock lock = null;
        try {
            lock = locker.lockWithNumberOfTries(LockerType.ACCOUNT_FOR_ANALYTICS, event.getAccountId().toString(), NB_LOCK_TRY);
            bacDao.accountUpdated(event.getAccountId(), createCallContext(event));
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
    }

    @Subscribe
    public void handleInvoiceCreation(final InvoiceCreationInternalEvent event) {
        // The event is used as a trigger to rebuild all invoices and invoice items for this account
        invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handleNullInvoice(final NullInvoiceInternalEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleInvoiceAdjustment(final InvoiceAdjustmentInternalEvent event) {
        // The event is used as a trigger to rebuild all invoices and invoice items for this account
        invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handlePaymentInfo(final PaymentInfoInternalEvent paymentInfo) {
        bipDao.invoicePaymentPosted(paymentInfo.getAccountId(),
                                    paymentInfo.getPaymentId(),
                                    paymentInfo.getExtFirstPaymentRefId(),
                                    paymentInfo.getExtSecondPaymentRefId(),
                                    paymentInfo.getStatus().toString(),
                                    createCallContext(paymentInfo));
    }

    @Subscribe
    public void handlePaymentError(final PaymentErrorInternalEvent paymentError) {
        bipDao.invoicePaymentPosted(paymentError.getAccountId(),
                                    paymentError.getPaymentId(),
                                    null,
                                    null,
                                    paymentError.getMessage(),
                                    createCallContext(paymentError));
    }

    @Subscribe
    public void handleOverdueChange(final OverdueChangeInternalEvent changeEvent) {
        bosDao.overdueStatusChanged(changeEvent.getOverdueObjectType(), changeEvent.getOverdueObjectId(), createCallContext(changeEvent));
    }

    @Subscribe
    public void handleControlTagCreation(final ControlTagCreationInternalEvent event) {
        tagDao.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleControlTagDeletion(final ControlTagDeletionInternalEvent event) {
        tagDao.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleUserTagCreation(final UserTagCreationInternalEvent event) {
        tagDao.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleUserTagDeletion(final UserTagDeletionInternalEvent event) {
        tagDao.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
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
        return internalCallContextFactory.createInternalCallContext(event.getTenantRecordId(), event.getAccountRecordId(), "AnalyticsService", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
    }
}
