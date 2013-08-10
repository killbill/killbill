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

public class CreditCollectionJson {

    private final String accountId;
    private final List<CreditJson> credits;

    @JsonCreator
    public CreditCollectionJson(@JsonProperty("accountId") final String accountId,
                                @JsonProperty("credits") final List<CreditJson> credits) {
        this.accountId = accountId;
        this.credits = credits;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<CreditJson> getCredits() {
        return credits;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CreditCollectionJson that = (CreditCollectionJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (credits != null ? !credits.equals(that.credits) : that.credits != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (credits != null ? credits.hashCode() : 0);
        return result;
    }
}
