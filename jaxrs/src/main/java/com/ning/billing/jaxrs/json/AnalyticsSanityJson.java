/*
 * Copyright 2010-2012 Ning, Inc.
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
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class AnalyticsSanityJson extends JsonBase {

    private final List<String> checkEntitlement;
    private final List<String> checkInvoice;
    private final List<String> checkPayment;
    private final List<String> checkTag;
    private final List<String> checkConsistency;

    @JsonCreator
    public AnalyticsSanityJson(@JsonProperty("checkEntitlement") final List<String> checkEntitlement,
                               @JsonProperty("checkInvoice") final List<String> checkInvoice,
                               @JsonProperty("checkPayment") final List<String> checkPayment,
                               @JsonProperty("checkTag") final List<String> checkTag,
                               @JsonProperty("checkConsistency") final List<String> checkConsistency) {
        this.checkEntitlement = checkEntitlement;
        this.checkInvoice = checkInvoice;
        this.checkPayment = checkPayment;
        this.checkTag = checkTag;
        this.checkConsistency = checkConsistency;
    }

    public AnalyticsSanityJson(final Collection<UUID> checkEntitlement,
                               final Collection<UUID> checkInvoice,
                               final Collection<UUID> checkPayment,
                               final Collection<UUID> checkTag,
                               final Collection<UUID> checkConsistency) {
        this.checkEntitlement = ImmutableList.<String>copyOf(Collections2.transform(checkEntitlement, new Function<UUID, String>() {
            @Override
            public String apply(final UUID input) {
                return input.toString();
            }
        }));
        this.checkInvoice = ImmutableList.<String>copyOf(Collections2.transform(checkInvoice, new Function<UUID, String>() {
            @Override
            public String apply(final UUID input) {
                return input.toString();
            }
        }));
        this.checkPayment = ImmutableList.<String>copyOf(Collections2.transform(checkPayment, new Function<UUID, String>() {
            @Override
            public String apply(final UUID input) {
                return input.toString();
            }
        }));
        this.checkTag = ImmutableList.<String>copyOf(Collections2.transform(checkTag, new Function<UUID, String>() {
            @Override
            public String apply(final UUID input) {
                return input.toString();
            }
        }));
        this.checkConsistency = ImmutableList.<String>copyOf(Collections2.transform(checkConsistency, new Function<UUID, String>() {
            @Override
            public String apply(final UUID input) {
                return input.toString();
            }
        }));
    }

    public List<String> getCheckEntitlement() {
        return checkEntitlement;
    }

    public List<String> getCheckInvoice() {
        return checkInvoice;
    }

    public List<String> getCheckPayment() {
        return checkPayment;
    }

    public List<String> getCheckTag() {
        return checkTag;
    }

    public List<String> getCheckConsistency() {
        return checkConsistency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AnalyticsSanityJson");
        sb.append("{checkEntitlement=").append(checkEntitlement);
        sb.append(", checkInvoice=").append(checkInvoice);
        sb.append(", checkPayment=").append(checkPayment);
        sb.append(", checkTag=").append(checkTag);
        sb.append(", checkConsistency=").append(checkConsistency);
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

        final AnalyticsSanityJson json = (AnalyticsSanityJson) o;

        if (checkConsistency != null ? !checkConsistency.equals(json.checkConsistency) : json.checkConsistency != null) {
            return false;
        }
        if (checkEntitlement != null ? !checkEntitlement.equals(json.checkEntitlement) : json.checkEntitlement != null) {
            return false;
        }
        if (checkInvoice != null ? !checkInvoice.equals(json.checkInvoice) : json.checkInvoice != null) {
            return false;
        }
        if (checkPayment != null ? !checkPayment.equals(json.checkPayment) : json.checkPayment != null) {
            return false;
        }
        if (checkTag != null ? !checkTag.equals(json.checkTag) : json.checkTag != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = checkEntitlement != null ? checkEntitlement.hashCode() : 0;
        result = 31 * result + (checkInvoice != null ? checkInvoice.hashCode() : 0);
        result = 31 * result + (checkPayment != null ? checkPayment.hashCode() : 0);
        result = 31 * result + (checkTag != null ? checkTag.hashCode() : 0);
        result = 31 * result + (checkConsistency != null ? checkConsistency.hashCode() : 0);
        return result;
    }
}
