/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.tenant.api.user;

import java.util.UUID;

import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.TenantConfigChangeInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DefaultTenantConfigChangeInternalEvent extends BusEventBase implements TenantConfigChangeInternalEvent {

    private final UUID id;
    private final String key;

    @JsonCreator
    public DefaultTenantConfigChangeInternalEvent(@JsonProperty("id") final UUID id,
                                                  @JsonProperty("key") final String key,
                                                  @JsonProperty("searchKey1") final Long searchKey1,
                                                  @JsonProperty("searchKey2") final Long searchKey2,
                                                  @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.id = id;
        this.key = key;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getKey() {
        return key;
    }

    @JsonIgnore
    @Override
    public BusInternalEventType getBusEventType() {
        return BusInternalEventType.TENANT_CONFIG_CHANGE;
    }
}
