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

package org.killbill.billing.subscription.api.timeline;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.catalog.DefaultSubscriptionCatalogApi;
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.bcd.BCDEvent;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.quantity.QuantityEvent;
import org.killbill.billing.subscription.events.quantity.QuantityEventData;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.clock.Clock;

public class DefaultSubscriptionBaseTimeline implements SubscriptionBaseTimeline {

    private final UUID id;
    private final List<ExistingEvent> existingEvents;
    private final Clock clock;

    public DefaultSubscriptionBaseTimeline(final DefaultSubscriptionBase input, final SubscriptionCatalog catalog, final Clock clock) throws CatalogApiException {
        this.id = input.getId();
        this.clock = clock;
        this.existingEvents = toExistingEvents(catalog, input.getCategory(), input.getEvents());
    }

    private List<ExistingEvent> toExistingEvents(final SubscriptionCatalog catalog, final ProductCategory category, final List<SubscriptionBaseEvent> events)
            throws CatalogApiException {

        final List<ExistingEvent> result = new LinkedList<SubscriptionBaseTimeline.ExistingEvent>();

        Plan prevPlan = null;
        String prevProductName = null;
        String prevPriceListName = null;
        PhaseType prevPhaseType = null;

        DateTime startDate = null;

        for (final SubscriptionBaseEvent cur : events) {

            if (!cur.isActive()) {
                continue;
            }
            startDate = (startDate == null) ? cur.getEffectiveDate() : startDate;

            String productName = null;
            String priceListName = null;
            PhaseType phaseType = null;
            Plan plan = null;
            String planPhaseName = null;
            Integer billCycleDayLocal = null;
            Integer quantity = null;

            ApiEventType apiType = null;
            switch (cur.getType()) {
                case PHASE:
                    final PhaseEvent phaseEV = (PhaseEvent) cur;
                    planPhaseName = phaseEV.getPhase();
                    // A PHASE event always occurs within the same plan (and is never the first event)
                    phaseType = prevPlan != null ? prevPlan.findPhase(phaseEV.getPhase()).getPhaseType() : null;
                    plan = prevPlan;
                    productName = prevProductName;
                    priceListName = prevPriceListName;
                    break;

                case BCD_UPDATE:
                    final BCDEvent bcdEvent = (BCDEvent) cur;
                    billCycleDayLocal = bcdEvent.getBillCycleDayLocal();
                    break;

                case QUANTITY_UPDATE:
                    final QuantityEvent quantityEvent = (QuantityEvent) cur;
                    quantity = quantityEvent.getQuantity();
                    break;

                case API_USER:
                    final ApiEvent userEV = (ApiEvent) cur;
                    apiType = userEV.getApiEventType();
                    planPhaseName = userEV.getEventPlanPhase();
                    plan = (userEV.getEventPlan() != null) ? catalog.findPlan(userEV.getEventPlan(), cur.getEffectiveDate(), startDate) : null;
                    phaseType = (plan != null && userEV.getEventPlanPhase() != null) ? plan.findPhase(userEV.getEventPlanPhase()).getPhaseType() : prevPhaseType;
                    productName = (plan != null) ? plan.getProduct().getName() : prevProductName;
                    priceListName = (userEV.getPriceList() != null) ? userEV.getPriceList() : prevPriceListName;
                    break;

                default:
                    break;
            }

            final SubscriptionBaseTransitionType transitionType = SubscriptionBaseTransitionData.toSubscriptionTransitionType(cur.getType(), apiType);

            final String planNameWithClosure = plan != null ? plan.getName() : null;
            final String planPhaseNameWithClosure = planPhaseName;
            final Integer billCycleDayLocalWithClosure = billCycleDayLocal;
            final Integer quantityWithClosure = quantity;
            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(planNameWithClosure, phaseType);
            result.add(new ExistingEvent() {
                @Override
                public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
                    return transitionType;
                }

                @Override
                public ProductCategory getProductCategory() {
                    return category;
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

                @Override
                public String getPlanName() {
                    return planNameWithClosure;
                }

                @Override
                public String getPlanPhaseName() {
                    return planPhaseNameWithClosure;
                }

                @Override
                public Integer getBillCycleDayLocal() {
                    return billCycleDayLocalWithClosure;
                }

                @Override
                public Integer getQuantity() {
                    return quantityWithClosure;
                }
            });

            prevPlan = plan;
            prevProductName = productName;
            prevPriceListName = priceListName;
            prevPhaseType = phaseType;

        }
        sortExistingEvent(result);
        return result;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public DateTime getCreatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DateTime getUpdatedDate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ExistingEvent> getExistingEvents() {
        return existingEvents;
    }

    private void sortExistingEvent(final List<ExistingEvent> events) {
        if (events != null) {
            Collections.sort(events, new Comparator<ExistingEvent>() {
                @Override
                public int compare(final ExistingEvent arg0, final ExistingEvent arg1) {
                    return arg0.getEffectiveDate().compareTo(arg1.getEffectiveDate());
                }
            });
        }
    }

}
