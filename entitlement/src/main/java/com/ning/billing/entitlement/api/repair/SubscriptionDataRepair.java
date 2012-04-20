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

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.events.EntitlementEvent;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;

public class SubscriptionDataRepair extends SubscriptionData {

    private final AddonUtils addonUtils;
    private final Clock clock;

    private final List<EntitlementEvent> initialEvents;

    // Low level events are ONLY used for Repair APIs
    protected List<EntitlementEvent> events;


    public SubscriptionDataRepair(SubscriptionBuilder builder, List<EntitlementEvent> initialEvents, SubscriptionApiService apiService,
            Clock clock, AddonUtils addonUtils) {
        super(builder, apiService, clock);
        this.addonUtils = addonUtils;
        this.clock = clock;
        this.initialEvents = initialEvents;
    }

    public void addNewRepairEvent(final DefaultNewEvent input, final SubscriptionDataRepair baseSubscription, final List<SubscriptionDataRepair> addonSubscriptions, final CallContext context)
    throws EntitlementRepairException {


        try {
            final PlanPhaseSpecifier spec = input.getPlanPhaseSpecifier();
            switch(input.getSubscriptionTransitionType()) {
            case CREATE:
            case RE_CREATE:
                recreate(spec, input.getRequestedDate(), context);
                break;
            case CHANGE:
                changePlan(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), input.getRequestedDate(), context);
                break;
            case CANCEL:
                cancel(input.getRequestedDate(), false, context);
                break;
            case PHASE:
                break;
            default:
                throw new EntitlementRepairException(ErrorCode.ENT_REPAIR_UNKNOWN_TYPE, input.getSubscriptionTransitionType(), id);
            }
            
            trickleDownBPEffectForAddon(addonSubscriptions, input.getRequestedDate(), context);
            checkAddonRights(baseSubscription);
            
        } catch (EntitlementUserApiException e) {
            throw new EntitlementRepairException(e);
        } catch (CatalogApiException e) {
            throw new EntitlementRepairException(e);
        }
    }


    private void trickleDownBPEffectForAddon(final List<SubscriptionDataRepair> addonSubscriptions, final DateTime requestedDate, final CallContext context)
     throws EntitlementUserApiException {

        if (category != ProductCategory.BASE) {
            return;
        }
        
        DateTime now = clock.getUTCNow();
        Product baseProduct = (getState() == SubscriptionState.CANCELLED ) ?
                null : getCurrentPlan().getProduct();

        Iterator<SubscriptionDataRepair> it = addonSubscriptions.iterator();
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

                cur.cancel(requestedDate, false, context);
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
