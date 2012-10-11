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

package com.ning.billing.overdue.applicator;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueCancellationPolicicy;
import com.ning.billing.overdue.OverdueChangeEvent;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.email.DefaultEmailSender;
import com.ning.billing.util.email.EmailApiException;
import com.ning.billing.util.email.EmailConfig;
import com.ning.billing.util.email.EmailSender;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import com.ning.billing.util.svcsapi.bus.Bus;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.samskivert.mustache.MustacheException;

public class OverdueStateApplicator<T extends Blockable> {

    private static final Logger log = LoggerFactory.getLogger(OverdueStateApplicator.class);

    private final BlockingInternalApi blockingApi;
    private final Clock clock;
    private final OverdueCheckPoster poster;
    private final Bus bus;
    private final AccountInternalApi accountApi;
    private final EntitlementInternalApi entitlementUserApi;
    private final OverdueEmailGenerator overdueEmailGenerator;
    private final EmailSender emailSender;

    @Inject
    public OverdueStateApplicator(final BlockingInternalApi accessApi, final AccountInternalApi accountApi, final EntitlementInternalApi entitlementUserApi,
                                  final Clock clock, final OverdueCheckPoster poster, final OverdueEmailGenerator overdueEmailGenerator,
                                  final EmailConfig config, final Bus bus) {
        this.blockingApi = accessApi;
        this.accountApi = accountApi;
        this.entitlementUserApi = entitlementUserApi;
        this.clock = clock;
        this.poster = poster;
        this.overdueEmailGenerator = overdueEmailGenerator;
        this.emailSender = new DefaultEmailSender(config);
        this.bus = bus;
    }

    public void apply(final OverdueState<T> firstOverdueState, final BillingState<T> billingState,
                      final T overdueable, final String previousOverdueStateName,
                      final OverdueState<T> nextOverdueState, final InternalCallContext context) throws OverdueException {
        try {
            // We did not reach first state, we we need to check if there is any pending condition for which we will not receive
            // any notifications.. (last two conditions are there for test purpose)
            if (nextOverdueState.isClearState() && firstOverdueState != null && billingState != null) {
                final LocalDate firstUnpaidInvoice = billingState.getDateOfEarliestUnpaidInvoice();
                if (firstUnpaidInvoice != null) {
                    final Period reevaluationInterval = firstOverdueState.getReevaluationInterval();
                    createFutureNotification(overdueable, firstUnpaidInvoice.toDateTimeAtCurrentTime().plus(reevaluationInterval), context);
                }
            }

            if (previousOverdueStateName.equals(nextOverdueState.getName())) {
                return; // That's it, we are done...
            }

            storeNewState(overdueable, nextOverdueState, context);

            cancelSubscriptionsIfRequired(overdueable, nextOverdueState, context);

            sendEmailIfRequired(billingState, overdueable, nextOverdueState, context);

            // Add entry in notification queue
            final Period reevaluationInterval = nextOverdueState.getReevaluationInterval();
            if (!nextOverdueState.isClearState()) {
                createFutureNotification(overdueable, clock.getUTCNow().plus(reevaluationInterval), context);
            }
        } catch (OverdueApiException e) {
            if (e.getCode() != ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL.getCode()) {
                throw new OverdueException(e);
            }
        }

        if (nextOverdueState.isClearState()) {
            clear(overdueable, context);
        }

        try {
            bus.post(createOverdueEvent(overdueable, previousOverdueStateName, nextOverdueState.getName()), context);
        } catch (Exception e) {
            log.error("Error posting overdue change event to bus", e);
        }
    }

    private OverdueChangeEvent createOverdueEvent(final T overdueable, final String previousOverdueStateName, final String nextOverdueStateName) throws BlockingApiException {
        return new DefaultOverdueChangeEvent(overdueable.getId(), Blockable.Type.get(overdueable), previousOverdueStateName, nextOverdueStateName, null);
    }

    protected void storeNewState(final T blockable, final OverdueState<T> nextOverdueState, final InternalCallContext context) throws OverdueException {
        try {
            blockingApi.setBlockingState(new DefaultBlockingState(blockable.getId(),
                                                                  nextOverdueState.getName(),
                                                                  Blockable.Type.get(blockable),
                                                                  OverdueService.OVERDUE_SERVICE_NAME,
                                                                  blockChanges(nextOverdueState),
                                                                  blockEntitlement(nextOverdueState),
                                                                  blockBilling(nextOverdueState)),
                                                                  context);
        } catch (Exception e) {
            throw new OverdueException(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, blockable.getId(), blockable.getClass().getName());
        }
    }

    private boolean blockChanges(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.blockChanges();
    }

