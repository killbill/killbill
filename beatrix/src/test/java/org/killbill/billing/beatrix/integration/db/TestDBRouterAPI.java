/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.db;

import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.killbill.billing.KillbillApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.dao.DBRouterUntyped;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;

public class TestDBRouterAPI implements KillbillApi {

    private final AtomicInteger rwCalls = new AtomicInteger(0);
    private final AtomicInteger roCalls = new AtomicInteger(0);

    private final DBRouterUntyped dbRouter;

    @Inject
    public TestDBRouterAPI() {
        final IDBI dbi = Mockito.mock(IDBI.class);
        Mockito.when(dbi.open()).thenAnswer(new Answer<Handle>() {
            @Override
            public Handle answer(final InvocationOnMock invocation) {
                rwCalls.incrementAndGet();
                return null;
            }
        });
        final IDBI roDbi = Mockito.mock(IDBI.class);
        Mockito.when(roDbi.open()).thenAnswer(new Answer<Handle>() {
            @Override
            public Handle answer(final InvocationOnMock invocation) {
                roCalls.incrementAndGet();
                return null;
            }
        });

        this.dbRouter = new DBRouterUntyped(dbi, roDbi);
    }

    public void reset() {
        rwCalls.set(0);
        roCalls.set(0);
    }

    public void doRWCall(final CallContext callContext) {
        dbRouter.getHandle(false);
    }

    public void doROCall(final TenantContext tenantContext) {
        dbRouter.getHandle(true);
    }

    // Nesting dolls
    public void doChainedROCall(final TenantContext tenantContext) {
        doROCall(tenantContext);
    }

    // Nesting dolls
    public void doChainedRWCall(final CallContext callContext) {
        doRWCall(callContext);
    }

    public int getNbRWCalls() {
        return rwCalls.get();
    }

    public int getNbRoCalls() {
        return roCalls.get();
    }
}
