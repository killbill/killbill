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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.analytics.api.BusinessField;
import com.ning.billing.analytics.api.BusinessInvoice;
import com.ning.billing.analytics.api.BusinessInvoicePayment;
import com.ning.billing.analytics.api.BusinessOverdueStatus;
import com.ning.billing.analytics.api.BusinessSnapshot;
import com.ning.billing.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.analytics.api.BusinessTag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class BusinessSnapshotJson extends JsonBase {

    private final BusinessAccountJson businessAccount;
    private final Collection<BusinessSubscriptionTransitionJson> businessSubscriptionTransitions;
    private final Collection<BusinessInvoiceJson> businessInvoices;
    private final Collection<BusinessInvoicePaymentJson> businessInvoicePayments;
    private final Collection<BusinessOverdueStatusJson> businessOverdueStatuses;
    private final Collection<BusinessTagJson> businessTags;
    private final Collection<BusinessFieldJson> businessFields;

    @JsonCreator
    public BusinessSnapshotJson(@JsonProperty("businessAccount") final BusinessAccountJson businessAccount,
                                @JsonProperty("businessSubscriptionTransitions") final Collection<BusinessSubscriptionTransitionJson> businessSubscriptionTransitions,
                                @JsonProperty("businessInvoices") final Collection<BusinessInvoiceJson> businessInvoices,
                                @JsonProperty("businessInvoicePayments") final Collection<BusinessInvoicePaymentJson> businessInvoicePayments,
                                @JsonProperty("businessOverdueStatuses") final Collection<BusinessOverdueStatusJson> businessOverdueStatuses,
                                @JsonProperty("businessTags") final Collection<BusinessTagJson> businessTags,
                                @JsonProperty("businessFields") final Collection<BusinessFieldJson> businessFields) {
        this.businessAccount = businessAccount;
        this.businessSubscriptionTransitions = businessSubscriptionTransitions;
        this.businessInvoices = businessInvoices;
        this.businessInvoicePayments = businessInvoicePayments;
        this.businessOverdueStatuses = businessOverdueStatuses;
        this.businessTags = businessTags;
        this.businessFields = businessFields;
    }

    public BusinessSnapshotJson(final BusinessSnapshot businessSnapshot) {
        final Map<UUID, Integer> invoiceIdToNumber = new HashMap<UUID, Integer>();

        this.businessAccount = new BusinessAccountJson(businessSnapshot.getBusinessAccount());
        this.businessSubscriptionTransitions = ImmutableList.<BusinessSubscriptionTransitionJson>copyOf(Collections2.transform(businessSnapshot.getBusinessSubscriptionTransitions(), new Function<BusinessSubscriptionTransition, BusinessSubscriptionTransitionJson>() {
            @Override
            public BusinessSubscriptionTransitionJson apply(@Nullable final BusinessSubscriptionTransition input) {
                return new BusinessSubscriptionTransitionJson(input);
            }
        }));
        this.businessInvoices = ImmutableList.<BusinessInvoiceJson>copyOf(Collections2.transform(businessSnapshot.getBusinessInvoices(), new Function<BusinessInvoice, BusinessInvoiceJson>() {
            @Override
            public BusinessInvoiceJson apply(final BusinessInvoice input) {
                invoiceIdToNumber.put(input.getInvoiceId(), input.getInvoiceNumber());
                return new BusinessInvoiceJson(input);
            }
        }));
        this.businessInvoicePayments = ImmutableList.<BusinessInvoicePaymentJson>copyOf(Collections2.transform(businessSnapshot.getBusinessInvoicePayments(), new Function<BusinessInvoicePayment, BusinessInvoicePaymentJson>() {
            @Override
            public BusinessInvoicePaymentJson apply(final BusinessInvoicePayment input) {
                return new BusinessInvoicePaymentJson(input, invoiceIdToNumber.get(input.getInvoiceId()));
            }
        }));
        this.businessOverdueStatuses = ImmutableList.<BusinessOverdueStatusJson>copyOf(Collections2.transform(businessSnapshot.getBusinessOverdueStatuses(), new Function<BusinessOverdueStatus, BusinessOverdueStatusJson>() {
            @Override
            public BusinessOverdueStatusJson apply(@Nullable final BusinessOverdueStatus input) {
                return new BusinessOverdueStatusJson(input);
            }
        }));
        this.businessTags = ImmutableList.<BusinessTagJson>copyOf(Collections2.transform(businessSnapshot.getBusinessTags(), new Function<BusinessTag, BusinessTagJson>() {
            @Override
            public BusinessTagJson apply(@Nullable final BusinessTag input) {
                return new BusinessTagJson(input);
            }
        }));
        this.businessFields = ImmutableList.<BusinessFieldJson>copyOf(Collections2.transform(businessSnapshot.getBusinessFields(), new Function<BusinessField, BusinessFieldJson>() {
            @Override
            public BusinessFieldJson apply(@Nullable final BusinessField input) {
                return new BusinessFieldJson(input);
            }
        }));
    }

    public BusinessAccountJson getBusinessAccount() {
        return businessAccount;
    }

    public Collection<BusinessSubscriptionTransitionJson> getBusinessSubscriptionTransitions() {
        return businessSubscriptionTransitions;
    }

    public Collection<BusinessInvoiceJson> getBusinessInvoices() {
        return businessInvoices;
    }

    public Collection<BusinessInvoicePaymentJson> getBusinessInvoicePayments() {
        return businessInvoicePayments;
    }

    public Collection<BusinessOverdueStatusJson> getBusinessOverdueStatuses() {
        return businessOverdueStatuses;
    }

    public Collection<BusinessTagJson> getBusinessTags() {
        return businessTags;
    }

    public Collection<BusinessFieldJson> getBusinessFields() {
        return businessFields;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessSnapshotJson");
        sb.append("{businessAccount=").append(businessAccount);
        sb.append(", businessSubscriptionTransitions=").append(businessSubscriptionTransitions);
        sb.append(", businessInvoices=").append(businessInvoices);
        sb.append(", businessInvoicePayments=").append(businessInvoicePayments);
        sb.append(", businessOverdueStatuses=").append(businessOverdueStatuses);
        sb.append(", businessTags=").append(businessTags);
        sb.append(", businessFields=").append(businessFields);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessSnapshotJson that = (BusinessSnapshotJson) o;

        if (businessAccount != null ? !businessAccount.equals(that.businessAccount) : that.businessAccount != null) {
            return false;
        }
        if (businessFields != null ? !businessFields.equals(that.businessFields) : that.businessFields != null) {
            return false;
        }
        if (businessInvoicePayments != null ? !businessInvoicePayments.equals(that.businessInvoicePayments) : that.businessInvoicePayments != null) {
            return false;
        }
        if (businessInvoices != null ? !businessInvoices.equals(that.businessInvoices) : that.businessInvoices != null) {
            return false;
        }
        if (businessOverdueStatuses != null ? !businessOverdueStatuses.equals(that.businessOverdueStatuses) : that.businessOverdueStatuses != null) {
            return false;
        }
        if (businessSubscriptionTransitions != null ? !businessSubscriptionTransitions.equals(that.businessSubscriptionTransitions) : that.businessSubscriptionTransitions != null) {
            return false;
        }
        if (businessTags != null ? !businessTags.equals(that.businessTags) : that.businessTags != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = businessAccount != null ? businessAccount.hashCode() : 0;
        result = 31 * result + (businessSubscriptionTransitions != null ? businessSubscriptionTransitions.hashCode() : 0);
        result = 31 * result + (businessInvoices != null ? businessInvoices.hashCode() : 0);
        result = 31 * result + (businessInvoicePayments != null ? businessInvoicePayments.hashCode() : 0);
        result = 31 * result + (businessOverdueStatuses != null ? businessOverdueStatuses.hashCode() : 0);
        result = 31 * result + (businessTags != null ? businessTags.hashCode() : 0);
        result = 31 * result + (businessFields != null ? businessFields.hashCode() : 0);
        return result;
    }
}
