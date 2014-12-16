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

package org.killbill.billing.overdue.glue;

import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockEntitlementModule;
import org.killbill.billing.mock.glue.MockInvoiceModule;
import org.killbill.billing.mock.glue.MockTagModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.overdue.TestOverdueHelper;
import org.killbill.billing.overdue.applicator.OverdueBusListenerTester;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.email.EmailModule;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.CustomFieldModule;

public class TestOverdueModule extends DefaultOverdueModule {

    public TestOverdueModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new AuditModule(configSource));
        install(new CacheModule(configSource));
        install(new CallContextModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new EmailModule(configSource));
        install(new MockAccountModule(configSource));
        install(new MockEntitlementModule(configSource));
        install(new MockInvoiceModule(configSource));
        install(new MockTagModule(configSource));
        install(new TemplateModule(configSource));
        install(new MockTenantModule(configSource));


        // We can't use the dumb mocks in MockJunctionModule here
        install(new ApplicatorMockJunctionModule(configSource));

        bind(OverdueBusListenerTester.class).asEagerSingleton();
        bind(TestOverdueHelper.class).asEagerSingleton();
    }
}
