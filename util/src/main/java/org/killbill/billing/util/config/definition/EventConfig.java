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
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;
import org.skife.config.Param;

public interface EventConfig extends KillbillConfig {

    @Config("org.killbill.billing.server.event.post.type.skip")
    @Default("")
    @Description("List of event types to be skipped (not posted)")
    List<BusInternalEventType> getSkipPostBusEventTypeList();

    // Not fully implemented
    @Config("org.killbill.billing.server.event.post.type.skip")
    @Default("")
    @Description("List of event types to be skipped (not posted)")
    List<BusInternalEventType> getSkipPostBusEventTypeList(@Param("dummy") final InternalTenantContext tenantContext);

    @Config("org.killbill.billing.server.event.dispatch.type.skip")
    @Default("")
    @Description("List of event types to be skipped (not dispatched internally)")
    List<BusInternalEventType> getSkipDispatchBusEventTypeList();

    @Config("org.killbill.billing.server.event.dispatch.type.skip")
    @Default("")
    @Description("List of event types to be skipped (not dispatched internally)")
    List<BusInternalEventType> getSkipDispatchBusEventTypeList(@Param("dummy") final InternalTenantContext tenantContext);


    @Config("org.killbill.billing.server.event.bulk.subscription.aggregate")
    @Default("false")
    @Description("List of event types to be skipped (not dispatched internally)")
    boolean isAggregateBulkSubscriptionEvents();

    @Config("org.killbill.billing.server.event.bulk.subscription.aggregate")
    @Default("false")
    @Description("List of event types to be skipped (not dispatched internally)")
    boolean isAggregateBulkSubscriptionEvents(@Param("dummy") final InternalTenantContext tenantContext);

}