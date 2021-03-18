/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.util.config.definition;

import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.killbill.billing.util.glue.KillBillModule;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class MultiTenantEventConfig extends MultiTenantConfigBase implements EventConfig {

    private final EventConfig staticConfig;

    @Inject
    public MultiTenantEventConfig(@Named(KillBillModule.STATIC_CONFIG) final EventConfig staticConfig, final CacheConfig cacheConfig) {
        super(cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public List<BusInternalEventType> getSkipPostBusEventTypeList() {
        return staticConfig.getSkipPostBusEventTypeList();
    }

    @Override
    public List<BusInternalEventType> getSkipPostBusEventTypeList(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getSkipPostBusEventTypeList", tenantContext);
        if (result != null) {
            return convertToListBusInternalEventType(result, "getSkipPostBusEventTypeList");
        }
        return getSkipPostBusEventTypeList();
    }

    @Override
    public List<BusInternalEventType> getSkipDispatchBusEventTypeList() {
        return staticConfig.getSkipDispatchBusEventTypeList();
    }

    @Override
    public List<BusInternalEventType> getSkipDispatchBusEventTypeList(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getSkipDispatchBusEventTypeList", tenantContext);
        if (result != null) {
            return convertToListBusInternalEventType(result, "getSkipDispatchBusEventTypeList");
        }
        return getSkipDispatchBusEventTypeList();
    }

    @Override
    public boolean isAggregateBulkSubscriptionEvents() {
        return staticConfig.isAggregateBulkSubscriptionEvents();
    }

    @Override
    public boolean isAggregateBulkSubscriptionEvents(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("isAggregateBulkSubscriptionEvents", tenantContext);
        if (result != null) {
            return Boolean.parseBoolean(result);
        }
        return isAggregateBulkSubscriptionEvents();
    }

    @Override
    protected Class<? extends KillbillConfig> getConfigClass() {
        return EventConfig.class;
    }
}
