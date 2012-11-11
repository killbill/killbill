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

package com.ning.billing.account.dao;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.entity.EntityBase;

public class AccountEmailModelDao extends EntityBase {

    private UUID accountId;
    private String email;

    public AccountEmailModelDao() { /* For the DAO mapper */ }

    public AccountEmailModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final UUID accountId, final String email) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.email = email;
    }

    public AccountEmailModelDao(final AccountEmail email) {
        this(email.getId(), email.getCreatedDate(), email.getUpdatedDate(), email.getAccountId(), email.getEmail());
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AccountEmailModelDao");
        sb.append("{accountId=").append(accountId);
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
        if (!super.equals(o)) {
            return false;
        }

        final AccountEmailModelDao that = (AccountEmailModelDao) o;

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
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        return result;
    }
}
