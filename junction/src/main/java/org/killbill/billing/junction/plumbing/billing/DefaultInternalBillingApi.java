/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBillingEvent;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultInternalBillingApi implements BillingInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalBillingApi.class);

    private static final long NANO_TO_MILLI_SEC = (1000L * 1000L);
    private static final int MAX_NB_EVENTS_TO_PRINT = 20;

    private final AccountInternalApi accountApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final CatalogInternalApi catalogInternalApi;
    private final BlockingCalculator blockCalculator;
    private final TagInternalApi tagApi;

    @Inject
    public DefaultInternalBillingApi(final AccountInternalApi accountApi,
                                     final SubscriptionBaseInternalApi subscriptionApi,
                                     final BlockingCalculator blockCalculator,
                                     final CatalogInternalApi catalogInternalApi,
                                     final TagInternalApi tagApi) {
        this.accountApi = accountApi;
        this.subscriptionApi = subscriptionApi;
        this.catalogInternalApi = catalogInternalApi;
        this.blockCalculator = blockCalculator;
        this.tagApi = tagApi;
    }

    @Override
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId, final DryRunArguments dryRunArguments, @Nullable final LocalDate cutoffDt, final InternalCallContext context) throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {

        long iniTs = System.nanoTime();

        final VersionedCatalog fullCatalog = catalogInternalApi.getFullCatalog(true, true, context);

        // Check to see if billing is off for the account
        final List<Tag> tagsForAccount = tagApi.getTagsForAccount(false, context);
        final List<Tag> accountTags = getTagsForObjectType(ObjectType.ACCOUNT, tagsForAccount, null);
        final boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(accountTags);
        final boolean found_INVOICING_DRAFT = is_AUTO_INVOICING_DRAFT(accountTags);
        final boolean found_INVOICING_REUSE_DRAFT = is_AUTO_INVOICING_REUSE_DRAFT(accountTags);

        final Set<UUID> skippedSubscriptions = new HashSet<UUID>();
        final DefaultBillingEventSet result;

        long subsIniTs = System.nanoTime();
        final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount = subscriptionApi.getSubscriptionsForAccount(fullCatalog, cutoffDt, context);
        long subsAfterTs = System.nanoTime();

        final ImmutableAccountData account = accountApi.getImmutableAccountDataById(accountId, context);
        result = new DefaultBillingEventSet(found_AUTO_INVOICING_OFF, found_INVOICING_DRAFT, found_INVOICING_REUSE_DRAFT);
        addBillingEventsForBundles(account, dryRunArguments, context, result, skippedSubscriptions, subscriptionsForAccount, fullCatalog, tagsForAccount);
        if (result.isEmpty()) {
            log.info("No billing event for accountId='{}'", accountId);
            return result;
        }

        // Pretty-print the events, before and after the blocking calculator does its magic
        final StringBuilder logStringBuilder = new StringBuilder("Computed billing events for accountId='").append(accountId).append("'");
        eventsToString(logStringBuilder, result);

        long bsIniTs = System.nanoTime();
        final boolean afterBlocking = blockCalculator.insertBlockingEvents(result, skippedSubscriptions, subscriptionsForAccount, fullCatalog, cutoffDt, context);
        long bsAfterTs = System.nanoTime();
        if (afterBlocking) {
            logStringBuilder.append("\nBilling Events After Blocking");
            eventsToString(logStringBuilder, result);
        }

        logStringBuilder.append(String.format("%nBilling Events total=%d mSec, subs=%d mSec, bs=%d mSec",
                                              (System.nanoTime() - iniTs) / NANO_TO_MILLI_SEC,
                                              (subsAfterTs - subsIniTs) / NANO_TO_MILLI_SEC,
                                              (bsAfterTs - bsIniTs) / NANO_TO_MILLI_SEC));

        log.info(logStringBuilder.toString());

        return result;
    }

    private void eventsToString(final StringBuilder stringBuilder, final SortedSet<BillingEvent> events) {
        int n = 0;
        for (final BillingEvent event : events) {
            if (n > MAX_NB_EVENTS_TO_PRINT) {
                // https://github.com/killbill/killbill/issues/1337
                stringBuilder.append("\n").append(String.format("... and %s more ...", events.size() - n));
                break;
            }
            stringBuilder.append("\n").append(event.toString());
            n++;
        }
    }

    private void addBillingEventsForBundles(final ImmutableAccountData account,
                                            final DryRunArguments dryRunArguments,
                                            final InternalCallContext context,
                                            final DefaultBillingEventSet result,
                                            final Set<UUID> skipSubscriptionsSet,
                                            final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount,
                                            final VersionedCatalog catalog,
                                            final List<Tag> tagsForAccount) throws AccountApiException, CatalogApiException, SubscriptionBaseApiException {
        final int currentAccountBCD = accountApi.getBCD(context);
        addBillingEventsForBundles(account,
                                   dryRunArguments,
                                   context,
                                   result,
                                   skipSubscriptionsSet,
                                   subscriptionsForAccount,
                                   catalog,
                                   tagsForAccount,
                                   currentAccountBCD);
    }

    private void addBillingEventsForBundles(final ImmutableAccountData account,
                                            final DryRunArguments dryRunArguments,
                                            final InternalCallContext context,
                                            final DefaultBillingEventSet result,
                                            final Set<UUID> skipSubscriptionsSet,
                                            final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount,
                                            final VersionedCatalog catalog,
                                            final List<Tag> tagsForAccount,
                                            final int currentAccountBCD) throws AccountApiException, CatalogApiException, SubscriptionBaseApiException {
        // In dryRun mode, when we care about invoice generated for new BASE subscription, no such bundle exists yet; we still
        // want to tap into subscriptionBase logic, so we make up a bundleId
        if (dryRunArguments != null &&
            dryRunArguments.getAction() == SubscriptionEventType.START_BILLING &&
            dryRunArguments.getBundleId() == null) {
            final UUID fakeBundleId = UUIDs.randomUUID();
            final List<SubscriptionBase> subscriptions = subscriptionApi.getSubscriptionsForBundle(fakeBundleId, dryRunArguments, context);
            addBillingEventsForSubscription(account, subscriptions, null, currentAccountBCD, context, result, skipSubscriptionsSet, catalog);
        }

        for (final UUID bundleId : subscriptionsForAccount.keySet()) {
            final DryRunArguments dryRunArgumentsForBundle = (dryRunArguments != null &&
                                                              dryRunArguments.getBundleId() != null &&
                                                              dryRunArguments.getBundleId().equals(bundleId)) ?
                                                             dryRunArguments : null;
            final List<SubscriptionBase> subscriptions;
            // In dryRun mode, optimization is intentionally left as is, since is not a common path.
            if (dryRunArgumentsForBundle == null || dryRunArgumentsForBundle.getAction() == null) {
                subscriptions = getSubscriptionsForAccountByBundleId(subscriptionsForAccount, bundleId);
            } else {
                subscriptions = subscriptionApi.getSubscriptionsForBundle(bundleId, dryRunArgumentsForBundle, context);
            }

            // Check if billing is off for the bundle
            final List<Tag> bundleTags = getTagsForObjectType(ObjectType.BUNDLE, tagsForAccount, bundleId);
            final boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(bundleTags);
            if (found_AUTO_INVOICING_OFF) {
                for (final SubscriptionBase subscription : subscriptions) { // billing is off so list sub ids in set to be excluded
                    result.getSubscriptionIdsWithAutoInvoiceOff().add(subscription.getId());
                }
            } else { // billing is not off
                final SubscriptionBase baseSubscription = subscriptions != null && !subscriptions.isEmpty() ? subscriptions.get(0) : null;
                addBillingEventsForSubscription(account, subscriptions, baseSubscription, currentAccountBCD, context, result, skipSubscriptionsSet, catalog);
            }
        }

        // If dryRun is specified, we don't want to update the account BCD value, so we initialize the flag updatedAccountBCD to true
        if (currentAccountBCD == 0) {
            final Integer accountBCDCandidate = computeAccountBCD(result);
            if (accountBCDCandidate == null) {
                return;
            }

            // Because we now have computed the real BCD, we need to re-compute the BillingEvents BCD for ACCOUNT alignments (see BillCycleDayCalculator#calculateBcdForAlignment).
            // The code could maybe be optimized (no need to re-run the full function?), but since it's run once per account, it's probably not worth it.
            result.clear();
            addBillingEventsForBundles(account, dryRunArguments, context, result, skipSubscriptionsSet, subscriptionsForAccount, catalog, tagsForAccount, accountBCDCandidate);

            final boolean dryRunMode = dryRunArguments != null;
            if (!dryRunMode) {
                log.info("Setting account BCD='{}', accountId='{}'", accountBCDCandidate, account.getId());
                accountApi.updateBCD(account.getExternalKey(), accountBCDCandidate, context);
            }
        }
    }

    private Integer computeAccountBCD(final BillingEventSet result) throws CatalogApiException {
        BillingEvent oldestAccountAlignedBillingEvent = null;

        for (final BillingEvent event : result) {
            if (event.getBillingAlignment() != BillingAlignment.ACCOUNT) {
                continue;
            }

            final BigDecimal recurringPrice = event.getRecurringPrice();
            final boolean hasRecurringPrice = recurringPrice != null; // Note: could be zero (BCD would still be set, by convention)
            final boolean hasUsage = event.getUsages() != null && !event.getUsages().isEmpty();
            if (!hasRecurringPrice &&
                !hasUsage) {
                // Nothing to bill, ignored for the purpose of BCD calculation
                continue;
            }

            if (oldestAccountAlignedBillingEvent == null ||
                event.getEffectiveDate().compareTo(oldestAccountAlignedBillingEvent.getEffectiveDate()) < 0 ||
                (event.getEffectiveDate().compareTo(oldestAccountAlignedBillingEvent.getEffectiveDate()) == 0 && event.getTotalOrdering().compareTo(oldestAccountAlignedBillingEvent.getTotalOrdering()) < 0)) {
                oldestAccountAlignedBillingEvent = event;
            }
        }

        if (oldestAccountAlignedBillingEvent == null) {
            return null;
        }

        // BCD in the account timezone
        final int accountBCDCandidate = oldestAccountAlignedBillingEvent.getBillCycleDayLocal();
        Preconditions.checkState(accountBCDCandidate > 0, "Wrong Account BCD calculation for event: " + oldestAccountAlignedBillingEvent);

        return accountBCDCandidate;
    }

    private void addBillingEventsForSubscription(final ImmutableAccountData account,
                                                 @Nullable final List<SubscriptionBase> subscriptions,
                                                 final SubscriptionBase baseSubscription,
                                                 final int currentAccountBCD,
                                                 final InternalCallContext context,
                                                 final DefaultBillingEventSet result,
                                                 final Set<UUID> skipSubscriptionsSet,
                                                 final VersionedCatalog catalog) throws SubscriptionBaseApiException, CatalogApiException {
        if (subscriptions == null) {
            return;
        }

        final Map<UUID, Integer> bcdCache = new HashMap<UUID, Integer>();

        for (final SubscriptionBase subscription : subscriptions) {
            // TODO Can we batch those ?
            final List<SubscriptionBillingEvent> billingTransitions = subscriptionApi.getSubscriptionBillingEvents(catalog, subscription, context);
            if (billingTransitions.isEmpty() ||
                (billingTransitions.get(0).getType() != SubscriptionBaseTransitionType.CREATE &&
                 billingTransitions.get(0).getType() != SubscriptionBaseTransitionType.TRANSFER)) {
                log.warn("Skipping billing events for subscription " + subscription.getId() + ": Does not start with a valid CREATE transition");
                skipSubscriptionsSet.add(subscription.getId());
                return;
            }

            Integer overridenBCD = null;
            int bcdLocal = 0;
            BillingAlignment alignment = null;
            for (final SubscriptionBillingEvent transition : billingTransitions) {

                if (transition.getType() != SubscriptionBaseTransitionType.CANCEL) {
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(transition.getPlan().getName(), transition.getPlanPhase().getPhaseType());
                    alignment = subscription.getBillingAlignment(spec, transition.getEffectiveDate(), catalog);

                    //
                    // A BCD_CHANGE transition defines a new billCycleDayLocal for the subscription and this overrides whatever computation
                    // occurs below (which is based on billing alignment policy). Also multiple of those BCD_CHANGE transitions could occur,
                    // to define different intervals with different billing cycle days.
                    //
                    overridenBCD = transition.getBcdLocal() != null ? transition.getBcdLocal() : overridenBCD;
                    bcdLocal = overridenBCD != null ?
                               overridenBCD :
                               calculateBcdForTransition(alignment, bcdCache, baseSubscription, subscription, currentAccountBCD, context);

                }

                final BillingEvent event = new DefaultBillingEvent(transition, subscription, bcdLocal, alignment, account.getCurrency());
                result.add(event);
            }
        }
    }

    private int calculateBcdForTransition(final BillingAlignment realBillingAlignment, final Map<UUID, Integer> bcdCache, final SubscriptionBase baseSubscription, final SubscriptionBase subscription, final int accountBillCycleDayLocal, final InternalTenantContext internalTenantContext) {
        BillingAlignment alignment = realBillingAlignment;
        if (alignment == BillingAlignment.ACCOUNT && accountBillCycleDayLocal == 0) {
            alignment = BillingAlignment.SUBSCRIPTION;
        }
        return BillCycleDayCalculator.calculateBcdForAlignment(bcdCache, subscription, baseSubscription, alignment, internalTenantContext, accountBillCycleDayLocal);
    }

    private boolean is_AUTO_INVOICING_OFF(final List<Tag> tags) {
        return ControlTagType.isAutoInvoicingOff(Collections2.transform(tags, new Function<Tag, UUID>() {
            @Override
            public UUID apply(final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }

    private boolean is_AUTO_INVOICING_DRAFT(final List<Tag> tags) {
        return Iterables.any(tags, new Predicate<Tag>() {
            @Override
            public boolean apply(final Tag input) {
                return input.getTagDefinitionId().equals(ControlTagType.AUTO_INVOICING_DRAFT.getId());
            }
        });
    }

    private boolean is_AUTO_INVOICING_REUSE_DRAFT(final List<Tag> tags) {
        return Iterables.any(tags, new Predicate<Tag>() {
            @Override
            public boolean apply(final Tag input) {
                return input.getTagDefinitionId().equals(ControlTagType.AUTO_INVOICING_REUSE_DRAFT.getId());
            }
        });
    }

    private List<Tag> getTagsForObjectType(final ObjectType objectType, final List<Tag> tags, @Nullable final UUID objectId) {
        return ImmutableList.<Tag>copyOf(Iterables.<Tag>filter(tags,
                                                               new Predicate<Tag>() {
                                                                   @Override
                                                                   public boolean apply(final Tag input) {
                                                                       if (objectId == null) {
                                                                           return objectType == input.getObjectType();
                                                                       } else {
                                                                           return objectType == input.getObjectType() && objectId.equals(input.getObjectId());
                                                                       }

                                                                   }
                                                               }));
    }

    private List<SubscriptionBase> getSubscriptionsForAccountByBundleId(final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount, final UUID bundleId) {
        return subscriptionsForAccount.containsKey(bundleId) ? subscriptionsForAccount.get(bundleId) : ImmutableList.<SubscriptionBase>of();
    }

}
