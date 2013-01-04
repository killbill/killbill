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

package com.ning.billing.junction.plumbing.billing;

import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class DefaultInternalBillingApi implements BillingInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalBillingApi.class);
    private final AccountInternalApi accountApi;
    private final BillCycleDayCalculator bcdCalculator;
    private final EntitlementInternalApi entitlementApi;
    private final CatalogService catalogService;
    private final BlockingCalculator blockCalculator;
    private final TagInternalApi tagApi;

    @Inject
    public DefaultInternalBillingApi(final AccountInternalApi accountApi,
                                     final BillCycleDayCalculator bcdCalculator,
                                     final EntitlementInternalApi entitlementApi,
                                     final BlockingCalculator blockCalculator,
                                     final CatalogService catalogService, final TagInternalApi tagApi) {
        this.accountApi = accountApi;
        this.bcdCalculator = bcdCalculator;
        this.entitlementApi = entitlementApi;
        this.catalogService = catalogService;
        this.blockCalculator = blockCalculator;
        this.tagApi = tagApi;
    }

    @Override
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId, final InternalCallContext context) {
        final List<SubscriptionBundle> bundles = entitlementApi.getBundlesForAccount(accountId, context);
        final DefaultBillingEventSet result = new DefaultBillingEventSet();

        try {
            final Account account = accountApi.getAccountById(accountId, context);

            // Check to see if billing is off for the account
            final List<Tag> accountTags = tagApi.getTags(accountId, ObjectType.ACCOUNT, context);
            final boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(accountTags);
            if (found_AUTO_INVOICING_OFF) {
                result.setAccountAutoInvoiceIsOff(true);
                return result; // billing is off, we are done
            }

            addBillingEventsForBundles(bundles, account, context, result);
        } catch (AccountApiException e) {
            log.warn("Failed while getting BillingEvent", e);
        }

        // Pretty-print the events, before and after the blocking calculator does its magic
        final StringBuilder logStringBuilder = new StringBuilder("Computed billing events for accountId ").append(accountId);
        eventsToString(logStringBuilder, result, "\nBilling Events Raw");
        blockCalculator.insertBlockingEvents(result, context);
        eventsToString(logStringBuilder, result, "\nBilling Events After Blocking");
        log.info(logStringBuilder.toString());

        return result;
    }

    private void eventsToString(final StringBuilder stringBuilder, final SortedSet<BillingEvent> events, final String title) {
        stringBuilder.append(title);
        for (final BillingEvent event : events) {
            stringBuilder.append("\n").append(event.toString());
        }
    }

    private void addBillingEventsForBundles(final List<SubscriptionBundle> bundles, final Account account, final InternalCallContext context,
                                            final DefaultBillingEventSet result) {
        for (final SubscriptionBundle bundle : bundles) {
            final List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(bundle.getId(), context);

            //Check if billing is off for the bundle
            final List<Tag> bundleTags = tagApi.getTags(bundle.getId(), ObjectType.BUNDLE, context);
            boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(bundleTags);
            if (found_AUTO_INVOICING_OFF) {
                for (final Subscription subscription : subscriptions) { // billing is off so list sub ids in set to be excluded
                    result.getSubscriptionIdsWithAutoInvoiceOff().add(subscription.getId());
                }
            } else { // billing is not off
                addBillingEventsForSubscription(subscriptions, bundle, account, context, result);
            }
        }
    }

    private void addBillingEventsForSubscription(final List<Subscription> subscriptions, final SubscriptionBundle bundle, final Account account, final InternalCallContext context, final DefaultBillingEventSet result) {
        for (final Subscription subscription : subscriptions) {
            for (final EffectiveSubscriptionInternalEvent transition : entitlementApi.getBillingTransitions(subscription, context)) {
                try {
                    final BillCycleDay bcd = bcdCalculator.calculateBcd(bundle, subscription, transition, account, context);

                    if (account.getBillCycleDay().getDayOfMonthUTC() == 0) {
                        final MutableAccountData modifiedData = account.toMutableAccountData();
                        modifiedData.setBillCycleDay(bcd);
                        accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
                    }

                    final BillingEvent event = new DefaultBillingEvent(account, transition, subscription, bcd, account.getCurrency(), catalogService.getFullCatalog());
                    result.add(event);
                } catch (CatalogApiException e) {
                    log.error("Failing to identify catalog components while creating BillingEvent from transition: " +
                              transition.getId().toString(), e);
                } catch (Exception e) {
                    log.warn("Failed while getting BillingEvent", e);
                }
            }
        }
    }

    private final boolean is_AUTO_INVOICING_OFF(final List<Tag> tags) {
        return ControlTagType.isAutoInvoicingOff(Collections2.transform(tags, new Function<Tag, UUID>() {
            @Nullable
            @Override
            public UUID apply(@Nullable final Tag tag) {
                return tag.getTagDefinitionId();
            }
        }));
    }
}
