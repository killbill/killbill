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
package com.ning.billing.jaxrs.util;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import com.ning.billing.jaxrs.resources.JaxrsResource;

public class JaxrsUriBuilder {

    public Response buildResponse(final Class<? extends JaxrsResource> theClass, final String getMethodName, final Object objectId) {
        final URI uri = UriBuilder.fromPath(objectId.toString()).build();
        final Response.ResponseBuilder ri = Response.created(uri);
        return ri.entity(new Object() {
            @SuppressWarnings(value = "all")
            public URI getUri() {
                final URI newUriFromResource = UriBuilder.fromResource(theClass).path(theClass, getMethodName).build(objectId);
                return newUriFromResource;
            }
        }).build();
    }


    public Response buildResponse(final Class<? extends JaxrsResource> theClass, final String getMethodName, final Object objectId, final String baseUri) {

        // Let's build a n absolute location for cross resources
        // See Jersey ContainerResponse.setHeaders
        final StringBuilder tmp = new StringBuilder(baseUri.substring(0, baseUri.length() - 1));
        tmp.append(UriBuilder.fromResource(theClass).path(theClass, getMethodName).build(objectId).toString());
        final URI newUriFromResource = UriBuilder.fromUri(tmp.toString()).build();
        final Response.ResponseBuilder ri = Response.created(newUriFromResource);
        return ri.entity(new Object() {
            @SuppressWarnings(value = "all")
            public URI getUri() {

                return newUriFromResource;
            }
        }).build();
    }
}
