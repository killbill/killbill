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

package com.ning.billing.tenant;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.tenant.dao.DefaultTenantDao;
import com.ning.billing.tenant.glue.TestTenantModuleWithEmbeddedDB;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class TenantTestSuiteWithEmbeddedDb extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected DefaultTenantDao tenantDao;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestTenantModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() {
    }
}