    private boolean blockBilling(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    private boolean blockEntitlement(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    protected void createFutureNotification(final T overdueable, final DateTime timeOfNextCheck, final InternalCallContext context) {
        poster.insertOverdueCheckNotification(overdueable, timeOfNextCheck, context);
    }

    protected void clear(final T blockable, final InternalCallContext context) {
        // Need to clear the override table here too (when we add it)
        poster.clearNotificationsFor(blockable, context);
    }

    private void cancelSubscriptionsIfRequired(final T blockable, final OverdueState<T> nextOverdueState, final InternalCallContext context) throws OverdueException {
        if (nextOverdueState.getSubscriptionCancellationPolicy() == OverdueCancellationPolicicy.NONE) {
            return;
        }
        try {
            final ActionPolicy actionPolicy;
            switch (nextOverdueState.getSubscriptionCancellationPolicy()) {
                case END_OF_TERM:
                    actionPolicy = ActionPolicy.END_OF_TERM;
                    break;
                case IMMEDIATE:
                    actionPolicy = ActionPolicy.IMMEDIATE;
                    break;
                default:
                    throw new IllegalStateException("Unexpected OverdueCancellationPolicy " + nextOverdueState.getSubscriptionCancellationPolicy());
            }
            final List<Subscription> toBeCancelled = new LinkedList<Subscription>();
            computeSubscriptionsToCancel(blockable, toBeCancelled, context);
            for (final Subscription cur : toBeCancelled) {
                cur.cancelWithPolicy(clock.getUTCNow(), actionPolicy, context.toCallContext());
            }
        } catch (EntitlementUserApiException e) {
            throw new OverdueException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void computeSubscriptionsToCancel(final T blockable, final List<Subscription> result, final InternalTenantContext context) throws EntitlementUserApiException {
        if (blockable instanceof Subscription) {
            result.add((Subscription) blockable);
        } else if (blockable instanceof SubscriptionBundle) {
            for (final Subscription cur : entitlementUserApi.getSubscriptionsForBundle(blockable.getId(), context)) {
                computeSubscriptionsToCancel((T) cur, result, context);
            }
        } else if (blockable instanceof Account) {
            for (final SubscriptionBundle cur : entitlementUserApi.getBundlesForAccount(blockable.getId(), context)) {
                computeSubscriptionsToCancel((T) cur, result, context);
            }
        }
    }

    private void sendEmailIfRequired(final BillingState<T> billingState, final T overdueable,
                                     final OverdueState<T> nextOverdueState, final InternalTenantContext context) {
        // Note: we don't want to fail the full refresh call because sending the email failed.
        // That's the reason why we catch all exceptions here.
        // The alternative would be to: throw new OverdueApiException(e, ErrorCode.EMAIL_SENDING_FAILED);

        // If sending is not configured, skip
        if (nextOverdueState.getEnterStateEmailNotification() == null) {
            return;
        }

        // Retrieve the account
        final Account account;
        final Type overdueableType = Blockable.Type.get(overdueable);
        try {
            if (Type.SUBSCRIPTION.equals(overdueableType)) {
                final UUID bundleId = ((Subscription) overdueable).getBundleId();
                final SubscriptionBundle bundle = entitlementUserApi.getBundleFromId(bundleId, context);
                account = accountApi.getAccountById(bundle.getAccountId(), context);
            } else if (Type.SUBSCRIPTION_BUNDLE.equals(overdueableType)) {
                final UUID bundleId = ((SubscriptionBundle) overdueable).getId();
                final SubscriptionBundle bundle = entitlementUserApi.getBundleFromId(bundleId, context);
                account = accountApi.getAccountById(bundle.getAccountId(), context);
            } else if (Type.ACCOUNT.equals(overdueableType)) {
                account = (Account) overdueable;
            } else {
                log.warn("Unable to retrieve account for overdueable {} (type {})", overdueable.getId(), overdueableType);
                return;
            }
        } catch (EntitlementUserApiException e) {
            log.warn(String.format("Unable to retrieve account for overdueable %s (type %s)", overdueable.getId(), overdueableType), e);
            return;
        } catch (AccountApiException e) {
            log.warn(String.format("Unable to retrieve account for overdueable %s (type %s)", overdueable.getId(), overdueableType), e);
            return;
        }

        final List<String> to = ImmutableList.<String>of(account.getEmail());
        // TODO - should we look at the account CC: list?
        final List<String> cc = ImmutableList.<String>of();
        final String subject = nextOverdueState.getEnterStateEmailNotification().getSubject();

        try {
            // Generate and send the email
            final String emailBody = overdueEmailGenerator.generateEmail(account, billingState, overdueable, nextOverdueState);
            if (nextOverdueState.getEnterStateEmailNotification().isHTML()) {
                emailSender.sendHTMLEmail(to, cc, subject, emailBody);
            } else {
                emailSender.sendPlainTextEmail(to, cc, subject, emailBody);
            }
        } catch (IOException e) {
            log.warn(String.format("Unable to generate or send overdue notification email for account %s and overdueable %s", account.getId(), overdueable.getId()), e);
        } catch (EmailApiException e) {
            log.warn(String.format("Unable to send overdue notification email for account %s and overdueable %s", account.getId(), overdueable.getId()), e);
        } catch (MustacheException e) {
            log.warn(String.format("Unable to generate overdue notification email for account %s and overdueable %s", account.getId(), overdueable.getId()), e);
        }
    }
}
