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

package com.ning.billing.jaxrs.json;

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

public class BundleTimelineJson {

    @JsonView(BundleTimelineViews.Timeline.class)
    private final String viewId;

    @JsonView(BundleTimelineViews.Timeline.class)
    private final BundleJsonWithSubscriptions bundle;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<PaymentJsonSimple> payments;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<InvoiceJsonSimple> invoices;

    @JsonView(BundleTimelineViews.WriteTimeline.class)
    private final String resonForChange;

    @JsonCreator
    public BundleTimelineJson(@JsonProperty("viewId") String viewId,
            @JsonProperty("bundle") BundleJsonWithSubscriptions bundle,
            @JsonProperty("payments") List<PaymentJsonSimple> payments,
            @JsonProperty("invoices") List<InvoiceJsonSimple> invoices,
            @JsonProperty("reasonForChange") String reason) {
        this.viewId = viewId;
        this.bundle = bundle;
        this.payments = payments;
        this.invoices = invoices;
        this.resonForChange = reason;
    }

    public String getViewId() {
        return viewId;
    }

    public BundleJsonWithSubscriptions getBundle() {
        return bundle;
    }

    public List<PaymentJsonSimple> getPayments() {
        return payments;
    }

    public List<InvoiceJsonSimple> getInvoices() {
        return invoices;
    }

    public String getResonForChange() {
        return resonForChange;
    }
}
