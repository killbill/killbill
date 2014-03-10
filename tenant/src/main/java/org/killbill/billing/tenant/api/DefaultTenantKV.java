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
package org.killbill.billing.tenant.api;


import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.entity.EntityBase;

public class DefaultTenantKV  extends EntityBase implements TenantKV {

    private final String key;
    private final String value;

    public DefaultTenantKV(final UUID id, final String key, final String value, final DateTime createdDate, final DateTime updatedDate) {
        super(id, createdDate, updatedDate);
        this.key = key;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }
}
