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
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import static org.testng.Assert.fail;

public abstract class AccountDaoTestBase extends AccountTestSuiteWithEmbeddedDB {
    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();

    protected AccountDao accountDao;
    protected AccountEmailDao accountEmailDao;
    protected IDBI dbi;
    protected Bus bus;
    protected CallContext context;

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            dbi = helper.getDBI();

            bus = new InMemoryBus();
            final BusService busService = new DefaultBusService(bus);
            ((DefaultBusService) busService).startBus();

            accountDao = new AuditedAccountDao(dbi, bus);
            // Health check test to make sure MySQL is setup properly
            accountDao.test();

            accountEmailDao = new AuditedAccountEmailDao(dbi);
            // Health check test to make sure MySQL is setup properly
            accountEmailDao.test();

            final Clock clock = new ClockMock();
            context = new DefaultCallContextFactory(clock).createCallContext("Account Dao Tests", CallOrigin.TEST, UserType.TEST);
        } catch (Throwable t) {
            fail(t.toString());
        }
    }
}
