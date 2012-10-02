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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.tag.TagDefinition;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JaxrsResource.TAG_DEFINITIONS_PATH)
public class TagResource extends JaxRsResourceBase {

    @Inject
    public TagResource(final JaxrsUriBuilder uriBuilder,
                       final TagUserApi tagUserApi,
                       final CustomFieldUserApi customFieldUserApi,
                       final AuditUserApi auditUserApi,
                       final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getTagDefinitions(@javax.ws.rs.core.Context final HttpServletRequest request) {
        final List<TagDefinition> tagDefinitions = tagUserApi.getTagDefinitions(context.createContext(request));

        final List<TagDefinitionJson> result = new LinkedList<TagDefinitionJson>();
        for (final TagDefinition cur : tagDefinitions) {
            result.add(new TagDefinitionJson(cur));
        }

        return Response.status(Status.OK).entity(result).build();
    }

    @GET
    @Path("/{tagDefinitionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response getTagDefinition(@PathParam("tagDefinitionId") final String tagDefId,
                                     @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final TagDefinition tagDef = tagUserApi.getTagDefinition(UUID.fromString(tagDefId), context.createContext(request));
        final TagDefinitionJson json = new TagDefinitionJson(tagDef);
        return Response.status(Status.OK).entity(json).build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response createTagDefinition(final TagDefinitionJson json,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        final TagDefinition createdTagDef = tagUserApi.create(json.getName(), json.getDescription(), context.createContext(createdBy, reason, comment, request));
        return uriBuilder.buildResponse(TagResource.class, "getTagDefinition", createdTagDef.getId());
    }

    @DELETE
    @Path("/{tagDefinitionId:" + UUID_PATTERN + "}")
    @Produces(APPLICATION_JSON)
    public Response deleteTagDefinition(@PathParam("tagDefinitionId") final String tagDefId,
                                        @HeaderParam(HDR_CREATED_BY) final String createdBy,
                                        @HeaderParam(HDR_REASON) final String reason,
                                        @HeaderParam(HDR_COMMENT) final String comment,
                                        @javax.ws.rs.core.Context final HttpServletRequest request) throws TagDefinitionApiException {
        tagUserApi.deleteTagDefinition(UUID.fromString(tagDefId), context.createContext(createdBy, reason, comment, request));
        return Response.status(Status.NO_CONTENT).build();
    }

    @Override
    protected ObjectType getObjectType() {
        return ObjectType.TAG_DEFINITION;
    }
}
