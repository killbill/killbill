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

package org.killbill.billing.junction.plumbing.billing;

import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;

public class DefaultInternalBillingApi implements BillingInternalApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInternalBillingApi.class);
    private final AccountInternalApi accountApi;
    private final BillCycleDayCalculator bcdCalculator;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final CatalogService catalogService;
    private final BlockingCalculator blockCalculator;
    private final TagInternalApi tagApi;

    @Inject
    public DefaultInternalBillingApi(final AccountInternalApi accountApi,
                                     final BillCycleDayCalculator bcdCalculator,
                                     final SubscriptionBaseInternalApi subscriptionApi,
                                     final BlockingCalculator blockCalculator,
                                     final CatalogService catalogService, final TagInternalApi tagApi) {
        this.accountApi = accountApi;
        this.bcdCalculator = bcdCalculator;
        this.subscriptionApi = subscriptionApi;
        this.catalogService = catalogService;
        this.blockCalculator = blockCalculator;
        this.tagApi = tagApi;
    }

    @Override
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId, final InternalCallContext context) {
        final List<SubscriptionBaseBundle> bundles = subscriptionApi.getBundlesForAccount(accountId, context);
        final DefaultBillingEventSet result = new DefaultBillingEventSet();
        result.setRecurrringBillingMode(catalogService.getCurrentCatalog().getRecurringBillingMode());

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

    private void addBillingEventsForBundles(final List<SubscriptionBaseBundle> bundles, final Account account, final InternalCallContext context,
                                            final DefaultBillingEventSet result) {
        for (final SubscriptionBaseBundle bundle : bundles) {
            final List<SubscriptionBase> subscriptions = subscriptionApi.getSubscriptionsForBundle(bundle.getId(), context);

            //Check if billing is off for the bundle
            final List<Tag> bundleTags = tagApi.getTags(bundle.getId(), ObjectType.BUNDLE, context);
            boolean found_AUTO_INVOICING_OFF = is_AUTO_INVOICING_OFF(bundleTags);
            if (found_AUTO_INVOICING_OFF) {
                for (final SubscriptionBase subscription : subscriptions) { // billing is off so list sub ids in set to be excluded
                    result.getSubscriptionIdsWithAutoInvoiceOff().add(subscription.getId());
                }
            } else { // billing is not off
                addBillingEventsForSubscription(subscriptions, bundle, account, context, result);
            }
        }
    }

    private void addBillingEventsForSubscription(final List<SubscriptionBase> subscriptions, final SubscriptionBaseBundle bundle, final Account account, final InternalCallContext context, final DefaultBillingEventSet result) {

        boolean updatedAccountBCD = false;
        for (final SubscriptionBase subscription : subscriptions) {
            for (final EffectiveSubscriptionInternalEvent transition : subscriptionApi.getBillingTransitions(subscription, context)) {
                try {
                    final int bcdLocal = bcdCalculator.calculateBcd(bundle, subscription, transition, account, context);

                    if (account.getBillCycleDayLocal() == 0 && !updatedAccountBCD) {
                        final MutableAccountData modifiedData = account.toMutableAccountData();
                        modifiedData.setBillCycleDayLocal(bcdLocal);
                        accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
                        updatedAccountBCD = true;
                    }

                    final BillingEvent event = new DefaultBillingEvent(account, transition, subscription, bcdLocal, account.getCurrency(), catalogService.getFullCatalog());
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
