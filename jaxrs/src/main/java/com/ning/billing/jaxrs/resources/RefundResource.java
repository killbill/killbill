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

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.google.inject.Inject;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(JaxrsResource.REFUNDS_PATH)
public class RefundResource extends JaxRsResourceBase {

    private final PaymentApi paymentApi;

    @Inject
    public RefundResource(final PaymentApi paymentApi,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, context);
        this.paymentApi = paymentApi;
    }

    @GET
    @Path("/{refundId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getRefund(@PathParam("refundId") final String refundId,
                              @javax.ws.rs.core.Context final HttpServletRequest request) throws PaymentApiException {
        final Refund refund = paymentApi.getRefund(UUID.fromString(refundId), false, context.createContext(request));
        // TODO Return adjusted items and audits
        return Response.status(Status.OK).entity(new RefundJson(refund, null, null)).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.REFUND;
    }
}
