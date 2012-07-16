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
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.entitlement.api.billing.ChargeThruApi;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.api.user.EffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.junction.api.BillingEventSet;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

public class DefaultBillingApi implements BillingApi {
    private static final String API_USER_NAME = "Billing Api";
    private static final Logger log = LoggerFactory.getLogger(DefaultBillingApi.class);
    private final ChargeThruApi chargeThruApi;
    private final CallContextFactory factory;
    private final AccountUserApi accountApi;
    private final BillCycleDayCalculator bcdCalculator;
    private final EntitlementUserApi entitlementUserApi;
    private final CatalogService catalogService;
    private final BlockingCalculator blockCalculator;
    private final TagUserApi tagApi;

    @Inject
    public DefaultBillingApi(final ChargeThruApi chargeThruApi, final CallContextFactory factory, final AccountUserApi accountApi,
                             final BillCycleDayCalculator bcdCalculator, final EntitlementUserApi entitlementUserApi, final BlockingCalculator blockCalculator,
                             final CatalogService catalogService, final TagUserApi tagApi) {

        this.chargeThruApi = chargeThruApi;
        this.accountApi = accountApi;
        this.bcdCalculator = bcdCalculator;
        this.factory = factory;
        this.entitlementUserApi = entitlementUserApi;
        this.catalogService = catalogService;
        this.blockCalculator = blockCalculator;
        this.tagApi = tagApi;
    }

    @Override
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(final UUID accountId) {
        final CallContext context = factory.createCallContext(API_USER_NAME, CallOrigin.INTERNAL, UserType.SYSTEM);

        final List<SubscriptionBundle> bundles = entitlementUserApi.getBundlesForAccount(accountId);
        final DefaultBillingEventSet result = new DefaultBillingEventSet();

        try {
            final Account account = accountApi.getAccountById(accountId);

            // Check to see if billing is off for the account
            final Map<String, Tag> accountTags = tagApi.getTags(accountId, ObjectType.ACCOUNT);
            if (accountTags.get(ControlTagType.AUTO_INVOICING_OFF.name()) != null) {
                result.setAccountAutoInvoiceIsOff(true);
                return result; // billing is off, we are done
            }

            addBillingEventsForBundles(bundles, account, context, result);
        } catch (AccountApiException e) {
            log.warn("Failed while getting BillingEvent", e);
        }

        debugLog(result, "********* Billing Events Raw");
        blockCalculator.insertBlockingEvents(result);
        debugLog(result, "*********  Billing Events After Blocking");

        return result;
    }


    private void debugLog(final SortedSet<BillingEvent> result, final String title) {
        log.debug(title);
        for (final BillingEvent aResult : result) {
            log.debug(aResult.toString());
        }
    }

    private void addBillingEventsForBundles(final List<SubscriptionBundle> bundles, final Account account, final CallContext context,
                                            final DefaultBillingEventSet result) {
        for (final SubscriptionBundle bundle : bundles) {
            final List<Subscription> subscriptions = entitlementUserApi.getSubscriptionsForBundle(bundle.getId());

            //Check if billing is off for the bundle
            final Map<String, Tag> bundleTags = tagApi.getTags(bundle.getId(), ObjectType.BUNDLE);
            if (bundleTags.get(ControlTagType.AUTO_INVOICING_OFF.name()) != null) {
                for (final Subscription subscription : subscriptions) { // billing is off so list sub ids in set to be excluded
                    result.getSubscriptionIdsWithAutoInvoiceOff().add(subscription.getId());
                }
            } else { // billing is not off
                addBillingEventsForSubscription(subscriptions, bundle, account, context, result);
            }
        }
    }

    private void addBillingEventsForSubscription(final List<Subscription> subscriptions, final SubscriptionBundle bundle, final Account account, final CallContext context, final DefaultBillingEventSet result) {
        for (final Subscription subscription : subscriptions) {
            for (final EffectiveSubscriptionEvent transition : subscription.getBillingTransitions()) {
                try {
                    final BillCycleDay bcd = bcdCalculator.calculateBcd(bundle, subscription, transition, account);

                    if (account.getBillCycleDay().getDayOfMonthUTC() == 0) {
                        final MutableAccountData modifiedData = account.toMutableAccountData();
                        modifiedData.setBillCycleDay(bcd);
                        accountApi.updateAccount(account.getExternalKey(), modifiedData, context);
                    }

                    final BillingEvent event = new DefaultBillingEvent(account, transition, subscription, bcd.getDayOfMonthUTC(), account.getCurrency(), catalogService.getFullCatalog());
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

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) throws EntitlementBillingApiException {
        final UUID result = chargeThruApi.getAccountIdFromSubscriptionId(subscriptionId);
        if (result == null) {
            throw new EntitlementBillingApiException(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID, subscriptionId.toString());
        }
        return result;
    }

    @Override
    public void setChargedThroughDate(final UUID subscriptionId, final DateTime ctd, final CallContext context) {
        chargeThruApi.setChargedThroughDate(subscriptionId, ctd, context);
    }
}
