/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.payment.caching;

import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.PaymentApiException;

public interface StateMachineConfigCache {

    public void loadDefaultPaymentStateMachineConfig(String url) throws PaymentApiException;

    public StateMachineConfig getPaymentStateMachineConfig(String pluginName, InternalTenantContext tenantContext) throws PaymentApiException;

    public void clearPaymentStateMachineConfig(String pluginName, InternalTenantContext tenantContext);
}
