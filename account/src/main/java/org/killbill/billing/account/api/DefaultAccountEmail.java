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

package org.killbill.billing.account.api;

import java.util.UUID;

import org.killbill.billing.account.dao.AccountEmailModelDao;
import org.killbill.billing.entity.EntityBase;

public class DefaultAccountEmail extends EntityBase implements AccountEmail {

    private final UUID accountId;
    private final String email;

    public DefaultAccountEmail(final UUID accountId, final String email) {
        super();
        this.accountId = accountId;
        this.email = email;
    }

    public DefaultAccountEmail(final AccountEmailModelDao accountEmail) {
        super(accountEmail.getId(), accountEmail.getCreatedDate(), accountEmail.getUpdatedDate());
        this.accountId = accountEmail.getAccountId();
        this.email = accountEmail.getEmail();
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultAccountEmail");
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

        final DefaultAccountEmail that = (DefaultAccountEmail) o;

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
