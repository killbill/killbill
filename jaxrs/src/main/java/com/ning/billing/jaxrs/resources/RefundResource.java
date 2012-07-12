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

package com.ning.billing.jaxrs.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.jaxrs.json.RefundJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.jaxrs.util.TagHelper;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;


@Path(JaxrsResource.REFUNDS_PATH)
public class RefundResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(RefundResource.class);

    private final PaymentApi paymentApi;

    @Inject
    public RefundResource(final JaxrsUriBuilder uriBuilder,
            final PaymentApi paymentApi,
            final TagUserApi tagUserApi,
            final TagHelper tagHelper,
            final CustomFieldUserApi customFieldUserApi,
            final Context context) {
        super(uriBuilder, tagUserApi, tagHelper, customFieldUserApi);
        this.paymentApi = paymentApi;
    }

    @GET
    @Path("/{refundId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getRefund(@PathParam("refundId") final String refundId) {
        try {
            Refund refund = paymentApi.getRefund(UUID.fromString(refundId));
            return Response.status(Status.OK).entity(new RefundJson(refund)).build();
        } catch (PaymentApiException e) {
            if (e.getCode() == ErrorCode.PAYMENT_NO_SUCH_REFUND.getCode()) {
                return Response.status(Status.NO_CONTENT).build();
            } else {
                return Response.status(Status.BAD_REQUEST).build();
            }
        }
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.REFUND;
    }
}
