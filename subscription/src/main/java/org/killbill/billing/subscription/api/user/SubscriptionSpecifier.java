/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;

public class SubscriptionSpecifier {

    private SubscriptionBuilder builder;
    private Plan plan;
    private PhaseType initialPhase;
    private String realPriceList;
    private DateTime effectiveDate;
    private DateTime processedDate;

    public SubscriptionSpecifier() {
    }

    public SubscriptionSpecifier(final SubscriptionBuilder builder, final Plan plan,
                                 final PhaseType initialPhase, final String realPriceList,
                                 final DateTime effectiveDate,
                                 final DateTime processedDate) {
        this.builder = builder;
        this.plan = plan;
        this.initialPhase = initialPhase;
        this.realPriceList = realPriceList;
        this.effectiveDate = effectiveDate;
        this.processedDate = processedDate;
    }

    public SubscriptionBuilder getBuilder() {
        return builder;
    }

    public void setBuilder(final SubscriptionBuilder builder) {
        this.builder = builder;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(final Plan plan) {
        this.plan = plan;
    }

    public PhaseType getInitialPhase() {
        return initialPhase;
    }

    public void setInitialPhase(final PhaseType initialPhase) {
        this.initialPhase = initialPhase;
    }

    public String getRealPriceList() {
        return realPriceList;
    }

    public void setRealPriceList(final String realPriceList) {
        this.realPriceList = realPriceList;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(final DateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public DateTime getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(final DateTime processedDate) {
        this.processedDate = processedDate;
    }

}
