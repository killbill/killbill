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

package com.ning.billing.util.security;

import javax.inject.Singleton;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.Factory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.security.Permission;
import com.ning.billing.security.RequiresPermissions;
import com.ning.billing.util.UtilTestSuiteNoDB;
import com.ning.billing.util.glue.SecurityModule;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

public class TestPermissionAnnotationMethodInterceptor extends UtilTestSuiteNoDB {

    public static interface IAopTester {

        @RequiresPermissions(Permission.PAYMENT_CAN_REFUND)
        public void createRefund();
    }

    public static class AopTesterImpl implements IAopTester {

        @Override
        public void createRefund() {}
    }

    @Singleton
    public static class AopTester implements IAopTester {

        @RequiresPermissions(Permission.PAYMENT_CAN_REFUND)
        public void createRefund() {}
    }

    @Test(groups = "fast")
    public void testAOPForClass() throws Exception {
        // Make sure it works as expected without any AOP magic
        final IAopTester simpleTester = new AopTester();
        try {
            simpleTester.createRefund();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }

        // Now, verify the interception works
        configureShiro();
        final Injector injector = Guice.createInjector(Stage.PRODUCTION, new SecurityModule());
        final AopTester aopedTester = injector.getInstance(AopTester.class);
        verifyAopedTester(aopedTester);
    }

    @Test(groups = "fast")
    public void testAOPForInterface() throws Exception {
        // Make sure it works as expected without any AOP magic
        final IAopTester simpleTester = new AopTesterImpl();
        try {
            simpleTester.createRefund();
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }

        // Now, verify the interception works
        configureShiro();
        final Injector injector = Guice.createInjector(Stage.PRODUCTION,
                                                       new SecurityModule(),
                                                       new Module() {
                                                           @Override
                                                           public void configure(final Binder binder) {
                                                               binder.bind(IAopTester.class).to(AopTesterImpl.class).asEagerSingleton();
                                                           }
                                                       });
        final IAopTester aopedTester = injector.getInstance(IAopTester.class);
        verifyAopedTester(aopedTester);
    }

    private void verifyAopedTester(final IAopTester aopedTester) {
        // Anonymous user
        logout();
        try {
            aopedTester.createRefund();
            Assert.fail();
        } catch (UnauthenticatedException e) {
            // Good!
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }

        // pierre can credit, but not refund
        login("pierre");
        try {
            aopedTester.createRefund();
            Assert.fail();
        } catch (AuthorizationException e) {
            // Good!
        } catch (Exception e) {
            Assert.fail(e.getLocalizedMessage());
        }

        // stephane can refund
        login("stephane");
        aopedTester.createRefund();
    }
}
