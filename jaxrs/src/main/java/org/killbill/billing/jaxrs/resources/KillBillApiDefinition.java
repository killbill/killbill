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
import java.util.Set;
import java.util.stream.Collectors;

import org.killbill.billing.util.api.AuditLevel;

import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.integration.api.OpenApiReader;
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
    public static final String API_KEY_SCHEME = "killbillApiKey";
    public static final String API_SECRET_SCHEME = "killbillApiSecret";

    @Override
    public void beforeScan(final OpenApiReader reader, final OpenAPI openAPI) {
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
                    // Swagger Core 2.x (OpenAPI 3) introspects @JsonCreator constructor parameters and
                    // discovers @JsonProperty values that differ from getter-derived names
                    // (See for example: src/main/java/org/killbill/billing/jaxrs/json/PlanDetailJson.java)
                    // It surfaces these as writeOnly properties (ex: "final_phase_billing_period" alongside
                    // "finalPhaseBillingPeriod", or "tiers" alongside "blocks"). These are internal
                    // deserialization aliases for backward compatibility. The server accepts legacy
                    // snake_case or renamed input fields so older clients don't break, but only ever
                    // serializes (outputs) the camelCase getter-derived names. Swagger Core 1.x (0.24.x)
                    // never discovered constructor parameters, so these aliases were invisible in the
                    // swagger spec. Removing them here ensures the generated OpenAPI spec remains
                    // backward compatible with 0.24.x: generated clients see only the canonical
                    // property names and won't be confused by write-only duplicates.
                    schema.getProperties().entrySet().removeIf(entry -> {
                        if (entry.getValue() instanceof Schema) {
                            return Boolean.TRUE.equals(((Schema<?>) entry.getValue()).getWriteOnly());
                        }
                        return false;
                    });

                    for (final Object prop : schema.getProperties().values()) {
                        if (prop instanceof Schema) {
                            ((Schema<?>) prop).setReadOnly(null);
                        }
                    }
                }
            }

            // Swagger Core 2.x registers component schemas for every non-primitive type it encounters
            // during scanning, even when those types are only used as method parameter types (not as
            // response/request body models). In Swagger Core 1.x (0.24.x), these types were never
            // registered because the scanner only introspected response models and explicit @ApiModel
            // classes. The schemas below are scanner artifacts that no operation, parameter, or other
            // schema references — they generate unused model classes in client SDKs:
            //
            // - AuditMode: query parameter wrapper type (server parses via single-String constructor,
            //   the inline schema is set explicitly in decorateOperation above)
            // - MultivaluedMapStringString: JAX-RS form body type from PluginResource.doFormPOST
            // - MultivaluedMapStringObject: resolved from MultivaluedMap interface hierarchy
            // - Response, EntityTag, Link, MediaType, NewCookie, StatusType, UriBuilder: JAX-RS
            //   framework types. Swagger Core 1.x had built-in filtering for javax.ws.rs.core.Response
            //   and never introspected it; Swagger Core 2.x does not filter and follows the entire
            //   class hierarchy if Response is explicitly referenced via @Schema(implementation=...).
            //   The source annotations on AdminResource and ExportResource have been fixed, but this
            //   list acts as a safety net in case similar references are added in the future.
            //
            // Removing them maintains backward compatibility with the 0.24.x swagger spec which never
            // exposed these internal types to generated clients.
            final Set<String> orphanedSchemas = Set.of(
                    "AuditMode",
                    "MultivaluedMapStringString",
                    "MultivaluedMapStringObject",
                    "Response",
                    "EntityTag",
                    "Link",
                    "MediaType",
                    "NewCookie",
                    "StatusType",
                    "UriBuilder"
            );
            openAPI.getComponents().getSchemas().keySet().removeAll(orphanedSchemas);
        }
    }

    @SuppressWarnings("unchecked")
    private void decorateOperation(final Operation op, final String pathName, final String httpMethod) {
        if (op != null) {
            final SecurityRequirement securityRequirement = new SecurityRequirement().addList(BASIC_AUTH_SCHEME);
            if (requiresTenantInformation(pathName, httpMethod)) {
                securityRequirement
                        .addList(API_KEY_SCHEME)
                        .addList(API_SECRET_SCHEME);
            }
            op.addSecurityItem(securityRequirement);

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
                            // Swagger Core 2.x (OpenAPI 3) introspects AuditMode as a POJO because it has
                            // a getLevel() getter, producing a $ref to an object schema in components/schemas.
                            // In Swagger Core 1.x (Swagger 2.0, master/0.24.x), query parameter types with a
                            // single-String constructor were treated as opaque scalars, so no $ref was emitted.
                            // We must unconditionally replace the schema to clear any $ref the scanner set;
                            // otherwise the $ref takes precedence per OAS 3.0 and the type/enum siblings are
                            // ignored, causing code generators to produce object-shaped requests instead of
                            // the flat ?audit=NONE string the server actually expects (via JAX-RS
                            // single-String-constructor convention on AuditMode).
                            //
                            // Furthermore, this ensures backward compatibility with the 0.24.x swagger spec
                            // which declared audit as a plain string enum — existing generated clients and
                            // integrations rely on sending ?audit=NONE as a flat query string value.
                            p.setSchema(new Schema<String>());
                            p.getSchema().setType("string");
                            p.getSchema().setDefault("NONE");
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
