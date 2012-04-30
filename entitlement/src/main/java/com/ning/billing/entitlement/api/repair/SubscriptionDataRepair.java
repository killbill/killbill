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
package com.ning.billing.entitlement.api.repair;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;
import org.omg.CORBA.Request;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionEventTransition;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;

public class SubscriptionDataRepair extends SubscriptionData {

    private final AddonUtils addonUtils;
    private final Clock clock;
    private final EntitlementDao repairDao;
    private final CatalogService catalogService;
    
    private final List<EntitlementEvent> initialEvents;

    // Low level events are ONLY used for Repair APIs
    protected List<EntitlementEvent> events;


    public SubscriptionDataRepair(SubscriptionBuilder builder, List<EntitlementEvent> initialEvents, SubscriptionApiService apiService,
            EntitlementDao dao, Clock clock, AddonUtils addonUtils, CatalogService catalogService) {
        super(builder, apiService, clock);
        this.repairDao = dao;
        this.addonUtils = addonUtils;
        this.clock = clock;
        this.catalogService = catalogService;
        this.initialEvents = initialEvents;
    }

    
    DateTime getLastUserEventEffectiveDate() {
        DateTime res = null;
        for (EntitlementEvent cur : events) {
            if (cur.getActiveVersion() != getActiveVersion()) {
                break;
            }
            if (cur.getType() == EventType.PHASE) {
                continue;
            }
            res = cur.getEffectiveDate();
        }
        return res;
    }

    public void addNewRepairEvent(final DefaultNewEvent input, final SubscriptionDataRepair baseSubscription, final List<SubscriptionDataRepair> addonSubscriptions, final CallContext context)
    throws EntitlementRepairException {

        try {
            final PlanPhaseSpecifier spec = input.getPlanPhaseSpecifier();
            switch(input.getSubscriptionTransitionType()) {
            case CREATE:
            case RE_CREATE:
                recreate(spec, input.getRequestedDate(), context);
                checkAddonRights(baseSubscription);
                break;
            case CHANGE:
                changePlan(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), input.getRequestedDate(), context);
                checkAddonRights(baseSubscription);
                trickleDownBPEffectForAddon(addonSubscriptions, getLastUserEventEffectiveDate(), context);
                break;
            case CANCEL:
                cancel(input.getRequestedDate(), false, context);
                trickleDownBPEffectForAddon(addonSubscriptions, getLastUserEventEffectiveDate(), context);
                break;
            case PHASE:
                break;
            default:
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_TYPE, input.getSubscriptionTransitionType(), id);
            }
        } catch (EntitlementUserApiException e) {
            throw new EntitlementRepairException(e);
        } catch (CatalogApiException e) {
            throw new EntitlementRepairException(e);
        }
    }


    public void addFutureAddonCancellation(List<SubscriptionDataRepair> addOnSubscriptionInRepair, final CallContext context) {

        if (category != ProductCategory.BASE) {
            return;
        }

        SubscriptionEventTransition pendingTransition = getPendingTransition();
        if (pendingTransition == null) {
            return;
        }
        Product baseProduct = (pendingTransition.getTransitionType() == SubscriptionTransitionType.CANCEL) ? null : 
            pendingTransition.getNextPlan().getProduct();

        addAddonCancellationIfRequired(addOnSubscriptionInRepair, baseProduct, pendingTransition.getEffectiveTransitionTime(), context);
    }
    
    private void trickleDownBPEffectForAddon(final List<SubscriptionDataRepair> addOnSubscriptionInRepair, final DateTime effectiveDate, final CallContext context)
     throws EntitlementUserApiException {

        if (category != ProductCategory.BASE) {
            return;
        }

        Product baseProduct = (getState() == SubscriptionState.CANCELLED ) ?
                null : getCurrentPlan().getProduct();
        addAddonCancellationIfRequired(addOnSubscriptionInRepair, baseProduct, effectiveDate, context);
    }
    
    
    
    private void addAddonCancellationIfRequired(final List<SubscriptionDataRepair> addOnSubscriptionInRepair, Product baseProduct, final DateTime effectiveDate, final CallContext context) {

        DateTime now = clock.getUTCNow();
        Iterator<SubscriptionDataRepair> it = addOnSubscriptionInRepair.iterator();
        while (it.hasNext()) {
            SubscriptionDataRepair cur = it.next();
            if (cur.getState() == SubscriptionState.CANCELLED ||
                    cur.getCategory() != ProductCategory.ADD_ON) {
                continue;
            }
            Plan addonCurrentPlan = cur.getCurrentPlan();
            if (baseProduct == null ||
                    addonUtils.isAddonIncluded(baseProduct, addonCurrentPlan) ||
                    ! addonUtils.isAddonAvailable(baseProduct, addonCurrentPlan)) {

                EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                .setSubscriptionId(cur.getId())
                .setActiveVersion(cur.getActiveVersion())
                .setProcessedDate(now)
                .setEffectiveDate(effectiveDate)
                .setRequestedDate(now)
                .setUserToken(context.getUserToken())
                .setFromDisk(true));
                repairDao.cancelSubscription(cur.getId(), cancelEvent, context, 0);
                cur.rebuildTransitions(repairDao.getEventsForSubscription(cur.getId()), catalogService.getFullCatalog());
            }
        }
    }

    private void checkAddonRights(final SubscriptionDataRepair baseSubscription) 
        throws EntitlementUserApiException, CatalogApiException  {
        if (category == ProductCategory.ADD_ON) {
            addonUtils.checkAddonCreationRights(baseSubscription, getCurrentPlan());
        }
    }
    
    public void rebuildTransitions(final List<EntitlementEvent> inputEvents, final Catalog catalog) {
        this.events = inputEvents;
        super.rebuildTransitions(inputEvents, catalog);
    }

    public List<EntitlementEvent> getEvents() {
        return events;
    }

    public List<EntitlementEvent> getInitialEvents() {
        return initialEvents;
    }

    
    public Collection<EntitlementEvent> getNewEvents() {
        Collection<EntitlementEvent> newEvents  = Collections2.filter(events, new Predicate<EntitlementEvent>() {
            @Override
            public boolean apply(EntitlementEvent input) {
                return ! initialEvents.contains(input);
            }
        });
        return newEvents;
    }
}
