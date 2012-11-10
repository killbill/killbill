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
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.BeforeClass;

import com.ning.billing.account.AccountTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.InMemoryInternalBus;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.api.user.TagEventBuilder;

import static org.testng.Assert.fail;

public abstract class AccountDaoTestBase extends AccountTestSuiteWithEmbeddedDB {

    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();
    protected final Clock clock = new ClockMock();

    protected AccountDao accountDao;
    protected AccountEmailDao accountEmailDao;
    protected InternalCallContextFactory internalCallContextFactory;
    protected IDBI dbi;
    protected InternalBus bus;

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            dbi = helper.getDBI();

            bus = new InMemoryInternalBus();
            final BusService busService = new DefaultBusService(bus);
            ((DefaultBusService) busService).startBus();

            internalCallContextFactory = new InternalCallContextFactory(dbi, clock);
            accountDao = new DefaultAccountDao(dbi, bus, internalCallContextFactory);
            // Health check test to make sure MySQL is setup properly
            accountDao.test(internalCallContext);

            accountEmailDao = new DefaultAccountEmailDao(dbi);
            // Health check test to make sure MySQL is setup properly
            accountEmailDao.test(internalCallContext);
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    protected AccountModelDao createTestAccount() {
        return createTestAccount(5, UUID.randomUUID().toString().substring(0, 5));
    }

    protected AccountModelDao createTestAccount(final int billCycleDay) {
        return createTestAccount(billCycleDay, "123-456-7890");
    }

    protected AccountModelDao createTestAccount(final int billCycleDay, final String phone) {
        final String thisKey = "test" + UUID.randomUUID().toString();
        final String lastName = UUID.randomUUID().toString();
        final String thisEmail = "me@me.com" + " " + UUID.randomUUID();
        final String firstName = "Bob";
        final String name = firstName + " " + lastName;
        final String locale = "EN-US";
        final DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        final int firstNameLength = firstName.length();

        return new AccountModelDao(UUID.randomUUID(), null, null, thisKey, thisEmail, name, firstNameLength, Currency.USD,
                                   billCycleDay, billCycleDay, UUID.randomUUID(), timeZone, locale,
                                   null, null, null, null, null, null, null, phone, false, false);
    }
}
