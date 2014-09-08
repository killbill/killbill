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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.clock.Clock;
import org.killbill.billing.jaxrs.json.CatalogJsonSimple;
import org.killbill.billing.jaxrs.json.PlanDetailJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.xmlloader.XMLWriter;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Singleton
@Path(JaxrsResource.CATALOG_PATH)
public class CatalogResource extends JaxRsResourceBase {

    private final CatalogService catalogService;

    @Inject
    public CatalogResource(final CatalogService catalogService,
                           final JaxrsUriBuilder uriBuilder,
                           final TagUserApi tagUserApi,
                           final CustomFieldUserApi customFieldUserApi,
                           final AuditUserApi auditUserApi,
                           final AccountUserApi accountUserApi,
                           final PaymentApi paymentApi,
                           final Clock clock,
                           final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, clock, context);
        this.catalogService = catalogService;
    }

    //@Timed
    @GET
    @Produces(APPLICATION_XML)
    public Response getCatalogXml(@javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        return Response.status(Status.OK).entity(XMLWriter.writeXML(catalogService.getCurrentCatalog(), StaticCatalog.class)).build();
    }

    //@Timed
    @GET
    @Produces(APPLICATION_JSON)
    public Response getCatalogJson(@javax.ws.rs.core.Context final HttpServletRequest request) throws Exception {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();

        return Response.status(Status.OK).entity(catalog).build();
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

    //@Timed
    @GET
    @Path("/availableAddons")
    @Produces(APPLICATION_JSON)
    public Response getAvailableAddons(@QueryParam("baseProductName") final String baseProductName,
                                       @javax.ws.rs.core.Context final HttpServletRequest request) throws CatalogApiException {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();
        final List<Listing> listings = catalog.getAvailableAddonListings(baseProductName);
        final List<PlanDetailJson> details = new ArrayList<PlanDetailJson>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJson(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }

    //@Timed
    @GET
    @Path("/availableBasePlans")
    @Produces(APPLICATION_JSON)
    public Response getAvailableBasePlans(@javax.ws.rs.core.Context final HttpServletRequest request) throws CatalogApiException {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();
        final List<Listing> listings = catalog.getAvailableBasePlanListings();
        final List<PlanDetailJson> details = new ArrayList<PlanDetailJson>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJson(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }

    //@Timed
    @GET
    @Path("/simpleCatalog")
    @Produces(APPLICATION_JSON)
    public Response getSimpleCatalog(@javax.ws.rs.core.Context final HttpServletRequest request) throws CatalogApiException {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();

        final CatalogJsonSimple json = new CatalogJsonSimple(catalog);
        return Response.status(Status.OK).entity(json).build();
    }
}
