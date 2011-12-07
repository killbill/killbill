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

package com.ning.billing.account.api.user;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountCreationNotification;
import com.ning.billing.account.api.AccountData;

import java.util.UUID;

public class AccountCreationEventDefault implements AccountCreationNotification {
    private final UUID id;
    private final AccountData data;

    public AccountCreationEventDefault(Account data) {
        this.id = data.getId();
        this.data = data;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public AccountData getData() {
        return data;
    }
}
