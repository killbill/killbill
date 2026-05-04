/*
 * Copyright 2026 The Billing Project, LLC
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

import java.util.Map;
import java.util.Set;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.swagger.v3.jaxrs2.ReaderListener;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenApiReader;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

public class TestKillBillApiDefinition {

    private final ReaderListener apiDefinition = new KillBillApiDefinition();
    private final OpenApiReader reader = Mockito.mock(OpenApiReader.class);

    @Test(groups = "fast")
    public void testBeforeScanAddsSecuritySchemes() {
        final OpenAPI openAPI = new OpenAPI().components(new Components());

        apiDefinition.beforeScan(reader, openAPI);
        final Map<String, SecurityScheme> securitySchemes = openAPI.getComponents().getSecuritySchemes();

        Assert.assertNotNull(securitySchemes);
        Assert.assertEquals(securitySchemes.size(), 3);

        final SecurityScheme basicAuth = securitySchemes.get(KillBillApiDefinition.BASIC_AUTH_SCHEME);
        Assert.assertNotNull(basicAuth);
        Assert.assertEquals(basicAuth.getType(), SecurityScheme.Type.HTTP);
        Assert.assertEquals(basicAuth.getScheme(), "basic");

        final SecurityScheme apiKey = securitySchemes.get(KillBillApiDefinition.API_KEY_SCHEME);
        Assert.assertNotNull(apiKey);
        Assert.assertEquals(apiKey.getType(), SecurityScheme.Type.APIKEY);
        Assert.assertEquals(apiKey.getIn(), SecurityScheme.In.HEADER);
        Assert.assertEquals(apiKey.getName(), JaxrsResource.HDR_API_KEY);

        final SecurityScheme apiSecret = securitySchemes.get(KillBillApiDefinition.API_SECRET_SCHEME);
        Assert.assertNotNull(apiSecret);
        Assert.assertEquals(apiSecret.getType(), SecurityScheme.Type.APIKEY);
        Assert.assertEquals(apiSecret.getIn(), SecurityScheme.In.HEADER);
        Assert.assertEquals(apiSecret.getName(), JaxrsResource.HDR_API_SECRET);
    }

    @Test(groups = "fast")
    public void testAfterScanAddsTenantSecurityRequirement() {
        final Operation operation = new Operation();
        final OpenAPI openAPI = new OpenAPI()
                .components(new Components())
                .paths(new Paths().addPathItem(JaxrsResource.ACCOUNTS_PATH, new PathItem().get(operation)));

        apiDefinition.afterScan(reader, openAPI);

        Assert.assertNotNull(operation.getSecurity());
        Assert.assertEquals(operation.getSecurity().size(), 1);
        final SecurityRequirement securityRequirement = operation.getSecurity().get(0); // Java21 has getFirst()
        Assert.assertEquals(securityRequirement.size(), 3);
        Assert.assertTrue(securityRequirement.containsKey(KillBillApiDefinition.BASIC_AUTH_SCHEME));
        Assert.assertTrue(securityRequirement.containsKey(KillBillApiDefinition.API_KEY_SCHEME));
        Assert.assertTrue(securityRequirement.containsKey(KillBillApiDefinition.API_SECRET_SCHEME));
    }

    @Test(groups = "fast")
    public void testAfterScanAddsOnlyBasicAuthWhenTenantInformationIsNotRequired() {
        final Operation operation = new Operation();
        final OpenAPI openAPI = new OpenAPI()
                .components(new Components())
                .paths(new Paths().addPathItem(JaxrsResource.TENANTS_PATH, new PathItem().post(operation)));

        apiDefinition.afterScan(reader, openAPI);

        Assert.assertNotNull(operation.getSecurity());
        Assert.assertEquals(operation.getSecurity().size(), 1);
        final SecurityRequirement securityRequirement = operation.getSecurity().get(0); // Java21 has getFirst()
        Assert.assertEquals(securityRequirement.size(), 1);
        Assert.assertTrue(securityRequirement.containsKey(KillBillApiDefinition.BASIC_AUTH_SCHEME));
    }

    @Test(groups = "fast")
    public void testScannerIncludesApiDefinition() {
        final KillBillApiScanner scanner = new KillBillApiScanner();
        final SwaggerConfiguration swaggerConfig = new SwaggerConfiguration()
                .resourcePackages(Set.of("org.killbill.billing.jaxrs.resources"));
        scanner.setConfiguration(swaggerConfig);

        Assert.assertTrue(scanner.classes().contains(KillBillApiDefinition.class));
    }
}
