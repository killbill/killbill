/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.payment.api.PluginProperty;

public interface EntitlementInternalApi {

    AccountEntitlements getAllEntitlementsForAccount(InternalTenantContext context) throws EntitlementApiException;

    Entitlement getEntitlementForId(final UUID uuid, final InternalTenantContext tenantContext) throws EntitlementApiException;

    void pause(UUID bundleId, LocalDate effectiveDate, Iterable<PluginProperty> properties, InternalCallContext context) throws EntitlementApiException;

    void resume(UUID bundleId, LocalDate localEffectiveDate, Iterable<PluginProperty> properties, InternalCallContext context) throws EntitlementApiException;

    void cancel(Iterable<Entitlement> entitlements, LocalDate effectiveDate, BillingActionPolicy billingPolicy, Iterable<PluginProperty> properties, InternalCallContext context) throws EntitlementApiException;
}
