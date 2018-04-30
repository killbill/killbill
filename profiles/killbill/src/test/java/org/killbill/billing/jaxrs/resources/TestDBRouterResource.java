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

package org.killbill.billing.jaxrs.resources;

import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.joda.time.DateTimeZone;
import org.killbill.billing.GuicyKillbillTestSuite;
import org.killbill.billing.beatrix.integration.db.TestDBRouterAPI;
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.UserType;

@Path("/testDbResource")
public class TestDBRouterResource implements JaxrsResource {

    private final MutableInternalCallContext internalCallContext = new MutableInternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID,
                                                                                                  1687L,
                                                                                                  DateTimeZone.UTC,
                                                                                                  GuicyKillbillTestSuite.getClock().getUTCNow(),
                                                                                                  UUID.randomUUID(),
                                                                                                  UUID.randomUUID().toString(),
                                                                                                  CallOrigin.TEST,
                                                                                                  UserType.TEST,
                                                                                                  "Testing",
                                                                                                  "This is a test",
                                                                                                  GuicyKillbillTestSuite.getClock().getUTCNow(),
                                                                                                  GuicyKillbillTestSuite.getClock().getUTCNow());

    private final MutableCallContext callContext = new MutableCallContext(internalCallContext);

    private final TestDBRouterAPI testDBRouterAPI;

    @Inject
    public TestDBRouterResource(final TestDBRouterAPI testDBRouterAPI) {
        this.testDBRouterAPI = testDBRouterAPI;
    }

    @POST
    public Response doChainedRWROCalls() {
        testDBRouterAPI.reset();
        testDBRouterAPI.doRWCall(callContext);
        testDBRouterAPI.doROCall(callContext);
        return Response.ok().build();
    }

    @GET
    public Response doChainedROROCalls() {
        testDBRouterAPI.reset();
        testDBRouterAPI.doROCall(callContext);
        testDBRouterAPI.doROCall(callContext);
        return Response.ok().build();
    }

    @GET
    @Path("/chained")
    public Response doChainedRORWROCalls() {
        testDBRouterAPI.reset();
        testDBRouterAPI.doROCall(callContext);
        // Naughty: @GET method doing a RW call... Verify the underlying code will detect it and mark the thread as dirty
        testDBRouterAPI.doRWCall(callContext);
        testDBRouterAPI.doROCall(callContext);
        return Response.ok().build();
    }
}
