/*
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

package org.killbill.billing.payment.glue;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.mock.glue.MockEntitlementModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;

public class MockEntitlementModuleForPayment extends MockEntitlementModule {

    public MockEntitlementModuleForPayment(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    public void installBlockingApi() {
        super.installBlockingApi();

        final BlockingState blockingState = new DefaultBlockingState(null, BlockingStateType.ACCOUNT, "CLEAR_STATE_NAME", "test", false, false, false, new DateTime(DateTimeZone.UTC));
        Mockito.when(blockingApi.getBlockingAllForAccount(Mockito.<InternalTenantContext>any())).thenReturn(ImmutableList.<BlockingState>of(blockingState));
        Mockito.when(blockingApi.getBlockingStateForService(Mockito.<UUID>any(), Mockito.<BlockingStateType>any(), Mockito.anyString(), Mockito.<InternalTenantContext>any())).thenReturn(blockingState);
    }
}
