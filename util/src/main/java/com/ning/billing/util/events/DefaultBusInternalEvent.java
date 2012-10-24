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
package com.ning.billing.util.events;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class DefaultBusInternalEvent implements BusInternalEvent {

    private final UUID userToken;
    private final Long tenantRecordId;
    private final Long accountRecordId;

    public DefaultBusInternalEvent(final UUID userToken, final Long accountRecordId, final Long tenantRecordId) {
        this.userToken = userToken;
        this.tenantRecordId = tenantRecordId;
        this.accountRecordId = accountRecordId;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @JsonIgnore
    @Override
    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    @JsonIgnore
    @Override
    public Long getAccountRecordId() {
        return accountRecordId;
    }
}
