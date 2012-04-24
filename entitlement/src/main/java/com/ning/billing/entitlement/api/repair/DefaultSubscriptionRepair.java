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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.ExistingEvent;
import com.ning.billing.entitlement.api.repair.SubscriptionRepair.NewEvent;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;

public class DefaultSubscriptionRepair implements SubscriptionRepair  {

    private final UUID id;
    private final List<ExistingEvent> existingEvents;
    private final List<NewEvent> newEvents;
    private final List<DeletedEvent> deletedEvents;    
    
    public DefaultSubscriptionRepair(final UUID id) {
        this.id = id;
        this.existingEvents = Collections.<SubscriptionRepair.ExistingEvent>emptyList();
        this.deletedEvents =  Collections.<SubscriptionRepair.DeletedEvent>emptyList();
        this.newEvents = Collections.<SubscriptionRepair.NewEvent>emptyList();
    }
    
    public DefaultSubscriptionRepair(SubscriptionRepair input) {
        this.id = input.getId();
        this.existingEvents = (input.getExistingEvents() != null) ? new ArrayList<SubscriptionRepair.ExistingEvent>(input.getExistingEvents()) : 
            Collections.<SubscriptionRepair.ExistingEvent>emptyList();
        sortExistingEvent(this.existingEvents);
        this.deletedEvents = (input.getDeletedEvents() != null) ? new ArrayList<SubscriptionRepair.DeletedEvent>(input.getDeletedEvents()) : 
            Collections.<SubscriptionRepair.DeletedEvent>emptyList();
        this.newEvents = (input.getNewEvents() != null) ? new ArrayList<SubscriptionRepair.NewEvent>(input.getNewEvents()) : 
            Collections.<SubscriptionRepair.NewEvent>emptyList();
        sortNewEvent(this.newEvents);
    }
    
     // CTOR for returning events only
    public DefaultSubscriptionRepair(SubscriptionData input) {
        this.id = input.getId();
        this.existingEvents = toExistingEvents(input.getCategory(), input.getAllTransitions());
        this.deletedEvents = null;
        this.newEvents = null;
    }
    
    private List<ExistingEvent> toExistingEvents(final ProductCategory category, final List<SubscriptionTransitionData> transitions) {
        List<ExistingEvent> result = new LinkedList<SubscriptionRepair.ExistingEvent>();
        for (final SubscriptionTransitionData cur : transitions) {
            
            String productName = null;
            BillingPeriod billingPeriod = null;
            String priceListName = null;
            PhaseType phaseType = null;
            if (cur.getTransitionType() != SubscriptionTransitionType.CANCEL) {
                productName = cur.getNextPlan().getProduct().getName();
                billingPeriod = cur.getNextPhase().getBillingPeriod(); 
                priceListName = cur.getNextPriceList(); 
                phaseType = cur.getNextPhase().getPhaseType();
            }
            
            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, category, billingPeriod, priceListName, phaseType);
            result.add(new ExistingEvent() {
                @Override
                public SubscriptionTransitionType getSubscriptionTransitionType() {
                    return cur.getTransitionType();
                }
                @Override
                public DateTime getRequestedDate() {
                    return cur.getRequestedTransitionTime();
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
                    return cur.getEffectiveTransitionTime();
                }
            });
        }
        sortExistingEvent(result);
        return result;
    }
    
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
