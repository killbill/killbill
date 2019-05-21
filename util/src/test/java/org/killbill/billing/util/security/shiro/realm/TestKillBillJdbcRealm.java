/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.security.shiro.realm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.DelegatingSubject;
import org.apache.shiro.util.ThreadContext;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TestKillBillJdbcRealm extends UtilTestSuiteWithEmbeddedDB {

    private SecurityManager securityManager;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        securityManager = new DefaultSecurityManager(realms);
        SecurityUtils.setSecurityManager(securityManager);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.afterMethod();
        ThreadContext.unbindSecurityManager();
    }

    @Test(groups = "slow")
    public void testAuthentication() throws SecurityApiException {
        final String username = "toto";
        final String password = "supperCompli43cated";

        securityApi.addRoleDefinition("root", ImmutableList.of("*"), callContext);
        securityApi.addUserRoles(username, password, ImmutableList.of("root"), callContext);
        final DelegatingSubject subject = new DelegatingSubject(securityManager);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);
        securityManager.login(subject, goodToken);
        Assert.assertTrue(true);

        try {
            final AuthenticationToken badToken = new UsernamePasswordToken(username, "somethingelse");
            securityManager.login(subject, badToken);
            Assert.assertTrue(true);
            securityManager.logout(subject);
            securityManager.login(subject, badToken);
            Assert.fail("Should not succeed to login with an incorrect password");
        } catch (final AuthenticationException e) {
        }

        // Update password and try again
        final String newPassword = "suppersimple";
        securityApi.updateUserPassword(username, newPassword, callContext);

        try {
            final AuthenticationToken notGoodTokenAnyLonger = goodToken;
            securityManager.login(subject, notGoodTokenAnyLonger);
            Assert.fail("Should not succeed to login with an incorrect password");
        } catch (final AuthenticationException e) {
        }

        final AuthenticationToken newGoodToken = new UsernamePasswordToken(username, newPassword);
        securityManager.login(subject, newGoodToken);
        Assert.assertTrue(true);

        securityManager.logout(subject);
        securityApi.invalidateUser(username, callContext);

        try {
            final AuthenticationToken notGoodTokenAnyLonger = goodToken;
            securityManager.login(subject, notGoodTokenAnyLonger);
            Assert.fail("Should not succeed to login with an incorrect password");
        } catch (final AuthenticationException e) {
        }

    }

    @Test(groups = "slow")
    public void testEmptyPermissions() throws SecurityApiException {
        securityApi.addRoleDefinition("sanity1", null, callContext);
        validateUserRoles(securityApi.getRoleDefinition("sanity1", callContext), ImmutableList.<String>of());

        securityApi.addRoleDefinition("sanity2", ImmutableList.<String>of(), callContext);
        validateUserRoles(securityApi.getRoleDefinition("sanity2", callContext), ImmutableList.<String>of());
    }

    @Test(groups = "slow")
    public void testInvalidPermissions() {
        testInvalidPermissionScenario(ImmutableList.of("account:credit:vvvv"));
    }

    @Test(groups = "slow")
    public void testSanityOfPermissions() throws SecurityApiException {
        securityApi.addRoleDefinition("sanity1", ImmutableList.of("account:*", "*"), callContext);
        validateUserRoles(securityApi.getRoleDefinition("sanity1", callContext), ImmutableList.of("*"));

        securityApi.addRoleDefinition("sanity2", ImmutableList.of("account:charge", "account:charge"), callContext);
        validateUserRoles(securityApi.getRoleDefinition("sanity2", callContext), ImmutableList.of("account:charge"));

        securityApi.addRoleDefinition("sanity3", ImmutableList.of("account:charge", "account:credit", "account:*", "invoice:*"), callContext);
        validateUserRoles(securityApi.getRoleDefinition("sanity3", callContext), ImmutableList.of("account:*", "invoice:*"));
    }

    @Test(groups = "slow")
    public void testCustomPermissionsAcrossRealms() throws Exception {
        final String role = "writer_off";
        final ImmutableList<String> rolePermissions = ImmutableList.<String>of(Permission.INVOICE_CAN_DELETE_CBA.toString(), /* Built-in permission */
                                                                               "invoice:write_off" /* Built-in group but custom value */,
                                                                               "acme:kb_dev" /* Custom group and value */);

        securityApi.addRoleDefinition(role, rolePermissions, callContext);
        validateUserRoles(securityApi.getRoleDefinition(role, callContext), rolePermissions);

        final List<String> roleDefinitions = securityApi.getRoleDefinition(role, callContext);
        Assert.assertEqualsNoOrder(roleDefinitions.toArray(), rolePermissions.toArray());

        final String username = "tester";
        final String password = "tester";
        securityApi.addUserRoles(username, password, ImmutableList.<String>of(role), callContext);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);
        final Subject subject = securityManager.login(null, goodToken);
        try {
            ThreadContext.bind(subject);

            // JDBC Realm
            subject.checkPermission(Permission.INVOICE_CAN_DELETE_CBA.toString());
            subject.checkPermission("invoice:write_off");
            subject.checkPermission("acme:kb_dev");
            // Shiro Realm
            subject.checkPermission("invoice:credit");
            subject.checkPermission("customx:customy");
            try {
                subject.checkPermission("acme:kb_deployer");
                Assert.fail("Subject should not have rights to deploy Kill Bill");
            } catch (final AuthorizationException e) {
            }

            final Set<String> permissions = securityApi.getCurrentUserPermissions(callContext);
            final Set<String> expectedPermissions = new HashSet<String>(rolePermissions);
            expectedPermissions.add("invoice:credit");
            expectedPermissions.add("customx:customy");
            Assert.assertEquals(permissions, expectedPermissions);
        } finally {
            ThreadContext.unbindSubject();
            subject.logout();
        }
    }

    @Test(groups = "slow")
    public void testAuthorization() throws SecurityApiException {
        final String username = "i like";
        final String password = "c0ff33";

        securityApi.addRoleDefinition("restricted", ImmutableList.of("account:*", "invoice", "tag:create_tag_definition"), callContext);
        securityApi.addUserRoles(username, password, ImmutableList.of("restricted"), callContext);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);
        final Subject subject = securityManager.login(null, goodToken);

        subject.checkPermission(Permission.ACCOUNT_CAN_CHARGE.toString());
        subject.checkPermission(Permission.INVOICE_CAN_CREDIT.toString());
        subject.checkPermission(Permission.TAG_CAN_CREATE_TAG_DEFINITION.toString());

        try {
            subject.checkPermission(Permission.TAG_CAN_DELETE_TAG_DEFINITION.toString());
            Assert.fail("Subject should not have rights to delete tag definitions");
        } catch (AuthorizationException e) {
        }
        subject.logout();

        securityApi.addRoleDefinition("newRestricted", ImmutableList.of("account:*", "invoice", "tag:delete_tag_definition"), callContext);
        securityApi.updateUserRoles(username, ImmutableList.of("newRestricted"), callContext);

        final Subject newSubject = securityManager.login(null, goodToken);
        newSubject.checkPermission(Permission.ACCOUNT_CAN_CHARGE.toString());
        newSubject.checkPermission(Permission.INVOICE_CAN_CREDIT.toString());
        newSubject.checkPermission(Permission.TAG_CAN_DELETE_TAG_DEFINITION.toString());

        try {
            newSubject.checkPermission(Permission.TAG_CAN_CREATE_TAG_DEFINITION.toString());
            Assert.fail("Subject should not have rights to create tag definitions");
        } catch (AuthorizationException e) {
        }
    }

    @Test(groups = "slow", description = "Check * behavior with custom permissions")
    public void testAuthorizationV2() throws SecurityApiException {
        securityApi.addRoleDefinition("for another user", ImmutableList.of("acme:kb_dev"), callContext);

        final String username = "i like";
        final String password = "c0ff33";
        final String role = "for this user";
        final ImmutableList<String> rolePermissions = ImmutableList.of("*");
        securityApi.addRoleDefinition(role, rolePermissions, callContext);
        securityApi.addUserRoles(username, password, ImmutableList.of(role), callContext);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);
        final Subject subject = securityManager.login(null, goodToken);
        try {
            ThreadContext.bind(subject);
            subject.checkPermission(Permission.ACCOUNT_CAN_CHARGE.toString());
            subject.checkPermission(Permission.INVOICE_CAN_CREDIT.toString());
            subject.checkPermission(Permission.TAG_CAN_CREATE_TAG_DEFINITION.toString());
            subject.checkPermission("acme:kb_dev");

            // "*" is not expanded
            final List<String> roleDefinitions = securityApi.getRoleDefinition(role, callContext);
            Assert.assertEqualsNoOrder(roleDefinitions.toArray(), rolePermissions.toArray());

            // "*" is not expanded
            final Set<String> permissions = securityApi.getCurrentUserPermissions(callContext);
            Assert.assertEquals(permissions, ImmutableList.<String>of("*"));

            securityApi.addRoleDefinition("for yet another user", ImmutableList.of("acme:kb_deployer"), callContext);

            // "*" is not expanded
            final List<String> roleDefinitions2 = securityApi.getRoleDefinition(role, callContext);
            Assert.assertEqualsNoOrder(roleDefinitions2.toArray(), rolePermissions.toArray());

            // "*" is not expanded
            final Set<String> permissions2 = securityApi.getCurrentUserPermissions(callContext);
            Assert.assertEquals(permissions2, ImmutableList.<String>of("*"));
        } finally {
            ThreadContext.unbindSubject();
            subject.logout();
        }
    }

    @Test(groups = "slow", description = "Check group:* behavior with custom permissions")
    public void testAuthorizationV3() throws SecurityApiException {
        securityApi.addRoleDefinition("for another user", ImmutableList.of("acme:kb_dev"), callContext);

        final String username = "i like";
        final String password = "c0ff33";
        final String role = "for this user";
        securityApi.addRoleDefinition(role, ImmutableList.of("account", "invoice:*", "tag:create_tag_definition", "acme:*"), callContext);
        securityApi.addUserRoles(username, password, ImmutableList.of(role), callContext);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);
        final Subject subject = securityManager.login(null, goodToken);
        try {
            ThreadContext.bind(subject);

            subject.checkPermission(Permission.ACCOUNT_CAN_CHARGE.toString());
            subject.checkPermission(Permission.INVOICE_CAN_CREDIT.toString());
            subject.checkPermission(Permission.TAG_CAN_CREATE_TAG_DEFINITION.toString());
            subject.checkPermission("acme:kb_dev");

            final Object[] rolePermissions = ImmutableList.of("account:*", "invoice:*", "tag:create_tag_definition", "acme:*").toArray();

            // "*" is not expanded
            final List<String> roleDefinitions = securityApi.getRoleDefinition(role, callContext);
            Assert.assertEqualsNoOrder(roleDefinitions.toArray(), rolePermissions);

            // "*" is not expanded
            final Set<String> permissions = securityApi.getCurrentUserPermissions(callContext);
            Assert.assertEqualsNoOrder(permissions.toArray(), rolePermissions);

            securityApi.addRoleDefinition("for yet another user", ImmutableList.of("acme:kb_deployer"), callContext);

            // "*" is not expanded
            final List<String> roleDefinitions2 = securityApi.getRoleDefinition(role, callContext);
            Assert.assertEqualsNoOrder(roleDefinitions2.toArray(), rolePermissions);

            // "*" is not expanded
            final Set<String> permissions2 = securityApi.getCurrentUserPermissions(callContext);
            Assert.assertEqualsNoOrder(permissions2.toArray(), rolePermissions);
        } finally {
            ThreadContext.unbindSubject();
            subject.logout();
        }
    }

    @Test(groups = "slow")
    public void testUpdateRoleDefinition() throws SecurityApiException {
        final String username = "siskiyou";
        final String password = "siskiyou33";

        securityApi.addRoleDefinition("original", ImmutableList.of("account:*", "invoice", "tag:create_tag_definition"), callContext);
        securityApi.addUserRoles(username, password, ImmutableList.of("restricted"), callContext);

        final AuthenticationToken goodToken = new UsernamePasswordToken(username, password);

        final List<String> roleDefinition = securityApi.getRoleDefinition("original", callContext);
        Assert.assertEquals(roleDefinition.size(), 3);
        Assert.assertTrue(roleDefinition.contains("account:*"));
        Assert.assertTrue(roleDefinition.contains("invoice:*"));
        Assert.assertTrue(roleDefinition.contains("tag:create_tag_definition"));

        securityApi.updateRoleDefinition("original", ImmutableList.of("account:*", "payment", "tag:create_tag_definition", "entitlement:create"), callContext);

        final List<String> updatedRoleDefinition = securityApi.getRoleDefinition("original", callContext);
        Assert.assertEquals(updatedRoleDefinition.size(), 4);
        Assert.assertTrue(updatedRoleDefinition.contains("account:*"));
        Assert.assertTrue(updatedRoleDefinition.contains("payment:*"));
        Assert.assertTrue(updatedRoleDefinition.contains("tag:create_tag_definition"));
        Assert.assertTrue(updatedRoleDefinition.contains("entitlement:create"));

        securityApi.updateRoleDefinition("original", ImmutableList.<String>of(), callContext);
        Assert.assertEquals(securityApi.getRoleDefinition("original", callContext).size(), 0);
    }

    private void testInvalidPermissionScenario(final List<String> permissions) {
        try {
            securityApi.addRoleDefinition("failed", permissions, callContext);
            Assert.fail("Should fail permissions " + permissions + " were invalid");
        } catch (SecurityApiException expected) {
            Assert.assertEquals(expected.getCode(), ErrorCode.SECURITY_INVALID_PERMISSIONS.getCode());
        }
    }

    private void validateUserRoles(final List<String> roles, final List<String> expectedRoles) {
        Assert.assertEquals(roles.size(), expectedRoles.size());
        for (final String cur : expectedRoles) {
            boolean found = false;
            if (Iterables.tryFind(roles, new Predicate<String>() {
                @Override
                public boolean apply(final String input) {
                    return input.equals(cur);
                }
            }).orNull() != null) {
                found = true;
            }
            Assert.assertTrue(found, "Cannot find role " + cur);
        }
    }
}
