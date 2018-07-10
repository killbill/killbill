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

package org.killbill.billing.overdue.applicator;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.inject.Named;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EntitlementInternalApi;
import org.killbill.billing.entitlement.api.BlockingApiException;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.events.OverdueChangeInternalEvent;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueCancellationPolicy;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.overdue.notification.OverdueCheckNotificationKey;
import org.killbill.billing.overdue.notification.OverdueCheckNotifier;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.bus.api.PersistentBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class OverdueStateApplicator {

    private static final Logger log = LoggerFactory.getLogger(OverdueStateApplicator.class);

    private final BlockingInternalApi blockingApi;
    private final OverduePoster checkPoster;
    private final PersistentBus bus;
    private final AccountInternalApi accountApi;
    private final EntitlementApi entitlementApi;
    private final EntitlementInternalApi entitlementInternalApi;
    private final TagInternalApi tagApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public OverdueStateApplicator(final BlockingInternalApi accessApi,
                                  final AccountInternalApi accountApi,
                                  final EntitlementApi entitlementApi,
                                  final EntitlementInternalApi entitlementInternalApi,
                                  @Named(DefaultOverdueModule.OVERDUE_NOTIFIER_CHECK_NAMED) final OverduePoster checkPoster,
                                  final PersistentBus bus,
                                  final TagInternalApi tagApi,
                                  final InternalCallContextFactory internalCallContextFactory) {

        this.blockingApi = accessApi;
        this.accountApi = accountApi;
        this.entitlementApi = entitlementApi;
        this.entitlementInternalApi = entitlementInternalApi;
        this.checkPoster = checkPoster;
        this.tagApi = tagApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.bus = bus;
    }

    public void apply(final DateTime effectiveDate, final OverdueStateSet overdueStateSet, final BillingState billingState,
                      final ImmutableAccountData account, final OverdueState previousOverdueState,
                      final OverdueState nextOverdueState, final InternalCallContext context) throws OverdueException, OverdueApiException {
        if (isAccountTaggedWith_OVERDUE_ENFORCEMENT_OFF(context)) {
            log.debug("OverdueStateApplicator: apply returns because account (recordId={}) is set with OVERDUE_ENFORCEMENT_OFF", context.getAccountRecordId());
            return;
        }

        log.debug("OverdueStateApplicator: time={}, previousState={}, nextState={}, billingState={}", effectiveDate, previousOverdueState, nextOverdueState, billingState);

        final OverdueState firstOverdueState = overdueStateSet.getFirstState();
        final boolean conditionForNextNotfication = !nextOverdueState.isClearState() ||
                                                    // We did not reach the first state yet but we have an unpaid invoice
                                                    (firstOverdueState != null && billingState != null && billingState.getDateOfEarliestUnpaidInvoice() != null);

        if (conditionForNextNotfication) {
            final Period reevaluationInterval = getReevaluationInterval(overdueStateSet, nextOverdueState);
            // If there is no configuration in the config, we assume this is because the overdue conditions are not time based and so there is nothing to retry
            if (reevaluationInterval == null) {
                log.debug("OverdueStateApplicator <notificationQ>: missing InitialReevaluationInterval from config, NOT inserting notification for account {}", account.getId());
            } else {
                log.debug("OverdueStateApplicator <notificationQ>: inserting notification for account={}, time={}", account.getId(), effectiveDate.plus(reevaluationInterval));
                createFutureNotification(account, effectiveDate.plus(reevaluationInterval), context);
            }
        } else if (nextOverdueState.isClearState()) {
            clearFutureNotification(account, context);
        }

        if (previousOverdueState.getName().equals(nextOverdueState.getName())) {
            log.debug("OverdueStateApplicator is no-op: previousState={}, nextState={}", previousOverdueState, nextOverdueState);
            return;
        } else {
            log.debug("OverdueStateApplicator has new state: previousState={}, nextState={}", previousOverdueState, nextOverdueState);
        }

        cancelSubscriptionsIfRequired(effectiveDate, account, nextOverdueState, context);

        avoid_extra_credit_by_toggling_AUTO_INVOICE_OFF(account, previousOverdueState, nextOverdueState, context);

        // Make sure to store the new state last here: the entitlement DAO will send a BlockingTransitionInternalEvent
        // on the bus to which invoice will react. We need the latest state (including AUTO_INVOICE_OFF tag for example)
        // to be present in the database first.
        storeNewState(effectiveDate, account, nextOverdueState, context);

        final OverdueChangeInternalEvent event;
        try {
            event = createOverdueEvent(account, previousOverdueState.getName(), nextOverdueState.getName(), isBlockBillingTransition(previousOverdueState, nextOverdueState),
                                       isUnblockBillingTransition(previousOverdueState, nextOverdueState), context);
        } catch (final BlockingApiException e) {
            log.warn("Failed to create OverdueChangeInternalEvent for accountId='{}'", account.getId(), e);
            return;
        }

        try {
            bus.post(event);
        } catch (final Exception e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private Period getReevaluationInterval(final OverdueStateSet overdueStateSet, final OverdueState nextOverdueState) throws OverdueException {
        try {
            if (nextOverdueState.isClearState()) {
                return overdueStateSet.getInitialReevaluationInterval();
            } else {
                return nextOverdueState.getAutoReevaluationInterval().toJodaPeriod();
            }
        } catch (final OverdueApiException e) {
            if (e.getCode() == ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL.getCode()) {
                return null;
            } else {
                throw new OverdueException(e);
            }
        }
    }

    private void avoid_extra_credit_by_toggling_AUTO_INVOICE_OFF(final ImmutableAccountData account, final OverdueState previousOverdueState,
                                                                 final OverdueState nextOverdueState, final InternalCallContext context) throws OverdueApiException {
        if (isBlockBillingTransition(previousOverdueState, nextOverdueState)) {
            set_AUTO_INVOICE_OFF_on_blockedBilling(account.getId(), context);
        } else if (isUnblockBillingTransition(previousOverdueState, nextOverdueState)) {
            remove_AUTO_INVOICE_OFF_on_clear(account.getId(), context);
        }
    }

    public void clear(final DateTime effectiveDate, final ImmutableAccountData account, final OverdueState previousOverdueState, final OverdueState clearState, final InternalCallContext context) throws OverdueException {

        log.debug("OverdueStateApplicator:clear : time = " + effectiveDate + ", previousState = " + previousOverdueState.getName());

        storeNewState(effectiveDate, account, clearState, context);

        clearFutureNotification(account, context);

        try {
            avoid_extra_credit_by_toggling_AUTO_INVOICE_OFF(account, previousOverdueState, clearState, context);
        } catch (final OverdueApiException e) {
            throw new OverdueException(e);
        }

        final OverdueChangeInternalEvent event;
        try {
            event = createOverdueEvent(account, previousOverdueState.getName(), clearState.getName(), isBlockBillingTransition(previousOverdueState, clearState),
                                       isUnblockBillingTransition(previousOverdueState, clearState), context);
        } catch (final BlockingApiException e) {
            log.warn("Failed to create OverdueChangeInternalEvent for accountId='{}'", account.getId(), e);
            return;
        }

        try {
            bus.post(event);
        } catch (final Exception e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private OverdueChangeInternalEvent createOverdueEvent(final ImmutableAccountData overdueable, final String previousOverdueStateName, final String nextOverdueStateName,
                                                          final boolean isBlockedBilling, final boolean isUnblockedBilling, final InternalCallContext context) throws BlockingApiException {
        return new DefaultOverdueChangeEvent(overdueable.getId(), previousOverdueStateName, nextOverdueStateName, isBlockedBilling, isUnblockedBilling,
                                             context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
    }

    protected void storeNewState(final DateTime effectiveDate, final ImmutableAccountData blockable, final OverdueState nextOverdueState, final InternalCallContext context) throws OverdueException {
        try {
            blockingApi.setBlockingState(new DefaultBlockingState(blockable.getId(),
                                                                  BlockingStateType.ACCOUNT,
                                                                  nextOverdueState.getName(),
                                                                  OverdueService.OVERDUE_SERVICE_NAME,
                                                                  blockChanges(nextOverdueState),
                                                                  blockEntitlement(nextOverdueState),
                                                                  blockBilling(nextOverdueState),
                                                                  effectiveDate),
                                         context);
        } catch (final Exception e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, blockable.getId(), blockable.getClass().getName());
        }
    }

    private void set_AUTO_INVOICE_OFF_on_blockedBilling(final UUID accountId, final InternalCallContext context) throws OverdueApiException {
        try {
            tagApi.addTag(accountId, ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), context);
        } catch (final TagApiException e) {
            throw new OverdueApiException(e);
        }
    }

    private void remove_AUTO_INVOICE_OFF_on_clear(final UUID accountId, final InternalCallContext context) throws OverdueApiException {
        try {
            tagApi.removeTag(accountId, ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), context);
        } catch (final TagApiException e) {
            if (e.getCode() != ErrorCode.TAG_DOES_NOT_EXIST.getCode()) {
                throw new OverdueApiException(e);
            }
        }
    }

    private boolean isBlockBillingTransition(final OverdueState prevOverdueState, final OverdueState nextOverdueState) {
        return !blockBilling(prevOverdueState) && blockBilling(nextOverdueState);
    }

    private boolean isUnblockBillingTransition(final OverdueState prevOverdueState, final OverdueState nextOverdueState) {
        return blockBilling(prevOverdueState) && !blockBilling(nextOverdueState);
    }

    private boolean blockChanges(final OverdueState nextOverdueState) {
        return nextOverdueState.isBlockChanges() || nextOverdueState.isDisableEntitlementAndChangesBlocked();
    }

    private boolean blockBilling(final OverdueState nextOverdueState) {
        return nextOverdueState.isDisableEntitlementAndChangesBlocked();
    }

    private boolean blockEntitlement(final OverdueState nextOverdueState) {
        return nextOverdueState.isDisableEntitlementAndChangesBlocked();
    }

    protected void createFutureNotification(final ImmutableAccountData account, final DateTime timeOfNextCheck, final InternalCallContext context) {
        final OverdueCheckNotificationKey notificationKey = new OverdueCheckNotificationKey(account.getId());
        checkPoster.insertOverdueNotification(account.getId(), timeOfNextCheck, OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE, notificationKey, context);
    }

    protected void clearFutureNotification(final ImmutableAccountData account, final InternalCallContext context) {
        // Need to clear the override table here too (when we add it)
        checkPoster.clearOverdueCheckNotifications(account.getId(), OverdueCheckNotifier.OVERDUE_CHECK_NOTIFIER_QUEUE, OverdueCheckNotificationKey.class, context);
    }

    private void cancelSubscriptionsIfRequired(final DateTime effectiveDate, final ImmutableAccountData account, final OverdueState nextOverdueState, final InternalCallContext context) throws OverdueException {
        if (nextOverdueState.getOverdueCancellationPolicy() == OverdueCancellationPolicy.NONE) {
            return;
        }

        final CallContext callContext = internalCallContextFactory.createCallContext(context);
        try {
            final BillingActionPolicy actionPolicy;
            switch (nextOverdueState.getOverdueCancellationPolicy()) {
                case END_OF_TERM:
                    actionPolicy = BillingActionPolicy.END_OF_TERM;
                    break;
                case IMMEDIATE:
                    actionPolicy = BillingActionPolicy.IMMEDIATE;
                    break;
                default:
                    throw new IllegalStateException("Unexpected OverdueCancellationPolicy " + nextOverdueState.getOverdueCancellationPolicy());
            }
            final List<Entitlement> toBeCancelled = new LinkedList<Entitlement>();
            computeEntitlementsToCancel(account, toBeCancelled, callContext);

            try {
                entitlementInternalApi.cancel(toBeCancelled, context.toLocalDate(effectiveDate), actionPolicy, ImmutableList.<PluginProperty>of(), context);
            } catch (final EntitlementApiException e) {
                throw new OverdueException(e);
            }
        } catch (final EntitlementApiException e) {
            throw new OverdueException(e);
        }
    }

    private void computeEntitlementsToCancel(final ImmutableAccountData account, final List<Entitlement> result, final CallContext context) throws EntitlementApiException {
        final List<Entitlement> allEntitlementsForAccountId = entitlementApi.getAllEntitlementsForAccountId(account.getId(), context);
        // Entitlement is smart enough and will cancel the associated add-ons. See also discussion in https://github.com/killbill/killbill/issues/94
        final Collection<Entitlement> allEntitlementsButAddonsForAccountId = Collections2.<Entitlement>filter(allEntitlementsForAccountId,
                                                                                                              new Predicate<Entitlement>() {
                                                                                                                  @Override
                                                                                                                  public boolean apply(final Entitlement entitlement) {
                                                                                                                      // Note: this would miss add-ons created in the future. We should expose a new API to do something similar to EventsStreamBuilder#findBaseSubscription
                                                                                                                      return !ProductCategory.ADD_ON.equals(entitlement.getLastActiveProductCategory());
                                                                                                                  }
                                                                                                              });
        result.addAll(allEntitlementsButAddonsForAccountId);
    }

    //
    // Uses callcontext information to retrieve account matching the Overduable object and check whether we should do any overdue processing
    //
    private boolean isAccountTaggedWith_OVERDUE_ENFORCEMENT_OFF(final InternalCallContext context) throws OverdueException {

        try {
            final UUID accountId = accountApi.getByRecordId(context.getAccountRecordId(), context);

            final List<Tag> accountTags = tagApi.getTags(accountId, ObjectType.ACCOUNT, context);
            for (final Tag cur : accountTags) {
                if (cur.getTagDefinitionId().equals(ControlTagType.OVERDUE_ENFORCEMENT_OFF.getId())) {
                    return true;
                }
            }
            return false;
        } catch (final AccountApiException e) {
            throw new OverdueException(e);
        }
    }
}
