/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockEntitlementModule;
import org.killbill.billing.mock.glue.MockInvoiceModule;
import org.killbill.billing.mock.glue.MockTagModule;
import org.killbill.billing.mock.glue.MockTenantModule;
import org.killbill.billing.overdue.TestOverdueHelper;
import org.killbill.billing.overdue.applicator.OverdueBusListenerTester;
import org.killbill.billing.overdue.caching.MockOverdueConfigCache;
import org.killbill.billing.overdue.caching.OverdueCacheInvalidationCallback;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.TenantInternalApi.CacheInvalidationCallback;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.MemoryGlobalLockerModule;

import com.google.inject.name.Names;

public class TestOverdueModule extends DefaultOverdueModule {

    public TestOverdueModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        super.configure();

        install(new AuditModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new CallContextModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new MockAccountModule(configSource));
        install(new MockEntitlementModule(configSource, new ApplicatorBlockingApi()));
        install(new MockInvoiceModule(configSource));
        install(new MockTagModule(configSource, true));
        install(new TemplateModule(configSource));
        install(new MockTenantModule(configSource));
        install(new MemoryGlobalLockerModule(configSource));

        bind(OverdueBusListenerTester.class).asEagerSingleton();
        bind(TestOverdueHelper.class).asEagerSingleton();
    }

    public void installOverdueConfigCache() {
        bind(OverdueConfigCache.class).to(MockOverdueConfigCache.class).asEagerSingleton();
        bind(CacheInvalidationCallback.class).annotatedWith(Names.named(OVERDUE_INVALIDATION_CALLBACK)).to(OverdueCacheInvalidationCallback.class).asEagerSingleton();
    }

    public static class ApplicatorBlockingApi implements BlockingInternalApi {

        private BlockingState blockingState;

        public BlockingState getBlockingState() {
            return blockingState;
        }


        @Override
        public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
            if (blockingState != null && blockingState.getBlockedId().equals(blockableId)) {
                return blockingState;
            } else {
                return new DefaultBlockingState(null, blockingStateType, OverdueWrapper.CLEAR_STATE_NAME, serviceName, false, false, false, null);
            }
        }

        @Override
        public List<BlockingState> getBlockingAllForAccount(final Catalog catalog, final InternalTenantContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBlockingState(final BlockingState state, final InternalCallContext context) {
            blockingState = state;
        }
    }
}
