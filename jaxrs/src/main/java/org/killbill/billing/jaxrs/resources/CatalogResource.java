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

package org.killbill.billing.jaxrs.resources;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.VersionedCatalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.SimplePlanDescriptor;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.api.user.DefaultSimplePlanDescriptor;
import org.killbill.billing.jaxrs.json.CatalogJson;
import org.killbill.billing.jaxrs.json.PlanDetailJson;
import org.killbill.billing.jaxrs.json.SimplePlanJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;
import org.killbill.commons.metrics.TimedResource;
import org.killbill.xmlloader.XMLWriter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Singleton
@Path(JaxrsResource.CATALOG_PATH)
@Api(value = JaxrsResource.CATALOG_PATH, description = "Catalog information")
public class CatalogResource extends JaxRsResourceBase {

    private final CatalogUserApi catalogUserApi;

    @Inject
    public CatalogResource(final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final CatalogUserApi catalogUserApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, null, clock, context);
        this.catalogUserApi = catalogUserApi;
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_XML)
    @ApiOperation(value = "Retrieve the full catalog as XML", response = String.class, hidden = true)
    @ApiResponses(value = {})
    public Response getCatalogXml(@javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final TenantContext tenantContext = context.createContext(request);
        return Response.status(Status.OK).entity(XMLWriter.writeXML((VersionedCatalog) catalogUserApi.getCatalog(catalogName, tenantContext), VersionedCatalog.class)).build();
    }

    @TimedResource
    @POST
    @Consumes(APPLICATION_XML)
    @ApiOperation(value = "Upload the full catalog as XML")
    @ApiResponses(value = {})
    public Response uploadCatalogXml(final String catalogXML,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final HttpServletRequest request,
                                     @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);
        catalogUserApi.uploadCatalog(catalogXML, callContext);
        return uriBuilder.buildResponse(uriInfo, CatalogResource.class, null, null, request);
    }

    @TimedResource
    @GET
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve the catalog as JSON", response = StaticCatalog.class)
    @ApiResponses(value = {})
    public Response getCatalogJson(@QueryParam(QUERY_REQUESTED_DT) final String requestedDate,
                                   @javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {

        final TenantContext tenantContext = context.createContext(request);
        final DateTime catalogDateVersion = requestedDate != null ?
                                      DATE_TIME_FORMATTER.parseDateTime(requestedDate).toDateTime(DateTimeZone.UTC) :
                                      null;

        // Yack...
        final VersionedCatalog catalog = (VersionedCatalog) catalogUserApi.getCatalog(catalogName, tenantContext);

        final List<CatalogJson> result = new ArrayList<CatalogJson>();
        if (catalogDateVersion != null) {
            result.add(new CatalogJson(catalog, catalogDateVersion));
        } else {
            for (final StandaloneCatalog v : catalog.getVersions()) {
                result.add(new CatalogJson(catalog, new DateTime(v.getEffectiveDate())));
            }
        }
        return Response.status(Status.OK).entity(result).build();
    }

    // Need to figure out dependency on StandaloneCatalog
    //    @GET
    //    @Path("/xsd")
    //    @Produces(APPLICATION_XML)
    //    public String getCatalogXsd() throws Exception
    //    {
    //        InputStream stream = XMLSchemaGenerator.xmlSchema(StandaloneCatalog.class);
    //        StringWriter writer = new StringWriter();
    //        IOUtils.copy(stream, writer);
    //        String result = writer.toString();
    //
    //        return result;
    //    }

    @TimedResource
    @GET
    @Path("/availableAddons")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve available add-ons for a given product", response = PlanDetailJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getAvailableAddons(@QueryParam("baseProductName") final String baseProductName,
                                       @Nullable @QueryParam("priceListName") final String priceListName,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CatalogApiException {
        final TenantContext tenantContext = context.createContext(request);
        final StaticCatalog catalog = catalogUserApi.getCurrentCatalog(catalogName, tenantContext);
        final List<Listing> listings = catalog.getAvailableAddOnListings(baseProductName, priceListName);
        final List<PlanDetailJson> details = new ArrayList<PlanDetailJson>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJson(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }

    @TimedResource
    @GET
    @Path("/availableBasePlans")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Retrieve available base plans", response = PlanDetailJson.class, responseContainer = "List")
    @ApiResponses(value = {})
    public Response getAvailableBasePlans(@javax.ws.rs.core.Context final HttpServletRequest request) throws CatalogApiException {
        final TenantContext tenantContext = context.createContext(request);
        final StaticCatalog catalog = catalogUserApi.getCurrentCatalog(catalogName, tenantContext);
        final List<Listing> listings = catalog.getAvailableBasePlanListings();
        final List<PlanDetailJson> details = new ArrayList<PlanDetailJson>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJson(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }


    @TimedResource
    @POST
    @Path("/simplePlan")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Upload the full catalog as XML")
    @ApiResponses(value = {})
    public Response addSimplePlan(final SimplePlanJson simplePlan,
                                     @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                     @HeaderParam(HDR_REASON) final String reason,
                                     @HeaderParam(HDR_COMMENT) final String comment,
                                     @javax.ws.rs.core.Context final HttpServletRequest request,
                                     @javax.ws.rs.core.Context final UriInfo uriInfo) throws Exception {
        final CallContext callContext = context.createContext(createdBy, reason, comment, request);

        final SimplePlanDescriptor desc = new DefaultSimplePlanDescriptor(simplePlan.getPlanId(),
                                                                          simplePlan.getProductName(),
                                                                          simplePlan.getProductCategory(),
                                                                          simplePlan.getCurrency(),
                                                                          simplePlan.getAmount(),
                                                                          simplePlan.getBillingPeriod(),
                                                                          simplePlan.getTrialLength(),
                                                                          simplePlan.getTrialTimeUnit(),
                                                                          simplePlan.getAvailableBaseProducts());
        catalogUserApi.addSimplePlan(desc, null, callContext);
        return uriBuilder.buildResponse(uriInfo, CatalogResource.class, null, null, request);
    }

}
