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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.RequestedSubscriptionEvent;
import com.ning.billing.entitlement.api.user.SubscriptionEvent;
import com.ning.billing.invoice.api.EmptyInvoiceEvent;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.payment.api.PaymentErrorEvent;
import com.ning.billing.payment.api.PaymentInfoEvent;
import com.ning.billing.util.tag.api.ControlTagCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.ControlTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.ControlTagDeletionEvent;
import com.ning.billing.util.tag.api.UserTagCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionCreationEvent;
import com.ning.billing.util.tag.api.UserTagDefinitionDeletionEvent;
import com.ning.billing.util.tag.api.UserTagDeletionEvent;

public class AnalyticsListener {
    private final BusinessSubscriptionTransitionRecorder bstRecorder;
    private final BusinessAccountRecorder bacRecorder;
    private final BusinessInvoiceRecorder invoiceRecorder;
    private final BusinessTagRecorder tagRecorder;

    @Inject
    public AnalyticsListener(final BusinessSubscriptionTransitionRecorder bstRecorder,
                             final BusinessAccountRecorder bacRecorder,
                             final BusinessInvoiceRecorder invoiceRecorder,
                             final BusinessTagRecorder tagRecorder) {
        this.bstRecorder = bstRecorder;
        this.bacRecorder = bacRecorder;
        this.invoiceRecorder = invoiceRecorder;
        this.tagRecorder = tagRecorder;
    }

    @Subscribe
    public void handleEffectiveSubscriptionTransitionChange(final EffectiveSubscriptionEvent eventEffective) throws AccountApiException, EntitlementUserApiException {
        handleSubscriptionTransitionChange(eventEffective);
    }

    @Subscribe
    public void handleRequestedSubscriptionTransitionChange(final RequestedSubscriptionEvent eventRequested) throws AccountApiException, EntitlementUserApiException {
        if (eventRequested.getEffectiveTransitionTime().isAfter(eventRequested.getRequestedTransitionTime())) {
            handleSubscriptionTransitionChange(eventRequested);
        }
    }

    @Subscribe
    public void handleAccountCreation(final AccountCreationEvent event) {
        bacRecorder.accountCreated(event.getData());
    }

    @Subscribe
    public void handleAccountChange(final AccountChangeEvent event) {
        if (!event.hasChanges()) {
            return;
        }

        bacRecorder.accountUpdated(event.getAccountId());
    }

    @Subscribe
    public void handleInvoiceCreation(final InvoiceCreationEvent event) {
        invoiceRecorder.invoiceCreated(event.getInvoiceId());
    }

    @Subscribe
    public void handleNullInvoice(final EmptyInvoiceEvent event) {
        // Ignored for now
    }

    @Subscribe
    public void handlePaymentInfo(final PaymentInfoEvent paymentInfo) {
        bacRecorder.accountUpdated(paymentInfo);
    }

    @Subscribe
    public void handlePaymentError(final PaymentErrorEvent paymentError) {
        // TODO - we can't tie the error back to an account yet
    }

    @Subscribe
    public void handleControlTagCreation(final ControlTagCreationEvent event) {
        tagRecorder.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName());
    }

    @Subscribe
    public void handleControlTagDeletion(final ControlTagDeletionEvent event) {
        tagRecorder.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName());
    }

    @Subscribe
    public void handleUserTagCreation(final UserTagCreationEvent event) {
        tagRecorder.tagAdded(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName());
    }

    @Subscribe
    public void handleUserTagDeletion(final UserTagDeletionEvent event) {
        tagRecorder.tagRemoved(event.getObjectType(), event.getObjectId(), event.getTagDefinition().getName());
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

    @Subscribe
    public void handleRepairEntitlement(final RepairEntitlementEvent event) {
        // Ignored for now
    }

    private void handleSubscriptionTransitionChange(final SubscriptionEvent eventEffective) throws AccountApiException, EntitlementUserApiException {
        switch (eventEffective.getTransitionType()) {
            // A subscription enters either through migration or as newly created subscription
            case MIGRATE_ENTITLEMENT:
            case CREATE:
                bstRecorder.subscriptionCreated(eventEffective);
                break;
            case RE_CREATE:
                bstRecorder.subscriptionRecreated(eventEffective);
                break;
            case MIGRATE_BILLING:
                break;
            case CANCEL:
                bstRecorder.subscriptionCancelled(eventEffective);
                break;
            case CHANGE:
                bstRecorder.subscriptionChanged(eventEffective);
                break;
            case UNCANCEL:
                break;
            case PHASE:
                bstRecorder.subscriptionPhaseChanged(eventEffective);
                break;
            default:
                throw new RuntimeException("Unexpected event type " + eventEffective.getTransitionType());
        }
    }
}
