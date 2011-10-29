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

package com.ning.billing.entitlement.alignment;

import java.util.LinkedList;
import java.util.List;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.PlanAlignmentChange;
import com.ning.billing.catalog.api.PlanAlignmentCreate;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PlanSpecifier;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;

public class PlanAligner implements IPlanAligner {

    // STEPH this is a bit broken right now until we figure out to retrieve ICatalog
    private /*final*/ ICatalog catalog;

    public PlanAligner() {
        //this.catalog = Engine.getInstance().getCatalog();
    }

    public void init(ICatalog catalog) {
        this.catalog = catalog;
    }

    private enum WhichPhase {
        CURRENT,
        NEXT
    }

    @Override
    public TimedPhase getCurrentTimedPhaseOnCreate(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate) {
        return getTimedPhaseOnCreate(subscription, plan, priceList, effectiveDate, WhichPhase.CURRENT);
    }

    @Override
    public TimedPhase getNextTimedPhaseOnCreate(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate) {
            return getTimedPhaseOnCreate(subscription, plan, priceList, effectiveDate, WhichPhase.NEXT);
    }

    @Override
    public TimedPhase getCurrentTimedPhaseOnChange(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate) {
        return getTimedPhaseOnChange(subscription, plan, priceList, effectiveDate, WhichPhase.CURRENT);
    }

    @Override
    public TimedPhase getNextTimedPhaseOnChange(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate) {
        return getTimedPhaseOnChange(subscription, plan, priceList, effectiveDate, WhichPhase.NEXT);
    }



    @Override
    public TimedPhase getNextTimedPhase(Subscription subscription,
            IPlan plan, DateTime effectiveDate, DateTime planStartDate) {
        List<TimedPhase> timedPhases = getPhaseAlignments(subscription, plan, effectiveDate, planStartDate);
        return getTimedPhase(timedPhases, effectiveDate, WhichPhase.NEXT);
    }

    private TimedPhase getTimedPhaseOnCreate(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate, WhichPhase which) {

            PlanSpecifier planSpecifier = new PlanSpecifier(plan.getProduct().getName(),
                    plan.getProduct().getCategory(),
                    plan.getBillingPeriod(),
                    priceList);

            DateTime planStartDate = null;
            PlanAlignmentCreate alignement =  catalog.getPlanCreateAlignment(planSpecifier);
            switch(alignement) {
            case START_OF_SUBSCRIPTION:
                planStartDate = subscription.getStartDate();
                break;
            case START_OF_BUNDLE:
                planStartDate = subscription.getBundleStartDate();
                break;
            default:
                throw new EntitlementError(String.format("Unknwon PlanAlignmentCreate %s", alignement));
            }
            List<TimedPhase> timedPhases = getPhaseAlignments(subscription, plan, effectiveDate, planStartDate);
            return getTimedPhase(timedPhases, effectiveDate, which);
    }

    private TimedPhase getTimedPhaseOnChange(Subscription subscription,
            IPlan plan, String priceList, DateTime effectiveDate, WhichPhase which) {

        IPlanPhase currentPhase = subscription.getCurrentPhase();
        IPlan currentPlan = subscription.getCurrentPlan();
        String currentPriceList = subscription.getCurrentPriceList();

        PlanPhaseSpecifier fromPlanPhaseSpecifier = new PlanPhaseSpecifier(currentPlan.getProduct().getName(),
                currentPlan.getProduct().getCategory(),
                currentPlan.getBillingPeriod(),
                currentPriceList,
                currentPhase.getPhaseType());

        PlanSpecifier toPlanSpecifier = new PlanSpecifier(plan.getProduct().getName(),
                plan.getProduct().getCategory(),
                plan.getBillingPeriod(),
                priceList);

        DateTime planStartDate = null;
        PlanAlignmentChange alignment = catalog.getPlanChangeAlignment(fromPlanPhaseSpecifier, toPlanSpecifier);
        switch(alignment) {
        case START_OF_SUBSCRIPTION:
            planStartDate = subscription.getStartDate();
            break;
        case START_OF_BUNDLE:
            planStartDate = subscription.getBundleStartDate();
            break;
        case CHANGE_OF_PLAN:
            // STEPH
            throw new EntitlementError(String.format("Not implemented yet %s", alignment));
            //break;
        case CHANGE_OF_PRICELIST:
            // STEPH
            throw new EntitlementError(String.format("Not implemented yet %s", alignment));
            //break;
        default:
            throw new EntitlementError(String.format("Unknwon PlanAlignmentChange %s", alignment));
        }
        List<TimedPhase> timedPhases = getPhaseAlignments(subscription, plan, effectiveDate, planStartDate);
        return getTimedPhase(timedPhases, effectiveDate, which);
    }

    private List<TimedPhase> getPhaseAlignments(Subscription subscription, IPlan plan,
            DateTime effectiveDate, DateTime planStartDate) {

        List<TimedPhase> result = new LinkedList<IPlanAligner.TimedPhase>();

        DateTime curPhaseStart = planStartDate;
        if (plan.getInitialPhases() == null) {
            result.add(new TimedPhase(plan.getFinalPhase(), curPhaseStart));
            return result;
        }

        DateTime nextPhaseStart = null;
        for (IPlanPhase cur : plan.getInitialPhases()) {

            result.add(new TimedPhase(cur, curPhaseStart));

            IDuration curPhaseDuration = cur.getDuration();
            nextPhaseStart = Clock.addDuration(curPhaseStart, curPhaseDuration);
            if (nextPhaseStart == null) {
                throw new EntitlementError(String.format("Unexpected non ending UNLIMITED phase for plan %s",
                        plan.getName()));
            }
            curPhaseStart = nextPhaseStart;
        }
        result.add(new TimedPhase(plan.getFinalPhase(), nextPhaseStart));
        return result;
    }

    private TimedPhase getTimedPhase(List<TimedPhase> timedPhases, DateTime effectiveDate, WhichPhase which) {
        TimedPhase cur = null;
        TimedPhase next = null;
        for (TimedPhase phase : timedPhases) {
            if (phase.getStartPhase().isAfter(effectiveDate)) {
                next = phase;
                break;
            }
            cur = phase;
        }
        switch(which) {
        case CURRENT:
            return cur;
        case NEXT:
            return next;
        default:
            throw new EntitlementError(String.format("Unepected %s TimedPhase", which));
        }
    }
}
