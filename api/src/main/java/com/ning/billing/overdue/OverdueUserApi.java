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

package com.ning.billing.overdue;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface OverdueUserApi {

    public <T extends Blockable> OverdueState<T> refreshOverdueStateFor(T overdueable, CallContext context) throws OverdueException, OverdueApiException;

    public <T extends Blockable> void setOverrideBillingStateForAccount(T overdueable, BillingState<T> state, CallContext context) throws OverdueException;

    public <T extends Blockable> OverdueState<T> getOverdueStateFor(T overdueable, TenantContext context) throws OverdueException;

    public <T extends Blockable> BillingState<T> getBillingStateFor(T overdueable, TenantContext context) throws OverdueException;

}
