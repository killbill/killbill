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

package org.killbill.billing.util;

import javax.inject.Inject;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.util.Factory;
import org.apache.shiro.util.ThreadContext;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.glue.TestUtilModuleNoDB;
import org.killbill.billing.util.security.shiro.realm.KillBillJndiLdapRealm;
import org.killbill.bus.api.PersistentBus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class UtilTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;
    @Inject
    protected AuditDao auditDao;
    @Inject
    protected AuditUserApi auditUserApi;
    @Inject
    protected SecurityApi securityApi;
    @Inject
    protected KillBillJndiLdapRealm killBillJndiLdapRealm;
    @Inject
    protected AccountUserApi accountUserApi;
    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected ImmutableAccountInternalApi immutableAccountInternalApi;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleNoDB(configSource));
        g.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        eventBus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        eventBus.stop();
        // Reset the security manager
        ThreadContext.unbindSecurityManager();
    }

    // Security helpers
    protected void login(final String username) {
        securityApi.login(username, "password");
    }

    protected void logout() {
        securityApi.logout();
    }

    protected void configureShiro() {
        final Ini config = new Ini();
        config.addSection("users");
        config.getSection("users").put("pierre", "password, creditor");
        config.getSection("users").put("stephane", "password, refunder");
        config.addSection("roles");
        config.getSection("roles").put("creditor", Permission.INVOICE_CAN_CREDIT.toString() + "," + Permission.INVOICE_CAN_ITEM_ADJUST.toString());
        config.getSection("roles").put("refunder", Permission.PAYMENT_CAN_REFUND.toString());

        // Reset the security manager
        ThreadContext.unbindSecurityManager();

        final Factory<SecurityManager> factory = new IniSecurityManagerFactory(config);
        final SecurityManager securityManager = factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
    }
}
