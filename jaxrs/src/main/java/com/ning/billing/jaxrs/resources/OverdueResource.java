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

package com.ning.billing.jaxrs.resources;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.jaxrs.json.OverdueStateJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.api.OverdueException;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.OVERDUE_PATH)
public class OverdueResource extends JaxRsResourceBase {

    private final OverdueUserApi overdueApi;

    @Inject
    public OverdueResource(final OverdueUserApi overdueApi,
                           final AccountUserApi accountApi,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, context);
        this.overdueApi = overdueApi;
    }


    @GET
    @Path("/" + ACCOUNTS + "/{accountId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getOverdueAccount(@PathParam("accountId") final String accountId,
                                      @javax.ws.rs.core.Context final HttpServletRequest request) throws AccountApiException, OverdueException, OverdueApiException {
        final TenantContext tenantContext = context.createContext(request);

        final Account account = accountUserApi.getAccountById(UUID.fromString(accountId), tenantContext);
        final OverdueState overdueState = overdueApi.getOverdueStateFor(account, tenantContext);

        return Response.status(Status.OK).entity(new OverdueStateJson(overdueState)).build();
    }
}
