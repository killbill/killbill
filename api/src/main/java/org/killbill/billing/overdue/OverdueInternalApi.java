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

package org.killbill.billing.overdue;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;

public interface OverdueInternalApi {

    public OverdueState refreshOverdueStateFor(Account overdueable, CallContext context) throws OverdueException, OverdueApiException;

    public void setOverrideBillingStateForAccount(Account overdueable, BillingState state, CallContext context) throws OverdueException;

    public OverdueState getOverdueStateFor(Account overdueable, TenantContext context) throws OverdueException;

    public BillingState getBillingStateFor(Account overdueable, TenantContext context) throws OverdueException;

}
