/*
 * Copyright 2010-2011 Ning, Inc
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

package com.ning.billing.entitlement.glue;


import com.ning.billing.entitlement.engine.core.ApiEventProcessorMemoryMock;
import com.ning.billing.entitlement.engine.core.IApiEventProcessor;
import com.ning.billing.entitlement.engine.dao.EntitlementDaoMemoryMock;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.clock.IClock;


public class EngineModuleMemoryMock extends EntitlementModule {
    @Override
    protected void installApiEventProcessor() {
        bind(IApiEventProcessor.class).to(ApiEventProcessorMemoryMock.class).asEagerSingleton();
    }

    @Override
    protected void installEntitlementDao() {
        bind(IEntitlementDao.class).to(EntitlementDaoMemoryMock.class).asEagerSingleton();
    }

    @Override
    protected void installClock() {
        bind(IClock.class).to(ClockMock.class).asEagerSingleton();
    }

    @Override
    protected void installDBI() {
    }
}
