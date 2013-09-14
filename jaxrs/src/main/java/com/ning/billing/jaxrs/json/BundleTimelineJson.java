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

package com.ning.billing.jaxrs.json;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BundleTimelineJson {

    private final String viewId;

    private final BundleJson bundle;

    private final List<PaymentJson> payments;

    private final List<InvoiceJson> invoices;


    private final String reasonForChange;

    @JsonCreator
    public BundleTimelineJson(@JsonProperty("viewId") final String viewId,
                              @JsonProperty("bundle") final BundleJson bundle,
                              @JsonProperty("payments") final List<PaymentJson> payments,
                              @JsonProperty("invoices") final List<InvoiceJson> invoices,
                              @JsonProperty("reasonForChange") final String reason) {
        this.viewId = viewId;
        this.bundle = bundle;
        this.payments = payments;
        this.invoices = invoices;
        this.reasonForChange = reason;
    }

    public String getViewId() {
        return viewId;
    }

    public BundleJson getBundle() {
        return bundle;
    }

    public List<PaymentJson> getPayments() {
        return payments;
    }

    public List<InvoiceJson> getInvoices() {
        return invoices;
    }

    public String getReasonForChange() {
        return reasonForChange;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BundleTimelineJson that = (BundleTimelineJson) o;

        if (bundle != null ? !bundle.equals(that.bundle) : that.bundle != null) {
            return false;
        }
        if (invoices != null ? !invoices.equals(that.invoices) : that.invoices != null) {
            return false;
        }
        if (payments != null ? !payments.equals(that.payments) : that.payments != null) {
            return false;
        }
        if (reasonForChange != null ? !reasonForChange.equals(that.reasonForChange) : that.reasonForChange != null) {
            return false;
        }
        if (viewId != null ? !viewId.equals(that.viewId) : that.viewId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = viewId != null ? viewId.hashCode() : 0;
        result = 31 * result + (bundle != null ? bundle.hashCode() : 0);
        result = 31 * result + (payments != null ? payments.hashCode() : 0);
        result = 31 * result + (invoices != null ? invoices.hashCode() : 0);
        result = 31 * result + (reasonForChange != null ? reasonForChange.hashCode() : 0);
        return result;
    }
}
