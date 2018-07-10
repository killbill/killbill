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

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.AccountEmail;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;


@ApiModel(value="AccountEmail", parent = JsonBase.class)
public class AccountEmailJson extends JsonBase {

    private final UUID accountId;
    @ApiModelProperty(required = true)
    private final String email;

    @JsonCreator
    public AccountEmailJson(@JsonProperty("accountId") final UUID accountId, @JsonProperty("email") final String email) {
        this.accountId = accountId;
        this.email = email;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public AccountEmail toAccountEmail(final UUID accountEmailId) {

        return new AccountEmail() {
            @Override
            public UUID getAccountId() {
                return accountId;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public UUID getId() {
                return accountEmailId;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }
        };
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AccountEmailJson");
        sb.append("{accountId='").append(accountId).append('\'');
        sb.append(", email='").append(email).append('\'');
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

        final AccountEmailJson that = (AccountEmailJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (email != null ? email.hashCode() : 0);
        return result;
    }
}
