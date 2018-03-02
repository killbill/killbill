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

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.auth.BasicAuthDefinition;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.properties.Property;

@SwaggerDefinition
public class KillBillApiDefinition implements ReaderListener {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Override
    public void beforeScan(final io.swagger.jaxrs.Reader reader, final Swagger swagger) {
        BasicAuthDefinition basicAuthDefinition = new BasicAuthDefinition();
        swagger.addSecurityDefinition(BASIC_AUTH_SCHEME, basicAuthDefinition);
    }

    @Override
    public void afterScan(final io.swagger.jaxrs.Reader reader, final Swagger swagger) {

        final HeaderParameter apiKeyParam = new HeaderParameter();
        apiKeyParam.setName("X-Killbill-ApiKey");
        apiKeyParam.setType("string");
        apiKeyParam.setRequired(true);

        final HeaderParameter apiSecretParam = new HeaderParameter();
        apiSecretParam.setName("X-Killbill-ApiSecret");
        apiSecretParam.setType("string");
        apiSecretParam.setRequired(true);

        for (final String pathName : swagger.getPaths().keySet()) {
            final Path path = swagger.getPaths().get(pathName);
            applyExtraSchemaOperation(path.getGet(), pathName, "GET", apiKeyParam, apiSecretParam);
            applyExtraSchemaOperation(path.getPost(), pathName, "POST", apiKeyParam, apiSecretParam);
            applyExtraSchemaOperation(path.getPut(), pathName, "PUT", apiKeyParam, apiSecretParam);
            applyExtraSchemaOperation(path.getDelete(), pathName, "DELETE", apiKeyParam, apiSecretParam);
            applyExtraSchemaOperation(path.getOptions(), pathName, "OPTIONS", apiKeyParam, apiSecretParam);
        }

        for (final Model m : swagger.getDefinitions().values()) {
            if (m.getProperties() != null) {
                for (final Property p : m.getProperties().values()) {
                    p.setReadOnly(false);
                }
            }
        }
    }

    private void applyExtraSchemaOperation(final Operation op, final String pathName, final String httpMethod, final HeaderParameter apiKeyParam, final HeaderParameter apiSecretParam) {
        if (op != null) {
            op.addSecurity(BASIC_AUTH_SCHEME, null);
            if (requiresTenantInformation(pathName, httpMethod)) {
                op.addParameter(apiKeyParam);
                op.addParameter(apiSecretParam);
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
        return JaxrsResource.TENANTS_PATH.equals(path) && "POST".equalsIgnoreCase(httpMethod);
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
