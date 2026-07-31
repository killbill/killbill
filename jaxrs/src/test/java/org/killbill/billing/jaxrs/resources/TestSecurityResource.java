/*
 * Copyright 2014-2026 The Billing Project, LLC
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.RoleDefinitionJson;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.security.api.SecurityApi;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestSecurityResource extends JaxrsTestSuiteNoDB {

    private HttpServletRequest servletRequest;
    private SecurityApi securityApi;
    private Context context;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        if (hasFailed()) {
            return;
        }
        servletRequest = mock(HttpServletRequest.class);
        securityApi = mock(SecurityApi.class);
        context = mock(Context.class);
    }

    private SecurityResource createSecurityResource() {
        return new SecurityResource(
                securityApi,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                context
        );
    }

    @Test(groups = "fast")
    public void testGetAvailableRolesReturnsSortedByRoleName() {
        // Use a LinkedHashMap with intentionally unsorted insertion order
        final Map<String, List<String>> unsortedRoles = new LinkedHashMap<>();
        unsortedRoles.put("supervisor_role", List.of("permission:read"));
        unsortedRoles.put("admin_role", List.of("permission:all"));
        unsortedRoles.put("manager_role", List.of("permission:write", "permission:read"));

        when(securityApi.getAvailableRoles(any())).thenReturn(unsortedRoles);

        final SecurityResource resource = createSecurityResource();
        final Response response = resource.getAvailableRoles(servletRequest);

        Assert.assertEquals(response.getStatus(), 200);

        @SuppressWarnings("unchecked")
        final List<RoleDefinitionJson> result = (List<RoleDefinitionJson>) response.getEntity();
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get(0).getRole(), "admin_role");
        Assert.assertEquals(result.get(1).getRole(), "manager_role");
        Assert.assertEquals(result.get(2).getRole(), "supervisor_role");
    }

    @Test(groups = "fast")
    public void testGetAvailableRolesReturnsSortedRegardlessOfMapType() {
        // Use a HashMap which has no guaranteed iteration order
        final Map<String, List<String>> roles = new HashMap<>();
        roles.put("delta", List.of("perm:d"));
        roles.put("alpha", List.of("perm:a"));
        roles.put("charlie", List.of("perm:c"));
        roles.put("bravo", List.of("perm:b"));

        when(securityApi.getAvailableRoles(any())).thenReturn(roles);

        final SecurityResource resource = createSecurityResource();
        final Response response = resource.getAvailableRoles(servletRequest);

        @SuppressWarnings("unchecked")
        final List<RoleDefinitionJson> result = (List<RoleDefinitionJson>) response.getEntity();
        Assert.assertEquals(result.size(), 4);
        Assert.assertEquals(result.get(0).getRole(), "alpha");
        Assert.assertEquals(result.get(1).getRole(), "bravo");
        Assert.assertEquals(result.get(2).getRole(), "charlie");
        Assert.assertEquals(result.get(3).getRole(), "delta");
    }
}
