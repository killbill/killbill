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

import java.util.List;

import org.killbill.billing.util.api.AuditLevel;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.BasicAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.Property;

import static org.killbill.billing.jaxrs.resources.JaxrsResource.HDR_CREATED_BY;
import static org.killbill.billing.jaxrs.resources.JaxrsResource.QUERY_AUDIT;

@SwaggerDefinition
public class KillBillApiDefinition implements ReaderListener {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";
    public static final String API_KEY_SCHEME = "Killbill Api Key";
    public static final String API_SECRET_SCHEME = "Killbill Api Secret";

    @Override
    public void beforeScan(final io.swagger.jaxrs.Reader reader, final Swagger swagger) {
        BasicAuthDefinition basicAuthDefinition = new BasicAuthDefinition();
        swagger.addSecurityDefinition(BASIC_AUTH_SCHEME, basicAuthDefinition);

        ApiKeyAuthDefinition xKillbillApiKey = new ApiKeyAuthDefinition("X-Killbill-ApiKey", In.HEADER);
        swagger.addSecurityDefinition(API_KEY_SCHEME, xKillbillApiKey);

        ApiKeyAuthDefinition xKillbillApiSecret = new ApiKeyAuthDefinition("X-Killbill-ApiSecret", In.HEADER);
        swagger.addSecurityDefinition(API_SECRET_SCHEME, xKillbillApiSecret);
    }

    @Override
    public void afterScan(final io.swagger.jaxrs.Reader reader, final Swagger swagger) {

        for (final String pathName : swagger.getPaths().keySet()) {
            final Path path = swagger.getPaths().get(pathName);
            decorateOperation(path.getGet(), pathName, "GET");
            decorateOperation(path.getPost(), pathName, "POST");
            decorateOperation(path.getPut(), pathName, "PUT");
            decorateOperation(path.getDelete(), pathName, "DELETE");
            decorateOperation(path.getOptions(), pathName, "OPTIONS");

        }

        for (final Model m : swagger.getDefinitions().values()) {
            if (m.getProperties() != null) {
                for (final Property p : m.getProperties().values()) {
                    p.setReadOnly(false);
                }
            }
        }
    }

    private void decorateOperation(final Operation op, final String pathName, final String httpMethod) {
        if (op != null) {

            // Bug in swagger ? somehow when we only specify a 201, swagger adds a 200 response with the schema response
            if (httpMethod.equals("POST")) {
                if (op.getResponses().containsKey("201") && op.getResponses().containsKey("200")) {
                    final Response resp200 =op.getResponses().remove("200");
                    final Response resp201 = op.getResponses().get("201");
                    if (resp201.getSchema() == null) {
                        resp201.setSchema(resp200.getSchema());
                    }
                }
            }

            op.addSecurity(BASIC_AUTH_SCHEME, null);
            if (requiresTenantInformation(pathName, httpMethod)) {
                op.addSecurity(API_KEY_SCHEME, null);
                op.addSecurity(API_SECRET_SCHEME, null);
            }

            for (Parameter p : op.getParameters()) {
                if (p instanceof BodyParameter) {
                    p.setRequired(true);
                } else if (p instanceof PathParameter) {
                    p.setRequired(true);
                } else if (p instanceof HeaderParameter) {
                    if (p.getName().equals(HDR_CREATED_BY)) {
                        p.setRequired(true);
                    }
                } else if (p instanceof QueryParameter) {
                    QueryParameter qp = (QueryParameter) p;
                    if (qp.getName().equals(QUERY_AUDIT)) {
                        qp.setRequired(false);
                        qp.setType("string");
                        final List<String> values = ImmutableList.copyOf(Iterables.transform(ImmutableList.<AuditLevel>copyOf(AuditLevel.values()), new Function<AuditLevel, String>() {
                            @Override
                            public String apply(final AuditLevel input) {
                                return input.toString();
                            }
                        }));
                        qp.setEnum(values);
                    } else if (qp.getName().equals(JaxrsResource.QUERY_REQUESTED_DT) ||
                               qp.getName().equals(JaxrsResource.QUERY_ENTITLEMENT_REQUESTED_DT) ||
                               qp.getName().equals(JaxrsResource.QUERY_BILLING_REQUESTED_DT) ||
                               qp.getName().equals(JaxrsResource.QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT) ||
                               qp.getName().equals(JaxrsResource.QUERY_START_DATE) ||
                               qp.getName().equals(JaxrsResource.QUERY_END_DATE) ||
                               qp.getName().equals(JaxrsResource.QUERY_TARGET_DATE)) {
                        qp.setType("string");
                        // Yack... See #922
                        if (op.getOperationId().equals("getCatalogJson") || op.getOperationId().equals("setTestClockTime")) {
                            qp.setFormat("date-time");
                        } else {
                            qp.setFormat("date");
                        }
                    }
                }
            }
        }
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
