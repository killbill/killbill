/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.killbill.billing.util.api.AuditLevel;

import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.integration.api.OpenApiReader;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import static org.killbill.billing.jaxrs.resources.JaxrsResource.HDR_CREATED_BY;
import static org.killbill.billing.jaxrs.resources.JaxrsResource.QUERY_AUDIT;

@OpenAPIDefinition
public class KillBillApiDefinition implements ReaderListener {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";
    public static final String API_KEY_SCHEME = "Killbill Api Key";
    public static final String API_SECRET_SCHEME = "Killbill Api Secret";

    @Override
    public void beforeScan(final OpenApiReader reader, final OpenAPI openAPI) {
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(new Components());
        }

        final SecurityScheme basicAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic");
        openAPI.getComponents().addSecuritySchemes(BASIC_AUTH_SCHEME, basicAuth);

        final SecurityScheme apiKey = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .name("X-Killbill-ApiKey")
                .in(SecurityScheme.In.HEADER);
        openAPI.getComponents().addSecuritySchemes(API_KEY_SCHEME, apiKey);

        final SecurityScheme apiSecret = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .name("X-Killbill-ApiSecret")
                .in(SecurityScheme.In.HEADER);
        openAPI.getComponents().addSecuritySchemes(API_SECRET_SCHEME, apiSecret);
    }

    @Override
    public void afterScan(final OpenApiReader reader, final OpenAPI openAPI) {
        if (openAPI.getPaths() != null) {
            for (final String pathName : openAPI.getPaths().keySet()) {
                final PathItem pathItem = openAPI.getPaths().get(pathName);
                decorateOperation(pathItem.getGet(), pathName, "GET");
                decorateOperation(pathItem.getPost(), pathName, "POST");
                decorateOperation(pathItem.getPut(), pathName, "PUT");
                decorateOperation(pathItem.getDelete(), pathName, "DELETE");
                decorateOperation(pathItem.getOptions(), pathName, "OPTIONS");
            }
        }

        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            for (final Schema<?> schema : openAPI.getComponents().getSchemas().values()) {
                if (schema.getProperties() != null) {
                    for (final Object prop : schema.getProperties().values()) {
                        if (prop instanceof Schema) {
                            ((Schema<?>) prop).setReadOnly(null);
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void decorateOperation(final Operation op, final String pathName, final String httpMethod) {
        if (op != null) {

            // Bug in swagger ? somehow when we only specify a 201, swagger adds a 200 response with the schema response
            if (httpMethod.equals("POST")) {
                if (op.getResponses() != null &&
                    op.getResponses().containsKey("201") && op.getResponses().containsKey("200")) {
                    final ApiResponse resp200 = op.getResponses().remove("200");
                    final ApiResponse resp201 = op.getResponses().get("201");
                    if (resp201.getContent() == null && resp200.getContent() != null) {
                        resp201.setContent(resp200.getContent());
                    }
                }
            }

            op.addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME));
            if (requiresTenantInformation(pathName, httpMethod)) {
                op.addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
                op.addSecurityItem(new SecurityRequirement().addList(API_SECRET_SCHEME));
            }

            // In OpenAPI 3, request body is separate from parameters
            if (op.getRequestBody() != null) {
                op.getRequestBody().setRequired(true);
            }

            if (op.getParameters() != null) {
                for (final Parameter p : op.getParameters()) {
                    if ("path".equals(p.getIn())) {
                        p.setRequired(true);
                    } else if ("header".equals(p.getIn())) {
                        if (HDR_CREATED_BY.equals(p.getName())) {
                            p.setRequired(true);
                        }
                    } else if ("query".equals(p.getIn())) {
                        if (QUERY_AUDIT.equals(p.getName())) {
                            p.setRequired(false);
                            if (p.getSchema() == null) {
                                p.setSchema(new Schema<String>());
                            }
                            p.getSchema().setType("string");
                            final List<String> values = Arrays.stream(AuditLevel.values())
                                    .map(Objects::toString)
                                    .collect(Collectors.toUnmodifiableList());
                            p.getSchema().setEnum(values);
                        } else if (isDateParameter(p.getName())) {
                            if (p.getSchema() == null) {
                                p.setSchema(new Schema<String>());
                            }
                            p.getSchema().setType("string");
                            // Yack... See #922
                            if (op.getOperationId() != null &&
                                (op.getOperationId().equals("getCatalogJson") ||
                                 op.getOperationId().equals("getCatalogXml") ||
                                 op.getOperationId().equals("setTestClockTime"))) {
                                p.getSchema().setFormat("date-time");
                            } else {
                                p.getSchema().setFormat("date");
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isDateParameter(final String name) {
        return JaxrsResource.QUERY_REQUESTED_DT.equals(name) ||
               JaxrsResource.QUERY_ENTITLEMENT_REQUESTED_DT.equals(name) ||
               JaxrsResource.QUERY_BILLING_REQUESTED_DT.equals(name) ||
               JaxrsResource.QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT.equals(name) ||
               JaxrsResource.QUERY_START_DATE.equals(name) ||
               JaxrsResource.QUERY_END_DATE.equals(name) ||
               JaxrsResource.QUERY_TARGET_DATE.equals(name);
    }

    public static boolean requiresTenantInformation(final String path, final String httpMethod) {
        boolean shouldSkipTenantInfoForRequests = (
                // Chicken - egg problem
                isTenantCreationRequest(path, httpMethod) ||
                // Retrieve user permissions should not require tenant info since this is cross tenants
                isPermissionRequest(path) ||
                // Node request are cross tenant
                isNodeInfoRequest(path) ||
                // See KillBillShiroWebModule#CorsBasicHttpAuthenticationFilter
                isOptionsRequest(httpMethod) ||
                // Shift the responsibility to the plugin
                isPluginRequest(path) ||
                // Static resources (welcome screen, Swagger, etc.)
                isNotKbNorPluginResourceRequest(path, httpMethod));
        return !shouldSkipTenantInfoForRequests;
    }

    private static boolean isPermissionRequest(final String path) {
        return path != null && path.startsWith(JaxrsResource.SECURITY_PATH);
    }

    private static boolean isTenantCreationRequest(final String path, final String httpMethod) {
        return JaxrsResource.TENANTS_PATH.equals(path);
    }

    private static boolean isNodeInfoRequest(final String path) {
        return JaxrsResource.NODES_INFO_PATH.equals(path);
    }

    private static boolean isOptionsRequest(final String httpMethod) {
        return "OPTIONS".equalsIgnoreCase(httpMethod);
    }

    private static boolean isNotKbNorPluginResourceRequest(final String path, final String httpMethod) {
        return !isPluginRequest(path) && !isKbApiRequest(path) && "GET".equalsIgnoreCase(httpMethod);
    }

    private static boolean isKbApiRequest(final String path) {
        return path != null && path.startsWith(JaxrsResource.PREFIX);
    }

    private static boolean isPluginRequest(final String path) {
        return path != null && path.startsWith(JaxrsResource.PLUGINS_PATH);
    }

}
