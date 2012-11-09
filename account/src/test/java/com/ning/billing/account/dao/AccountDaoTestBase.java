/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.account.dao;

import java.io.IOException;

import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.BeforeClass;

import com.ning.billing.account.AccountTestSuiteWithEmbeddedDB;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.InMemoryInternalBus;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import static org.testng.Assert.fail;

public abstract class AccountDaoTestBase extends AccountTestSuiteWithEmbeddedDB {

    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();

    protected AccountDao accountDao;
    protected AccountEmailDao accountEmailDao;
    protected IDBI dbi;
    protected InternalBus bus;

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            dbi = helper.getDBI();

            bus = new InMemoryInternalBus();
            final BusService busService = new DefaultBusService(bus);
            ((DefaultBusService) busService).startBus();

            accountDao = new DefaultAccountDao(dbi, bus, new InternalCallContextFactory(dbi, new ClockMock()));
            // Health check test to make sure MySQL is setup properly
            accountDao.test(internalCallContext);

            accountEmailDao = new DefaultAccountEmailDao(dbi);
            // Health check test to make sure MySQL is setup properly
            accountEmailDao.test(internalCallContext);
        } catch (Throwable t) {
            fail(t.toString());
        }
    }
}
