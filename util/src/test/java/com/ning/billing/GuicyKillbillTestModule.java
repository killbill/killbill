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

package com.ning.billing;

import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.clock.Clock;
import com.ning.billing.clock.ClockMock;

import com.google.inject.AbstractModule;

public class GuicyKillbillTestModule extends AbstractModule {

    //
    // CreatedFontTracker references that will later be injected through Guices.
    // That we we have only one clock and all internalContext/callcontext are consistent
    //

    private final InternalCallContext internalCallContext = new InternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, 1687L, UUID.randomUUID(),
                                                                                    UUID.randomUUID().toString(), CallOrigin.TEST,
                                                                                    UserType.TEST, "Testing", "This is a test",
                                                                                    GuicyKillbillTestSuite.getClock().getUTCNow(), GuicyKillbillTestSuite.getClock().getUTCNow());

    private final CallContext callContext = internalCallContext.toCallContext(null);



    @Override
    protected void configure() {
        bind(ClockMock.class).toInstance(GuicyKillbillTestSuite.getClock());
        bind(Clock.class).to(ClockMock.class);
        bind(InternalCallContext.class).toInstance(internalCallContext);
        bind(CallContext.class).toInstance(callContext);
    }
}
