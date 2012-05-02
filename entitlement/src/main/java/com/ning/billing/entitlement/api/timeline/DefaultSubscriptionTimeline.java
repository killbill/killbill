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
package com.ning.billing.entitlement.api.timeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;

public class DefaultSubscriptionTimeline implements SubscriptionTimeline  {

    private final UUID id;
    private final List<ExistingEvent> existingEvents;
    private final List<NewEvent> newEvents;
    private final List<DeletedEvent> deletedEvents;    
    
    public DefaultSubscriptionTimeline(final UUID id) {
        this.id = id;
        this.existingEvents = Collections.<SubscriptionTimeline.ExistingEvent>emptyList();
        this.deletedEvents =  Collections.<SubscriptionTimeline.DeletedEvent>emptyList();
        this.newEvents = Collections.<SubscriptionTimeline.NewEvent>emptyList();
    }
    
    public DefaultSubscriptionTimeline(SubscriptionTimeline input) {
        this.id = input.getId();
        this.existingEvents = (input.getExistingEvents() != null) ? new ArrayList<SubscriptionTimeline.ExistingEvent>(input.getExistingEvents()) : 
            Collections.<SubscriptionTimeline.ExistingEvent>emptyList();
        sortExistingEvent(this.existingEvents);
        this.deletedEvents = (input.getDeletedEvents() != null) ? new ArrayList<SubscriptionTimeline.DeletedEvent>(input.getDeletedEvents()) : 
            Collections.<SubscriptionTimeline.DeletedEvent>emptyList();
        this.newEvents = (input.getNewEvents() != null) ? new ArrayList<SubscriptionTimeline.NewEvent>(input.getNewEvents()) : 
            Collections.<SubscriptionTimeline.NewEvent>emptyList();
        sortNewEvent(this.newEvents);
    }
    
     // CTOR for returning events only
    public DefaultSubscriptionTimeline(SubscriptionDataRepair input, Catalog catalog) throws CatalogApiException {
        this.id = input.getId();
        this.existingEvents = toExistingEvents(catalog, input.getActiveVersion(), input.getCategory(), input.getEvents());
        this.deletedEvents = null;
        this.newEvents = null;
    }
    
