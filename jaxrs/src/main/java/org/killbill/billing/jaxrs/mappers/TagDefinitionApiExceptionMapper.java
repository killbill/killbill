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

package org.killbill.billing.jaxrs.mappers;

import javax.inject.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.util.api.TagDefinitionApiException;

@Singleton
@Provider
public class TagDefinitionApiExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<TagDefinitionApiException> {

    private final UriInfo uriInfo;

    public TagDefinitionApiExceptionMapper(@Context final UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(final TagDefinitionApiException exception) {
        if (exception.getCode() == ErrorCode.TAG_DEFINITION_ALREADY_EXISTS.getCode()) {
            return buildConflictingRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG.getCode()) {
            return buildConflictingRequestResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TAG_DEFINITION_DOES_NOT_EXIST.getCode()) {
            return buildNotFoundResponse(exception, uriInfo);
        } else if (exception.getCode() == ErrorCode.TAG_DEFINITION_IN_USE.getCode()) {
            return buildBadRequestResponse(exception, uriInfo);
        } else {
            return buildBadRequestResponse(exception, uriInfo);
        }
    }
}
