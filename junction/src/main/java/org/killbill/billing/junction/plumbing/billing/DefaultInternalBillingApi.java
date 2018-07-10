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

package org.killbill.billing.junction.plumbing.billing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.events.SubscriptionInternalEvent;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.bcd.BillCycleDayCalculator;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class DefaultInternalBillingApi implements BillingInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalBillingApi.class);
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
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId, final DryRunArguments dryRunArguments, final InternalCallContext context) throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Catalog fullCatalog = catalogInternalApi.getFullCatalog(true, true, context);

        // Check to see if billing is off for the account
        final List<Tag> tagsForAccount = tagApi.getTagsForAccount(false, context);
        final List<Tag> accountTags = getTagsForObjectType(ObjectType.ACCOUNT, tagsForAccount, null);
        final boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(accountTags);
        final boolean found_INVOICING_DRAFT = is_AUTO_INVOICING_DRAFT(accountTags);
        final boolean found_INVOICING_REUSE_DRAFT = is_AUTO_INVOICING_REUSE_DRAFT(accountTags);

        final Set<UUID> skippedSubscriptions = new HashSet<UUID>();
        final DefaultBillingEventSet result;

        if (found_AUTO_INVOICING_OFF) {
            result = new DefaultBillingEventSet(true, found_INVOICING_DRAFT, found_INVOICING_REUSE_DRAFT); // billing is off, we are done
        } else {
            final List<SubscriptionBaseBundle> bundles = subscriptionApi.getBundlesForAccount(accountId, context);

            final ImmutableAccountData account = accountApi.getImmutableAccountDataById(accountId, context);
            result = new DefaultBillingEventSet(false, found_INVOICING_DRAFT, found_INVOICING_REUSE_DRAFT);
            addBillingEventsForBundles(bundles, account, dryRunArguments, context, result, skippedSubscriptions, fullCatalog, tagsForAccount);
        }

        if (result.isEmpty()) {
            log.info("No billing event for accountId='{}'", accountId);
            return result;
        }

        // Pretty-print the events, before and after the blocking calculator does its magic
        final StringBuilder logStringBuilder = new StringBuilder("Computed billing events for accountId='").append(accountId).append("'");
        eventsToString(logStringBuilder, result);
        if (blockCalculator.insertBlockingEvents(result, skippedSubscriptions, fullCatalog, context)) {
            logStringBuilder.append("\nBilling Events After Blocking");
            eventsToString(logStringBuilder, result);
        }
        log.info(logStringBuilder.toString());

        return result;
    }

    private void eventsToString(final StringBuilder stringBuilder, final SortedSet<BillingEvent> events) {
        for (final BillingEvent event : events) {
            stringBuilder.append("\n").append(event.toString());
        }
    }

    private void addBillingEventsForBundles(final List<SubscriptionBaseBundle> bundles, final ImmutableAccountData account, final DryRunArguments dryRunArguments, final InternalCallContext context,
                                            final DefaultBillingEventSet result, final Set<UUID> skipSubscriptionsSet, final Catalog catalog, final List<Tag> tagsForAccount) throws AccountApiException, CatalogApiException, SubscriptionBaseApiException {

        final boolean dryRunMode = dryRunArguments != null;

        // In dryRun mode, when we care about invoice generated for new BASE subscription, no such bundle exists yet; we still
        // want to tap into subscriptionBase logic, so we make up a bundleId
        if (dryRunArguments != null &&
            dryRunArguments.getAction() == SubscriptionEventType.START_BILLING &&
            dryRunArguments.getBundleId() == null) {
            final UUID fakeBundleId = UUIDs.randomUUID();
            final List<SubscriptionBase> subscriptions = subscriptionApi.getSubscriptionsForBundle(fakeBundleId, dryRunArguments, context);

            addBillingEventsForSubscription(account, subscriptions, null, dryRunMode, context, result, skipSubscriptionsSet, catalog);

        }

        final Map<UUID, List<SubscriptionBase>> subscriptionsForAccount = subscriptionApi.getSubscriptionsForAccount(catalog, context);

        for (final SubscriptionBaseBundle bundle : bundles) {
            final DryRunArguments dryRunArgumentsForBundle = (dryRunArguments != null &&
                                                              dryRunArguments.getBundleId() != null &&
                                                              dryRunArguments.getBundleId().equals(bundle.getId())) ?
                                                              dryRunArguments : null;
            final List<SubscriptionBase> subscriptions;
            // In dryRun mode, optimization is intentionally left as is, since is not a common path.
            if (dryRunArgumentsForBundle == null || dryRunArgumentsForBundle.getAction() == null) {
                subscriptions = getSubscriptionsForAccountByBundleId(subscriptionsForAccount,bundle.getId());
            } else {
                subscriptions = subscriptionApi.getSubscriptionsForBundle(bundle.getId(), dryRunArgumentsForBundle, context);
            }

            // Check if billing is off for the bundle
            final List<Tag> bundleTags = getTagsForObjectType(ObjectType.BUNDLE, tagsForAccount, bundle.getId());
            boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(bundleTags);
            if (found_AUTO_INVOICING_OFF) {
                for (final SubscriptionBase subscription : subscriptions) { // billing is off so list sub ids in set to be excluded
                    result.getSubscriptionIdsWithAutoInvoiceOff().add(subscription.getId());
                }
            } else { // billing is not off
                final SubscriptionBase baseSubscription = subscriptions != null && !subscriptions.isEmpty() ? subscriptions.get(0) : null;
                addBillingEventsForSubscription(account, subscriptions, baseSubscription, dryRunMode, context, result, skipSubscriptionsSet, catalog);
            }
        }
    }

    private void addBillingEventsForSubscription(final ImmutableAccountData account,
                                                 final List<SubscriptionBase> subscriptions,
                                                 final SubscriptionBase baseSubscription,
                                                 final boolean dryRunMode,
                                                 final InternalCallContext context,
                                                 final DefaultBillingEventSet result,
                                                 final Set<UUID> skipSubscriptionsSet,
                                                 final Catalog catalog) throws AccountApiException, CatalogApiException, SubscriptionBaseApiException {

        // If dryRun is specified, we don't want to to update the account BCD value, so we initialize the flag updatedAccountBCD to true
        boolean updatedAccountBCD = dryRunMode;

        final Map<UUID, Integer> bcdCache = new HashMap<UUID, Integer>();

        int currentAccountBCD = accountApi.getBCD(account.getId(), context);

        for (final SubscriptionBase subscription : subscriptions) {

            final List<EffectiveSubscriptionInternalEvent> billingTransitions = subscriptionApi.getBillingTransitions(subscription, context);
            if (billingTransitions.isEmpty() ||
                (billingTransitions.get(0).getTransitionType() != SubscriptionBaseTransitionType.CREATE &&
                 billingTransitions.get(0).getTransitionType() != SubscriptionBaseTransitionType.TRANSFER)) {
                log.warn("Skipping billing events for subscription " + subscription.getId() + ": Does not start with a valid CREATE transition");
                skipSubscriptionsSet.add(subscription.getId());
                return;
            }

            Integer overridenBCD = null;
            for (final EffectiveSubscriptionInternalEvent transition : billingTransitions) {
                //
                // A BCD_CHANGE transition defines a new billCycleDayLocal for the subscription and this overrides whatever computation
                // occurs below (which is based on billing alignment policy). Also multiple of those BCD_CHANGE transitions could occur,
                // to define different intervals with different billing cycle days.
                //
                overridenBCD = transition.getNextBillCycleDayLocal() != null ? transition.getNextBillCycleDayLocal() : overridenBCD;
                final int bcdLocal = overridenBCD != null ?
                                     overridenBCD :
                                     calculateBcdForTransition(catalog, bcdCache, baseSubscription, subscription, currentAccountBCD, transition, context);

                if (currentAccountBCD == 0 && !updatedAccountBCD) {
                    log.info("Setting account BCD='{}', accountId='{}'", bcdLocal, account.getId());
                    accountApi.updateBCD(account.getExternalKey(), bcdLocal, context);
                    updatedAccountBCD = true;
                }

                final BillingEvent event = new DefaultBillingEvent(transition, subscription, bcdLocal, account.getCurrency(), catalog);
                result.add(event);
            }
        }
    }

    private int calculateBcdForTransition(final Catalog catalog, final Map<UUID, Integer> bcdCache, final SubscriptionBase baseSubscription, final SubscriptionBase subscription, final int accountBillCycleDayLocal, final EffectiveSubscriptionInternalEvent transition, final InternalTenantContext internalTenantContext)
            throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final BillingAlignment alignment = catalog.billingAlignment(getPlanPhaseSpecifierFromTransition(catalog, transition), transition.getEffectiveTransitionTime(), subscription.getStartDate());
        return BillCycleDayCalculator.calculateBcdForAlignment(bcdCache, subscription, baseSubscription, alignment, internalTenantContext, accountBillCycleDayLocal);
    }

    private PlanPhaseSpecifier getPlanPhaseSpecifierFromTransition(final Catalog catalog, final SubscriptionInternalEvent transition) throws CatalogApiException {
        final Plan prevPlan = (transition.getPreviousPlan() != null) ? catalog.findPlan(transition.getPreviousPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;
        final Plan nextPlan = (transition.getNextPlan() != null) ? catalog.findPlan(transition.getNextPlan(), transition.getEffectiveTransitionTime(), transition.getSubscriptionStartDate()) : null;

        final Plan plan = (transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL) ? nextPlan : prevPlan;

        final PlanPhase prevPhase = prevPlan != null && transition.getPreviousPhase() != null ? prevPlan.findPhase(transition.getPreviousPhase()) : null;
        final PlanPhase nextPhase = nextPlan != null && transition.getNextPhase() != null ? nextPlan.findPhase(transition.getNextPhase()) : null;

        final PlanPhase phase = (transition.getTransitionType() != SubscriptionBaseTransitionType.CANCEL) ? nextPhase : prevPhase;

        return new PlanPhaseSpecifier(plan.getName(), phase.getPhaseType());
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

    private List<Tag> getTagsForObjectType(final ObjectType objectType, final List<Tag> tags, final @Nullable UUID objectId) {
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
