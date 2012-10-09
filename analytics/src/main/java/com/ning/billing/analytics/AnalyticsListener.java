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
import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.RequestedSubscriptionEvent;
import com.ning.billing.invoice.api.InvoiceAdjustmentEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.NullInvoiceEvent;
import com.ning.billing.overdue.OverdueChangeEvent;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.bus.BusEvent;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.tag.api.ControlTagCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.ControlTagDeletionEvent;
import com.ning.billing.util.tag.api.UserTagCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.UserTagDeletionEvent;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

public class AnalyticsListener {

    private final BusinessSubscriptionTransitionRecorder bstRecorder;
    private final BusinessAccountRecorder bacRecorder;
    private final BusinessInvoiceDao invoiceDao;
    private final BusinessOverdueStatusRecorder bosRecorder;
    private final BusinessInvoicePaymentRecorder bipRecorder;
    private final BusinessTagRecorder tagRecorder;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public AnalyticsListener(final BusinessSubscriptionTransitionRecorder bstRecorder,
                             final BusinessAccountRecorder bacRecorder,
                             final BusinessInvoiceDao invoiceDao,
                             final BusinessOverdueStatusRecorder bosRecorder,
                             final BusinessInvoicePaymentRecorder bipRecorder,
                             final BusinessTagRecorder tagRecorder,
                             final InternalCallContextFactory internalCallContextFactory) {
        this.bstRecorder = bstRecorder;
        this.bacRecorder = bacRecorder;
        this.invoiceDao = invoiceDao;
        this.bosRecorder = bosRecorder;
        this.bipRecorder = bipRecorder;
        this.tagRecorder = tagRecorder;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Subscribe
    public void handleEffectiveSubscriptionTransitionChange(final EffectiveSubscriptionEvent eventEffective) throws AccountApiException, EntitlementUserApiException {
        // The event is used as a trigger to rebuild all transitions for this bundle
        bstRecorder.rebuildTransitionsForBundle(eventEffective.getBundleId(), createCallContext(eventEffective));
    }

    @Subscribe
    public void handleRequestedSubscriptionTransitionChange(final RequestedSubscriptionEvent eventRequested) throws AccountApiException, EntitlementUserApiException {
        // The event is used as a trigger to rebuild all transitions for this bundle
        bstRecorder.rebuildTransitionsForBundle(eventRequested.getBundleId(), createCallContext(eventRequested));
    }

    @Subscribe
    public void handleRepairEntitlement(final RepairEntitlementEvent event) {
        // In case of repair, just rebuild all transitions
        bstRecorder.rebuildTransitionsForBundle(event.getBundleId(), createCallContext(event));
    }

    @Subscribe
    public void handleAccountCreation(final AccountCreationEvent event) {
        bacRecorder.accountCreated(event.getData(), createCallContext(event));
    }

    @Subscribe
    public void handleAccountChange(final AccountChangeEvent event) {
        if (!event.hasChanges()) {
            return;
        }

        bacRecorder.accountUpdated(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handleInvoiceCreation(final InvoiceCreationEvent event) {
        // The event is used as a trigger to rebuild all invoices and invoice items for this account
        invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handleNullInvoice(final NullInvoiceEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleInvoiceAdjustment(final InvoiceAdjustmentEvent event) {
        // The event is used as a trigger to rebuild all invoices and invoice items for this account
        invoiceDao.rebuildInvoicesForAccount(event.getAccountId(), createCallContext(event));
    }

    @Subscribe
    public void handlePaymentInfo(final PaymentInfoEvent paymentInfo) {
        bipRecorder.invoicePaymentPosted(paymentInfo.getAccountId(),
                                         paymentInfo.getPaymentId(),
                                         paymentInfo.getExtFirstPaymentRefId(),
                                         paymentInfo.getExtSecondPaymentRefId(),
                                         paymentInfo.getStatus().toString(),
                                         createCallContext(paymentInfo));
    }

    @Subscribe
    public void handlePaymentError(final PaymentErrorEvent paymentError) {
        bipRecorder.invoicePaymentPosted(paymentError.getAccountId(),
                                         paymentError.getPaymentId(),
                                         null,
                                         null,
                                         paymentError.getMessage(),
                                         createCallContext(paymentError));
    }

    @Subscribe
    public void handleOverdueChange(final OverdueChangeEvent changeEvent) {
        bosRecorder.overdueStatusChanged(changeEvent.getOverdueObjectType(), changeEvent.getOverdueObjectId(), createCallContext(changeEvent));
    }

    @Subscribe
    public void handleControlTagCreation(final ControlTagCreationEvent event) {
        tagRecorder.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleControlTagDeletion(final ControlTagDeletionEvent event) {
        tagRecorder.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleUserTagCreation(final UserTagCreationEvent event) {
        tagRecorder.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleUserTagDeletion(final UserTagDeletionEvent event) {
        tagRecorder.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName(), createCallContext(event));
    }

    @Subscribe
    public void handleControlTagDefinitionCreation(final ControlTagDefinitionCreationEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleControlTagDefinitionDeletion(final ControlTagDefinitionDeletionEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleUserTagDefinitionCreation(final UserTagDefinitionCreationEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handleUserTagDefinitionDeletion(final UserTagDefinitionDeletionEvent event) {
        // Ignored for now
    }

    private InternalCallContext createCallContext(final BusEvent event) {
        return internalCallContextFactory.createInternalCallContext("AnalyticsService", CallOrigin.INTERNAL, UserType.SYSTEM, event.getUserToken());
    }
}
