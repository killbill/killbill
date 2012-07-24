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
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Listing;
import com.ning.billing.catalog.api.StaticCatalog;
import com.ning.billing.jaxrs.json.CatalogJsonSimple;
import com.ning.billing.jaxrs.json.PlanDetailJason;
import com.ning.billing.util.config.XMLWriter;

@Singleton
@Path(JaxrsResource.CATALOG_PATH)
public class CatalogResource implements JaxrsResource {

    private final CatalogService catalogService;

    @Inject
    public CatalogResource(final CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GET
    @Produces(APPLICATION_XML)
    public Response getCatalogXml() throws Exception {
        return Response.status(Status.OK).entity(XMLWriter.writeXML(catalogService.getCurrentCatalog(), StaticCatalog.class)).build();
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getCatalogJson() throws Exception {
        StaticCatalog catalog = catalogService.getCurrentCatalog();

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


    @GET
    @Path("/availableAddons")
    @Produces(APPLICATION_JSON)
    public Response getAvailableAddons(@QueryParam("baseProductName") final String baseProductName) throws CatalogApiException {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();
        final List<Listing> listings = catalog.getAvailableAddonListings(baseProductName);
        final List<PlanDetailJason> details = new ArrayList<PlanDetailJason>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJason(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }

    @GET
    @Path("/availableBasePlans")
    @Produces(APPLICATION_JSON)
    public Response getAvailableBasePlans() throws CatalogApiException {
        final StaticCatalog catalog = catalogService.getCurrentCatalog();
        final List<Listing> listings = catalog.getAvailableBasePlanListings();
        final List<PlanDetailJason> details = new ArrayList<PlanDetailJason>();
        for (final Listing listing : listings) {
            details.add(new PlanDetailJason(listing));
        }
        return Response.status(Status.OK).entity(details).build();
    }

    @GET
    @Path("/simpleCatalog")
    @Produces(APPLICATION_JSON)
    public Response getSimpleCatalog() throws CatalogApiException {

        StaticCatalog catalog  = catalogService.getCurrentCatalog();

        CatalogJsonSimple json = new CatalogJsonSimple(catalog);
        return Response.status(Status.OK).entity(json).build();
    }
}
