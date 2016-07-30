/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Permissions;
import org.killbill.billing.client.model.RoleDefinition;
import org.killbill.billing.client.model.UserRoles;
import org.killbill.billing.security.Permission;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.http.client.Response;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

public class TestSecurity extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testPermissions() throws Exception {
        logout();

        try {
            killBillClient.getPermissions();
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getResponse().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());
        }

        // See src/test/resources/shiro.ini

        final List<String> pierresPermissions = getPermissions("pierre", "password");
        Assert.assertEquals(pierresPermissions.size(), 2);
        Assert.assertEquals(new HashSet<String>(pierresPermissions), ImmutableSet.<String>of(Permission.INVOICE_CAN_CREDIT.toString(), Permission.INVOICE_CAN_ITEM_ADJUST.toString()));

        final List<String> stephanesPermissions = getPermissions("stephane", "password");
        Assert.assertEquals(stephanesPermissions.size(), 1);
        Assert.assertEquals(new HashSet<String>(stephanesPermissions), ImmutableSet.<String>of(Permission.PAYMENT_CAN_REFUND.toString()));
    }

    @Test(groups = "slow")
    public void testDynamicUserRolesAllPermissions() throws Exception {
        testDynamicUserRolesInternal("wqeqwe", "jdsh763s", "all", ImmutableList.of("*"), true);
    }

    @Test(groups = "slow")
    public void testDynamicUserRolesAllCatalogPermissions() throws Exception {
        testDynamicUserRolesInternal("wqeqsdswe", "jsddsh763s", "allcatalog", ImmutableList.of("catalog:*","tenant_kvs:add"), true);
    }

    @Test(groups = "slow")
    public void testDynamicUserRolesCorrectCatalogPermissions() throws Exception {
        testDynamicUserRolesInternal("wqeq23f6we", "jds5gh763s", "correctcatalog", ImmutableList.of("catalog:config_upload","tenant_kvs:add"), true);
    }

    @Test(groups = "slow")
    public void testDynamicUserRolesIncorrectPermissions() throws Exception {
        testDynamicUserRolesInternal("wqsdeqwe", "jd23fsh63s", "incorrect", ImmutableList.of("account:*"), false);
    }

    @Test(groups = "slow")
    public void testDynamicUserRolesNoPermissions() throws Exception {
        final String username = UUID.randomUUID().toString();
        final String password = UUID.randomUUID().toString();
        final String role = UUID.randomUUID().toString();
        testDynamicUserRolesInternal(username, password, role, ImmutableList.of(""), false);

        final Permissions permissions = killBillClient.getPermissions(RequestOptions.builder().withUser(username).withPassword(password).build());
        Assert.assertEquals(permissions.size(), 0);
    }

    @Test(groups = "slow")
    public void testUserPermission() throws KillBillClientException {

        final String roleDefinition = "notEnoughToAddUserAndRoles";

        final List<String> permissions = new ArrayList<String>();
        for (Permission cur : Permission.values()) {
            if (!cur.getGroup().equals("user")) {
                permissions.add(cur.toString());
            }
        }
        Response response = killBillClient.addRoleDefinition(new RoleDefinition(roleDefinition, permissions), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        final String username = "candy";
        final String password = "lolipop";
        response = killBillClient.addUserRoles(new UserRoles(username, password, ImmutableList.of(roleDefinition)), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        // Now 'login' as new user (along with roles to make an API call requiring permissions), and check behavior
        logout();
        login(username, password);

        boolean success = false;
        try {
            killBillClient.addRoleDefinition(new RoleDefinition("dsfdsfds", ImmutableList.of("*")), createdBy, reason, comment);
            success = true;
        } catch (final Exception e) {
        } finally {
            Assert.assertFalse(success);
        }

        success = false;
        try {
            killBillClient.addUserRoles(new UserRoles("sdsd", "sdsdsd", ImmutableList.of(roleDefinition)), createdBy, reason, comment);
            success = true;
        } catch (final Exception e) {
        } finally {
            Assert.assertFalse(success);
        }
    }

    @Test(groups = "slow")
    public void testUserWithUpdates() throws KillBillClientException {

        final String roleDefinition = "somethingNice";
        final String allPermissions = "*";

        final String username = "GuanYu";
        final String password = "IamAGreatWarrior";

        Response response = killBillClient.addRoleDefinition(new RoleDefinition(roleDefinition, ImmutableList.of(allPermissions)), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        response = killBillClient.addUserRoles(new UserRoles(username, password, ImmutableList.of(roleDefinition)), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        logout();
        login(username, password);
        Permissions permissions =  killBillClient.getPermissions();
        Assert.assertEquals(permissions.size(), Permission.values().length);

        String newPassword = "IamTheBestWarrior";
        killBillClient.updateUserPassword(username, newPassword, createdBy, reason, comment);

        logout();
        login(username, newPassword);
        permissions =  killBillClient.getPermissions();
        Assert.assertEquals(permissions.size(), Permission.values().length);

        final String newRoleDefinition = "somethingLessNice";
        // Only enough permissions to invalidate itself in the last step...
        final String littlePermissions = "user";

        response = killBillClient.addRoleDefinition(new RoleDefinition(newRoleDefinition, ImmutableList.of(littlePermissions)), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        killBillClient.updateUserRoles(username, ImmutableList.of(newRoleDefinition), createdBy, reason, comment);
        permissions =  killBillClient.getPermissions();
        // This will only work if correct shiro cache invalidation was performed... requires lots of sweat to get it to work ;-)
        Assert.assertEquals(permissions.size(), 2);

        killBillClient.invalidateUser(username, createdBy, reason, comment);
        try {
            killBillClient.getPermissions();
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getResponse().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());
        }


    }

    private void testDynamicUserRolesInternal(final String username, final String password, final String roleDefinition, final List<String> permissions, final boolean expectPermissionSuccess) throws Exception {

        Response response = killBillClient.addRoleDefinition(new RoleDefinition(roleDefinition, permissions), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        response = killBillClient.addUserRoles(new UserRoles(username, password, ImmutableList.of(roleDefinition)), createdBy, reason, comment);
        Assert.assertEquals(response.getStatusCode(), 201);

        // Now 'login' as new user (along with roles to make an API call requiring permissions), and check behavior
        logout();
        login(username, password);

        boolean success = false;
        try {
            final String catalogPath = Resources.getResource("SpyCarBasic.xml").getPath();
            killBillClient.uploadXMLCatalog(catalogPath, createdBy, reason, comment);
            success = true;
        } catch (final Exception e) {
            if (expectPermissionSuccess ||
                !e.getMessage().startsWith("java.lang.IllegalArgumentException: Unauthorized")) {
                throw e;
            }
        } finally {
            Assert.assertTrue(success == expectPermissionSuccess);
        }
    }

    private List<String> getPermissions(@Nullable final String username, @Nullable final String password) throws Exception {
        login(username, password);
        return killBillClient.getPermissions();
    }
}
