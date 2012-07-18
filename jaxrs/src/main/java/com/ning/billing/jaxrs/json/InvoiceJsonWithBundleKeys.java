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

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class InvoiceJsonWithBundleKeys extends InvoiceJsonSimple {

    private final String bundleKeys;
    private final List<CreditJson> credits;

    public InvoiceJsonWithBundleKeys() {
        super();
        this.bundleKeys = null;
        this.credits = ImmutableList.<CreditJson>of();
    }

    @JsonCreator
    public InvoiceJsonWithBundleKeys(@JsonProperty("amount") final BigDecimal amount,
                                     @JsonProperty("cba") final BigDecimal cba,
                                     @JsonProperty("creditAdj") final BigDecimal creditAdj,
                                     @JsonProperty("refundAdj") final BigDecimal refundAdj,
                                     @JsonProperty("invoiceId") final String invoiceId,
                                     @JsonProperty("invoiceDate") final LocalDate invoiceDate,
                                     @JsonProperty("targetDate") final LocalDate targetDate,
                                     @JsonProperty("invoiceNumber") final String invoiceNumber,
                                     @JsonProperty("balance") final BigDecimal balance,
                                     @JsonProperty("accountId") final String accountId,
                                     @JsonProperty("externalBundleKeys") final String bundleKeys,
                                     @JsonProperty("credits") final List<CreditJson> credits) {
        super(amount, cba, creditAdj, refundAdj, invoiceId, invoiceDate, targetDate, invoiceNumber, balance, accountId);
        this.bundleKeys = bundleKeys;
        this.credits = credits;
    }

    public InvoiceJsonWithBundleKeys(final Invoice input, final String bundleKeys, final List<CreditJson> credits) {
        super(input);
        this.bundleKeys = bundleKeys;
        this.credits = credits;
    }

    public String getBundleKeys() {
        return bundleKeys;
    }

    public List<CreditJson> getCredits() {
        return credits;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceJsonWithBundleKeys");
        sb.append("{bundleKeys='").append(bundleKeys).append('\'');
        sb.append(", credits=").append(credits);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        final InvoiceJsonWithBundleKeys that = (InvoiceJsonWithBundleKeys) o;

        if (bundleKeys != null ? !bundleKeys.equals(that.bundleKeys) : that.bundleKeys != null) return false;
        if (credits != null ? !credits.equals(that.credits) : that.credits != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (bundleKeys != null ? bundleKeys.hashCode() : 0);
        result = 31 * result + (credits != null ? credits.hashCode() : 0);
        return result;
    }
}