   private List<ExistingEvent> toExistingEvents(final Catalog catalog, final long activeVersion, final ProductCategory category, final List<EntitlementEvent> events) 
       throws CatalogApiException {
        
        List<ExistingEvent> result = new LinkedList<SubscriptionTimeline.ExistingEvent>();
        
        String prevProductName = null; 
        BillingPeriod prevBillingPeriod = null;
        String prevPriceListName = null;
        PhaseType prevPhaseType = null;
        
        DateTime startDate = null;
        
        for (final EntitlementEvent cur : events) {
            
            // First active event is used to figure out which catalog version to use.
            //startDate = (startDate == null && cur.getActiveVersion() == activeVersion) ?  cur.getEffectiveDate() : startDate;
            
            // STEPH that needs tp be reviewed if we support mutli version events
            if (cur.getActiveVersion() != activeVersion) {
                continue;
            }
            startDate = (startDate == null) ?  cur.getEffectiveDate() : startDate;
            
            
            String productName = null; 
            BillingPeriod billingPeriod = null;
            String priceListName = null;
            PhaseType phaseType = null;

            ApiEventType apiType = null;
            switch (cur.getType()) {
            case PHASE:
                PhaseEvent phaseEV = (PhaseEvent) cur;
                phaseType = catalog.findPhase(phaseEV.getPhase(), cur.getEffectiveDate(), startDate).getPhaseType();
                productName = prevProductName;
                billingPeriod = catalog.findPhase(phaseEV.getPhase(), cur.getEffectiveDate(), startDate).getBillingPeriod();
                priceListName = prevPriceListName;
                break;

            case API_USER:
                ApiEvent userEV = (ApiEvent) cur;
                apiType = userEV.getEventType();
                Plan plan =  (userEV.getEventPlan() != null) ? catalog.findPlan(userEV.getEventPlan(), cur.getRequestedDate(), startDate) : null;
                phaseType = (userEV.getEventPlanPhase() != null) ? catalog.findPhase(userEV.getEventPlanPhase(), cur.getEffectiveDate(), startDate).getPhaseType() : prevPhaseType;
                productName = (plan != null) ? plan.getProduct().getName() : prevProductName;
                billingPeriod = (userEV.getEventPlanPhase() != null) ? catalog.findPhase(userEV.getEventPlanPhase(), cur.getEffectiveDate(), startDate).getBillingPeriod() : prevBillingPeriod;
                priceListName = (userEV.getPriceList() != null) ? userEV.getPriceList() : prevPriceListName;
                break;
            }

            final SubscriptionTransitionType transitionType = SubscriptionTransitionData.toSubscriptionTransitionType(cur.getType(), apiType);
            
            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
            result.add(new ExistingEvent() {
                @Override
                public SubscriptionTransitionType getSubscriptionTransitionType() {
                    return transitionType;
                }
                @Override
                public DateTime getRequestedDate() {
                    return cur.getRequestedDate();
                }
                @Override
                public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                    return spec;
                }
                @Override
                public UUID getEventId() {
                    return cur.getId();
                }
                @Override
                public DateTime getEffectiveDate() {
                    return cur.getEffectiveDate();
                }
            });
            
            prevProductName = productName; 
            prevBillingPeriod = billingPeriod;
            prevPriceListName = priceListName;
            prevPhaseType = phaseType;

        }
        sortExistingEvent(result);
        return result;
    }
   
   
   /*
   
   private List<ExistingEvent> toExistingEvents(final Catalog catalog, final long processingVersion, final ProductCategory category, final List<EntitlementEvent> events, List<ExistingEvent> result)
       throws CatalogApiException {
       
       
       String prevProductName = null; 
       BillingPeriod prevBillingPeriod = null;
       String prevPriceListName = null;
       PhaseType prevPhaseType = null;
       
       DateTime startDate = null;
       
       for (final EntitlementEvent cur : events) {
           
           if (processingVersion != cur.getActiveVersion()) {
               continue;
           }
           
           // First active event is used to figure out which catalog version to use.
           startDate = (startDate == null && cur.getActiveVersion() == processingVersion) ?  cur.getEffectiveDate() : startDate;
           
           String productName = null; 
           BillingPeriod billingPeriod = null;
           String priceListName = null;
           PhaseType phaseType = null;

           ApiEventType apiType = null;
           switch (cur.getType()) {
           case PHASE:
               PhaseEvent phaseEV = (PhaseEvent) cur;
               phaseType = catalog.findPhase(phaseEV.getPhase(), cur.getEffectiveDate(), startDate).getPhaseType();
               productName = prevProductName;
               billingPeriod = prevBillingPeriod;
               priceListName = prevPriceListName;
               break;

           case API_USER:
               ApiEvent userEV = (ApiEvent) cur;
               apiType = userEV.getEventType();
               Plan plan =  (userEV.getEventPlan() != null) ? catalog.findPlan(userEV.getEventPlan(), cur.getRequestedDate(), startDate) : null;
               phaseType = (userEV.getEventPlanPhase() != null) ? catalog.findPhase(userEV.getEventPlanPhase(), cur.getEffectiveDate(), startDate).getPhaseType() : prevPhaseType;
               productName = (plan != null) ? plan.getProduct().getName() : prevProductName;
               billingPeriod = (plan != null) ? plan.getBillingPeriod() : prevBillingPeriod;
               priceListName = (userEV.getPriceList() != null) ? userEV.getPriceList() : prevPriceListName;
               break;
           }

           final SubscriptionTransitionType transitionType = SubscriptionTransitionData.toSubscriptionTransitionType(cur.getType(), apiType);
           
           final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
           result.add(new ExistingEvent() {
               @Override
               public SubscriptionTransitionType getSubscriptionTransitionType() {
                   return transitionType;
               }
               @Override
               public DateTime getRequestedDate() {
                   return cur.getRequestedDate();
               }
               @Override
               public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                   return spec;
               }
               @Override
               public UUID getEventId() {
                   return cur.getId();
               }
               @Override
               public DateTime getEffectiveDate() {
                   return cur.getEffectiveDate();
               }
           });
           prevProductName = productName; 
           prevBillingPeriod = billingPeriod;
           prevPriceListName = priceListName;
           prevPhaseType = phaseType;
       }
   }
   */
   
   
    
    
    
    
    @Override
    public UUID getId() {
        return id;
    }
    
    @Override
    public List<DeletedEvent> getDeletedEvents() {
        return deletedEvents;
    }

    @Override
    public List<NewEvent> getNewEvents() {
        return newEvents;
    }
    
    @Override
    public List<ExistingEvent> getExistingEvents() {
        return existingEvents;
    }
    
    private void sortExistingEvent(final List<ExistingEvent> events) {
        if (events != null) {
            Collections.sort(events, new Comparator<ExistingEvent>() {
                @Override
                public int compare(ExistingEvent arg0, ExistingEvent arg1) {
                    return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
                }
            });
        }
    }
    private void sortNewEvent(final List<NewEvent> events) {
        if (events != null) {
            Collections.sort(events, new Comparator<NewEvent>() {
                @Override
                public int compare(NewEvent arg0, NewEvent arg1) {
                    return arg0.getRequestedDate().compareTo(arg1.getRequestedDate());
                }
            });
        }
    }
}
