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
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.jaxrs.resources.JaxRsResourceBase;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.util.config.definition.JaxrsConfig;

public class JaxrsUriBuilder {

    private final JaxrsConfig jaxrsConfig;
    private final Map<Class, UriBuilder> classToUriBuilder = new HashMap<Class, UriBuilder>();
    private final Map<String, UriBuilder> classAndMethodToUriBuilder = new HashMap<String, UriBuilder>();
    private final Map<String, UriBuilder> pathAndClassToUriBuilder = new HashMap<String, UriBuilder>();
    private final Map<String, UriBuilder> pathClassAndMethodToUriBuilder = new HashMap<String, UriBuilder>();

    @Inject
    public JaxrsUriBuilder(JaxrsConfig jaxrsConfig) {
        this.jaxrsConfig = jaxrsConfig;
    }

    public Response buildResponse(final UriInfo uriInfo, final Class<? extends JaxrsResource> theClass,
                                  final String getMethodName, final Object objectId) {
        final URI location = buildLocation(uriInfo, theClass, getMethodName, objectId);
        return !jaxrsConfig.isJaxrsLocationFullUrl() ?
               Response.status(Response.Status.CREATED).header("Location", location.getPath()).build() :
               Response.created(location).build();
    }

    public URI buildLocation(final UriInfo uriInfo, final Class<? extends JaxrsResource> theClass,
                             final String getMethodName, final Object objectId) {
        final UriBuilder uriBuilder = getUriBuilder(uriInfo.getBaseUri().getPath(), theClass, getMethodName);

        if (jaxrsConfig.isJaxrsLocationFullUrl()) {
            uriBuilder.scheme(uriInfo.getAbsolutePath().getScheme())
                      .host(uriInfo.getAbsolutePath().getHost())
                      .port(uriInfo.getAbsolutePath().getPort());
        }
        return objectId != null ? uriBuilder.build(objectId) : uriBuilder.build();
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

    private UriBuilder getUriBuilder(final String path, final Class<? extends JaxrsResource> theClassMaybeEnhanced, @Nullable final String getMethodName) {
        final Class theClass = getNonEnhancedClass(theClassMaybeEnhanced);
        return getMethodName != null ? fromPath(path.equals("/") ? path.substring(1) : path, theClass, getMethodName) : fromPath(path, theClass);
    }

    private UriBuilder fromPath(final String path, final Class theClass, final String getMethodName) {
        final String key = path + theClass.getName() + getMethodName;

        UriBuilder uriBuilder = pathClassAndMethodToUriBuilder.get(key);
        if (uriBuilder == null) {
            synchronized (pathClassAndMethodToUriBuilder) {
                uriBuilder = pathClassAndMethodToUriBuilder.get(key);
                if (uriBuilder == null) {
                    uriBuilder = fromPath(path, theClass).path(theClass, getMethodName);
                    pathClassAndMethodToUriBuilder.put(key, uriBuilder);
                }
            }
        }
        return uriBuilder.clone();
    }

    private UriBuilder fromPath(final String path, final Class theClass) {
        final String key = path + theClass.getName();

        UriBuilder uriBuilder = pathAndClassToUriBuilder.get(key);
        if (uriBuilder == null) {
            synchronized (pathAndClassToUriBuilder) {
                uriBuilder = pathAndClassToUriBuilder.get(key);
                if (uriBuilder == null) {
                    uriBuilder = UriBuilder.fromPath(path).path(theClass);
                    pathAndClassToUriBuilder.put(key, uriBuilder);
                }
            }
        }
        return uriBuilder.clone();
    }

    private UriBuilder getUriBuilder(final Class<? extends JaxrsResource> theClassMaybeEnhanced, @Nullable final String getMethodName) {
        final Class theClass = getNonEnhancedClass(theClassMaybeEnhanced);
        return getMethodName != null ? fromResource(theClass, getMethodName) : fromResource(theClass);
    }

    private UriBuilder fromResource(final Class theClass, final String getMethodName) {
        final String key = theClass.getName() + getMethodName;

        UriBuilder uriBuilder = classAndMethodToUriBuilder.get(key);
        if (uriBuilder == null) {
            synchronized (classAndMethodToUriBuilder) {
                uriBuilder = classAndMethodToUriBuilder.get(key);
                if (uriBuilder == null) {
                    uriBuilder = fromResource(theClass).path(theClass, getMethodName);
                    classAndMethodToUriBuilder.put(key, uriBuilder);
                }
            }
        }
        return uriBuilder.clone();
    }

    private UriBuilder fromResource(final Class theClass) {
        UriBuilder uriBuilder = classToUriBuilder.get(theClass);
        if (uriBuilder == null) {
            synchronized (classToUriBuilder) {
                uriBuilder = classToUriBuilder.get(theClass);
                if (uriBuilder == null) {
                    uriBuilder = UriBuilder.fromResource(theClass);
                    classToUriBuilder.put(theClass, uriBuilder);
                }
            }
        }
        return uriBuilder.clone();
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
