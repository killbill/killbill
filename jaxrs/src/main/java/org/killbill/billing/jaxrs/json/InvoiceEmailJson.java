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

package org.killbill.billing.jaxrs.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

public class InvoiceEmailJson extends JsonBase {

    @ApiModelProperty(dataType = "java.util.UUID")
    private final String accountId;
    private final boolean isNotifiedForInvoices;

    @JsonCreator
    public InvoiceEmailJson(@JsonProperty("accountId") final String accountId,
                            @JsonProperty("isNotifiedForInvoices") final boolean isNotifiedForInvoices) {
        this.accountId = accountId;
        this.isNotifiedForInvoices = isNotifiedForInvoices;
    }

    public String getAccountId() {
        return accountId;
    }

    @JsonGetter("isNotifiedForInvoices")
    public boolean isNotifiedForInvoices() {
        return isNotifiedForInvoices;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InvoiceEmailJson");
        sb.append("{accountId='").append(accountId).append('\'');
        sb.append(", isNotifiedForInvoices=").append(isNotifiedForInvoices);
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

        final InvoiceEmailJson that = (InvoiceEmailJson) o;

        if (isNotifiedForInvoices != that.isNotifiedForInvoices) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (isNotifiedForInvoices ? 1 : 0);
        return result;
    }
}
