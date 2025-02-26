/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.glue;

import java.io.IOException;
import java.util.Set;

import javax.inject.Singleton;

import org.apache.shiro.config.Ini;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.osgi.api.PluginsInfoApi;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.shiro.realm.KillBillJdbcRealm;
import org.killbill.clock.ClockMock;
import org.killbill.commons.embeddeddb.EmbeddedDB;
import org.mockito.Mockito;

import com.google.inject.Provides;

public class TestUtilModuleWithEmbeddedDB extends TestUtilModule {

    private final ClockMock clock;

    public TestUtilModuleWithEmbeddedDB(final KillbillConfigSource configSource, final ClockMock clock) {
        super(configSource);
        this.clock = clock;
    }

    @Override
    protected void configure() {
        super.configure();
        install(new GuicyKillbillTestWithEmbeddedDBModule(configSource, clock));

        install(new AuditModule(configSource));
        install(new InfoModuleWithPluginInfoApi(configSource));
        install(new BroadcastModule(configSource));
        install(new TagStoreModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new NonEntityDaoModule(configSource));
        install(new SecurityModuleWithNoSecurityManager(configSource));
        install(new ExportModule(configSource));
        bind(TestApiListener.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    protected Set<Realm> provideRealms(final EmbeddedDB embeddedDB, final SecurityConfig securityConfig) throws IOException {
        final Ini ini = new Ini();
        ini.load("[users]\n" +
                 "tester = tester, creditor\n" +
                 "[roles]\n" +
                 "creditor = invoice:credit, customx:customy\n");
        final Realm iniRealm = new IniRealm(ini);
        final Realm killBillJdbcRealm = new KillBillJdbcRealm(embeddedDB.getDataSource(), securityConfig);

        return Set.of(iniRealm, killBillJdbcRealm);
    }

    private final class SecurityModuleWithNoSecurityManager extends SecurityModule {

        public SecurityModuleWithNoSecurityManager(final KillbillConfigSource configSource) {
            super(configSource);
        }

        protected void installSecurityService() {
        }
    }

    private static class InfoModuleWithPluginInfoApi extends NodesModule {

        public InfoModuleWithPluginInfoApi(final KillbillConfigSource configSource) {
            super(configSource);
        }

        protected void installUserApi() {
            bind(PluginsInfoApi.class).toInstance(Mockito.mock(PluginsInfoApi.class));
            super.installUserApi();
        }
    }
}
