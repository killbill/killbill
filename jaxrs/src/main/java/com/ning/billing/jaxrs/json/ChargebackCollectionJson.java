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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ChargebackCollectionJson {
    private final String accountId;
    private final List<ChargebackJson> chargebacks;

    @JsonCreator
    public ChargebackCollectionJson(@JsonProperty("accountId") final String accountId,
                                    @JsonProperty("chargebacks") final List<ChargebackJson> chargebacks) {
        this.accountId = accountId;
        this.chargebacks = chargebacks;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<ChargebackJson> getChargebacks() {
        return chargebacks;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ChargebackCollectionJson that = (ChargebackCollectionJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (chargebacks != null ? !chargebacks.equals(that.chargebacks) : that.chargebacks != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (chargebacks != null ? chargebacks.hashCode() : 0);
        return result;
    }
}
