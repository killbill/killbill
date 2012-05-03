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

package com.ning.billing.account.api;

import com.ning.billing.util.entity.UpdatableEntityBase;
import org.joda.time.DateTime;

import java.util.UUID;

public class DefaultAccountEmail extends UpdatableEntityBase implements AccountEmail {
    private final UUID accountId;
    private final String email;

    public DefaultAccountEmail(UUID accountId, String email) {
        super();
        this.accountId = accountId;
        this.email = email;
    }

    public DefaultAccountEmail(AccountEmail source, String newEmail) {
        this(source.getId(), source.getAccountId(), newEmail,
             source.getCreatedBy(), source.getCreatedDate(), source.getUpdatedBy(), source.getUpdatedDate());
    }

    public DefaultAccountEmail(UUID id, UUID accountId, String email, String createdBy, DateTime createdDate, String updatedBy, DateTime updatedDate) {
        super(id, createdBy, createdDate, updatedBy, updatedDate);
        this.accountId = accountId;
        this.email = email;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getEmail() {
        return email;
    }
}
