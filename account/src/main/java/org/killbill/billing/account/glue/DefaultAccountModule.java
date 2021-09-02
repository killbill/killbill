/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.account.glue;

import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountService;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.DefaultAccountService;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.account.api.svcs.DefaultAccountInternalApi;
import org.killbill.billing.account.api.svcs.DefaultImmutableAccountInternalApi;
import org.killbill.billing.account.api.user.DefaultAccountUserApi;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.dao.DefaultAccountDao;
import org.killbill.billing.glue.AccountModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.audit.dao.DefaultAuditDao;
import org.killbill.billing.util.features.KillbillFeatures;
import org.killbill.billing.util.glue.KillBillModule;

public class DefaultAccountModule extends KillBillModule implements AccountModule {

    public DefaultAccountModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    public DefaultAccountModule(final KillbillConfigSource configSource, final KillbillFeatures killbillFeatures) {
        super(configSource, killbillFeatures);
    }

    private void installConfig() {
    }

    protected void installAccountDao() {
        bind(AccountDao.class).to(DefaultAccountDao.class).asEagerSingleton();
    }

    @Override
    public void installAccountUserApi() {
        bind(AccountUserApi.class).to(DefaultAccountUserApi.class).asEagerSingleton();
    }

    @Override
    public void installInternalApi() {
        bind(AccountInternalApi.class).to(DefaultAccountInternalApi.class).asEagerSingleton();
        bind(ImmutableAccountInternalApi.class).to(DefaultImmutableAccountInternalApi.class).asEagerSingleton();
    }

    private void installAccountService() {
        bind(AccountService.class).to(DefaultAccountService.class).asEagerSingleton();
    }

    private void installKillBillFeatures() {
        bind(KillbillFeatures.class).toInstance(killbillFeatures);
    }

    @Override
    protected void configure() {
        installConfig();
        installAccountDao();
        installAccountService();
        installAccountUserApi();
        installInternalApi();
        installKillBillFeatures();
    }
}
