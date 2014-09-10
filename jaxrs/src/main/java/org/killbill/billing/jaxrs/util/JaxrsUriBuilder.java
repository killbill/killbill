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

package org.killbill.billing.jaxrs.util;

import java.net.URI;
import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;
import org.killbill.billing.jaxrs.resources.JaxrsResource;

public class JaxrsUriBuilder {

    public Response buildResponse(final UriInfo uriInfo, final Class<? extends JaxrsResource> theClass, final String getMethodName, final Object objectId) {
        final UriBuilder uriBuilder = getUriBuilder(theClass, getMethodName).scheme(uriInfo.getAbsolutePath().getScheme())
                                                                            .host(uriInfo.getAbsolutePath().getHost())
                                                                            .port(uriInfo.getAbsolutePath().getPort());

        final URI location = objectId != null ? uriBuilder.build(objectId) : uriBuilder.build();
        return Response.created(location).build();
    }

    public URI nextPage(final Class<? extends JaxrsResource> theClass, final String getMethodName, final Long nextOffset, final Long limit, final Map<String, String> params) {
        if (nextOffset == null || limit == null) {
            // End of pagination?
            return null;
        }

        final UriBuilder uriBuilder = getUriBuilder(theClass, getMethodName).queryParam(JaxRsResourceBase.QUERY_SEARCH_OFFSET, nextOffset)
                                                                            .queryParam(JaxRsResourceBase.QUERY_SEARCH_LIMIT, limit);
        for (final String key : params.keySet()) {
            uriBuilder.queryParam(key, params.get(key));
        }
        return uriBuilder.build();
    }

    public Response buildResponse(final Class<? extends JaxrsResource> theClass, final String getMethodName, final Object objectId, final String baseUri) {

        // Let's build a n absolute location for cross resources
        // See Jersey ContainerResponse.setHeaders
        final StringBuilder tmp = new StringBuilder(baseUri.substring(0, baseUri.length() - 1));
        tmp.append(getUriBuilder(theClass, getMethodName).build(objectId).toString());
        final URI newUriFromResource = UriBuilder.fromUri(tmp.toString()).build();
        final Response.ResponseBuilder ri = Response.created(newUriFromResource);
        final Object obj = new Object() {
            @SuppressWarnings(value = "all")
            public URI getUri() {
                return newUriFromResource;
            }
        };
        return ri.entity(obj).build();
    }

    private UriBuilder getUriBuilder(final Class<? extends JaxrsResource> theClassMaybeEnhanced, final String getMethodName) {
        final Class theClass = getNonEnhancedClass(theClassMaybeEnhanced);
        return UriBuilder.fromResource(theClass).path(theClass, getMethodName);
    }

    private Class getNonEnhancedClass(final Class<? extends JaxrsResource> theClassMaybeEnhanced) {
        // If Guice is proxying the class for example ($EnhancerByGuice$), we want the real class.
        // See https://java.net/projects/jersey/lists/users/archive/2008-10/message/291
        Class theClass = theClassMaybeEnhanced;
        while (theClass.getAnnotation(Path.class) == null && JaxRsResourceBase.class.isAssignableFrom(theClass)) {
            theClass = theClass.getSuperclass();
        }

        return theClass;
    }
}
