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
package com.ning.billing.jaxrs.util;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.ning.billing.jaxrs.resources.BaseJaxrsResource;

public class JaxrsUriBuilder {

	
	public Response buildResponse(final Class<? extends BaseJaxrsResource> theClass, final String getMethodName, final UUID objectId) {
		URI uri = UriBuilder.fromPath(objectId.toString()).build();
		Response.ResponseBuilder ri = Response.created(uri);
		return ri.entity(new Object() {
			@SuppressWarnings(value = "all")
			public URI getUri() {
				return UriBuilder.fromResource(theClass).path(theClass, getMethodName).build(objectId);
			}
		}).build();
	}
}
