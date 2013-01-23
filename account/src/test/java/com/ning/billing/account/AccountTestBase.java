/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.account;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;

import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.DefaultBillCycleDay;
import com.ning.billing.account.api.DefaultMutableAccountData;
import com.ning.billing.account.api.user.DefaultAccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountModelDao;
import com.ning.billing.account.dao.DefaultAccountDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.audit.dao.DefaultAuditDao;
import com.ning.billing.util.bus.DefaultBusService;
import com.ning.billing.util.bus.InMemoryInternalBus;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.customfield.dao.DefaultCustomFieldDao;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.svcsapi.bus.BusService;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.api.user.TagEventBuilder;
import com.ning.billing.util.tag.dao.DefaultTagDao;
import com.ning.billing.util.tag.dao.DefaultTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import static org.testng.Assert.fail;

public abstract class AccountTestBase extends AccountTestSuiteWithEmbeddedDB {

    protected final TagEventBuilder tagEventBuilder = new TagEventBuilder();
    protected final Clock clock = new ClockMock();
    protected final InternalBus bus = new InMemoryInternalBus();

    protected AccountDao accountDao;
    protected AuditDao auditDao;
    protected CustomFieldDao customFieldDao;
    protected TagDefinitionDao tagDefinitionDao;
    protected TagDao tagDao;
    protected CacheControllerDispatcher controllerDispatcher;
    protected NonEntityDao nonEntityDao;

    protected AccountUserApi accountUserApi;

    @BeforeClass(groups = "slow")
    protected void setup() throws IOException {
        try {
            final IDBI dbi = getDBI();

            controllerDispatcher = new CacheControllerDispatcher();
            nonEntityDao = new DefaultNonEntityDao(dbi);
            final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(clock, nonEntityDao, controllerDispatcher);
            accountDao = new DefaultAccountDao(dbi, bus, clock, controllerDispatcher, internalCallContextFactory, nonEntityDao);
            auditDao = new DefaultAuditDao(dbi);
            customFieldDao = new DefaultCustomFieldDao(dbi, clock, controllerDispatcher, nonEntityDao);
            tagDefinitionDao = new DefaultTagDefinitionDao(dbi, tagEventBuilder, bus, clock, controllerDispatcher, nonEntityDao);

            tagDao = new DefaultTagDao(dbi, tagEventBuilder, bus, clock, controllerDispatcher, nonEntityDao);

            // Health check test to make sure MySQL is setup properly
            accountDao.test(internalCallContext);

            final BusService busService = new DefaultBusService(bus);
            ((DefaultBusService) busService).startBus();

            final DefaultCallContextFactory callContextFactory = new DefaultCallContextFactory(clock);
            accountUserApi = new DefaultAccountUserApi(callContextFactory, internalCallContextFactory, accountDao);
        } catch (Throwable t) {
            fail(t.toString());
        }
    }

    protected void checkAccountsEqual(final AccountData retrievedAccount, final AccountData account) {
        final UUID fakeId = UUID.randomUUID();
        checkAccountsEqual(new AccountModelDao(fakeId, retrievedAccount), new AccountModelDao(fakeId, account));
    }

    protected void checkAccountsEqual(final AccountModelDao retrievedAccount, final AccountModelDao account) {
        if (retrievedAccount == null || account == null) {
            Assert.assertNull(retrievedAccount);
            Assert.assertNull(account);
            return;
        }

        // Check all fields but createdDate/updatedDate (comes from the context)
        Assert.assertEquals(retrievedAccount.getId(), account.getId());
        Assert.assertEquals(retrievedAccount.getExternalKey(), account.getExternalKey());
        Assert.assertEquals(retrievedAccount.getEmail(), account.getEmail());
        Assert.assertEquals(retrievedAccount.getName(), account.getName());
        Assert.assertEquals(retrievedAccount.getFirstNameLength(), account.getFirstNameLength());
        Assert.assertEquals(retrievedAccount.getCurrency(), account.getCurrency());
        Assert.assertEquals(retrievedAccount.getBillingCycleDayLocal(), account.getBillingCycleDayLocal());
        Assert.assertEquals(retrievedAccount.getBillingCycleDayUtc(), account.getBillingCycleDayUtc());
        Assert.assertEquals(retrievedAccount.getPaymentMethodId(), account.getPaymentMethodId());
        Assert.assertEquals(retrievedAccount.getTimeZone(), account.getTimeZone());
        Assert.assertEquals(retrievedAccount.getLocale(), account.getLocale());
        Assert.assertEquals(retrievedAccount.getAddress1(), account.getAddress1());
        Assert.assertEquals(retrievedAccount.getAddress2(), account.getAddress2());
        Assert.assertEquals(retrievedAccount.getCompanyName(), account.getCompanyName());
        Assert.assertEquals(retrievedAccount.getCity(), account.getCity());
        Assert.assertEquals(retrievedAccount.getStateOrProvince(), account.getStateOrProvince());
        Assert.assertEquals(retrievedAccount.getCountry(), account.getCountry());
        Assert.assertEquals(retrievedAccount.getPostalCode(), account.getPostalCode());
        Assert.assertEquals(retrievedAccount.getPhone(), account.getPhone());
        Assert.assertEquals(retrievedAccount.getIsNotifiedForInvoices(), account.getIsNotifiedForInvoices());
        Assert.assertEquals(retrievedAccount.getMigrated(), account.getMigrated());
    }

    protected AccountModelDao createTestAccount() {
        return createTestAccount(30, 31, UUID.randomUUID().toString().substring(0, 4));
    }

    protected AccountModelDao createTestAccount(final String phone) {
        return createTestAccount(30, 31, phone);
    }

    protected AccountModelDao createTestAccount(final int billCycleDay) {
        return createTestAccount(billCycleDay, billCycleDay, UUID.randomUUID().toString().substring(0, 4));
    }

    private AccountModelDao createTestAccount(final int billCycleDayUTC, final int billCycleDayLocal, final String phone) {
        final AccountData accountData = createAccountData(billCycleDayUTC, billCycleDayLocal, phone);
        return new AccountModelDao(UUID.randomUUID(), accountData);
    }

    protected AccountData createAccountData() {
        return createAccountData(30, 31, UUID.randomUUID().toString().substring(0, 4));
    }

    private AccountData createAccountData(final int billCycleDayUTC, final int billCycleDayLocal, final String phone) {

        final String externalKey = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString().substring(0, 4) + '@' + UUID.randomUUID().toString().substring(0, 4);
        final String name = UUID.randomUUID().toString();
        final String locale = Locale.GERMANY.toString();
        final DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        final int firstNameLength = name.length();
        final Currency currency = Currency.MXN;
        final BillCycleDay billCycleDay = new DefaultBillCycleDay(billCycleDayLocal, billCycleDayUTC);
        final UUID paymentMethodId = UUID.randomUUID();
        final String address1 = UUID.randomUUID().toString();
        final String address2 = UUID.randomUUID().toString();
        final String companyName = UUID.randomUUID().toString();
        final String city = UUID.randomUUID().toString();
        final String stateOrProvince = UUID.randomUUID().toString();
        final String country = Locale.GERMANY.getCountry();
        final String postalCode = UUID.randomUUID().toString().substring(0, 4);

        return new DefaultMutableAccountData(externalKey, email, name, firstNameLength, currency,
                                             billCycleDay, paymentMethodId, timeZone,
                                             locale, address1, address2, companyName, city, stateOrProvince,
                                             country, postalCode, phone, false, true);
    }
}
