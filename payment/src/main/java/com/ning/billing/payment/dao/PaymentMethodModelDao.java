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
package com.ning.billing.payment.dao;

import java.util.UUID;

import com.ning.billing.util.entity.EntityBase;

public class PaymentMethodModelDao extends EntityBase {
    
    private final UUID accountId;
    private final String pluginName;
    private final Boolean isActive;
    
    public PaymentMethodModelDao(UUID id, UUID accountId, String pluginName,
            Boolean isActive) {
        super(id);
        this.accountId = accountId;
        this.pluginName = pluginName;
        this.isActive = isActive;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public Boolean isActive() {
        return isActive;
    }
}
