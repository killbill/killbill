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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Handle;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.BillCycleDay;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.account.api.DefaultBillCycleDay;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.customfield.dao.AuditedCustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.AuditedTagDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAccountDao extends AccountDaoTestBase {

    private Account createTestAccount() {
        return createTestAccount(5, UUID.randomUUID().toString().substring(0, 5));
    }

    private Account createTestAccount(final int billCycleDay) {
        return createTestAccount(billCycleDay, "123-456-7890");
    }

    private Account createTestAccount(final int billCycleDay, final String phone) {
        final String thisKey = "test" + UUID.randomUUID().toString();
        final String lastName = UUID.randomUUID().toString();
        final String thisEmail = "me@me.com" + " " + UUID.randomUUID();
        final String firstName = "Bob";
        final String name = firstName + " " + lastName;
        final String locale = "EN-US";
        final DateTimeZone timeZone = DateTimeZone.forID("America/Los_Angeles");
        final int firstNameLength = firstName.length();

        return new DefaultAccount(UUID.randomUUID(), thisKey, thisEmail, name, firstNameLength, Currency.USD,
                                  new DefaultBillCycleDay(billCycleDay, billCycleDay), UUID.randomUUID(), timeZone, locale,
                                  null, null, null, null, null, null, null, // add null address fields
                                  phone, false, false);
    }

    @Test(groups = "slow")
    public void testBasic() throws EntityPersistenceException {
        final Account a = createTestAccount(5);
        accountDao.create(a, context);
        final String key = a.getExternalKey();

        Account r = accountDao.getAccountByKey(key);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        r = accountDao.getById(r.getId());
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        final List<Account> all = accountDao.get();
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }

    // simple test to ensure long phone numbers can be stored
    @Test(groups = "slow")
    public void testLongPhoneNumber() throws EntityPersistenceException {
        final Account account = createTestAccount(1, "123456789012345678901234");
        accountDao.create(account, context);

        final Account saved = accountDao.getAccountByKey(account.getExternalKey());
        assertNotNull(saved);
    }

    // simple test to ensure excessively long phone numbers cannot be stored
    @Test(groups = "slow", expectedExceptions = EntityPersistenceException.class)
    public void testOverlyLongPhoneNumber() throws EntityPersistenceException {
        final Account account = createTestAccount(1, "12345678901234567890123456");
        accountDao.create(account, context);
    }

    @Test(groups = "slow")
    public void testGetById() throws EntityPersistenceException {
        Account account = createTestAccount(1);
        final UUID id = account.getId();
        final String key = account.getExternalKey();
        final String name = account.getName();
        final Integer firstNameLength = account.getFirstNameLength();

        accountDao.create(account, context);

        account = accountDao.getById(id);
        assertNotNull(account);
        assertEquals(account.getId(), id);
        assertEquals(account.getExternalKey(), key);
        assertEquals(account.getName(), name);
        assertEquals(account.getFirstNameLength(), firstNameLength);
    }

    @Test(groups = "slow")
    public void testCustomFields() throws EntityPersistenceException {
        final String fieldName = "testField1";
        final String fieldValue = "testField1_value";

        final UUID accountId = UUID.randomUUID();
        final List<CustomField> customFields = new ArrayList<CustomField>();
        customFields.add(new StringCustomField(fieldName, fieldValue));
        final CustomFieldDao customFieldDao = new AuditedCustomFieldDao(dbi);
        customFieldDao.saveEntities(accountId, ObjectType.ACCOUNT, customFields, context);

        final Map<String, CustomField> customFieldMap = customFieldDao.loadEntities(accountId, ObjectType.ACCOUNT);
        assertEquals(customFieldMap.size(), 1);
        final CustomField customField = customFieldMap.get(fieldName);
        assertEquals(customField.getName(), fieldName);
        assertEquals(customField.getValue(), fieldValue);
    }

    @Test(groups = "slow")
    public void testTags() throws EntityPersistenceException, TagApiException {
        final Account account = createTestAccount(1);
        final TagDefinition definition = new DefaultTagDefinition("Test Tag", "For testing only", false);
        final TagDefinitionSqlDao tagDescriptionDao = dbi.onDemand(TagDefinitionSqlDao.class);
        tagDescriptionDao.create(definition, context);

        final TagDao tagDao = new AuditedTagDao(dbi, tagEventBuilder, bus);
        tagDao.insertTag(account.getId(), ObjectType.ACCOUNT, definition.getId(), context);

        final Map<String, Tag> tagMap = tagDao.loadEntities(account.getId(), ObjectType.ACCOUNT);
        assertEquals(tagMap.size(), 1);

        assertEquals(tagMap.values().iterator().next().getTagDefinitionId(), definition.getId());
    }

    @Test(groups = "slow")
    public void testGetIdFromKey() throws EntityPersistenceException {
        final Account account = createTestAccount(1);
        accountDao.create(account, context);

        try {
            final UUID accountId = accountDao.getIdFromKey(account.getExternalKey());
            assertEquals(accountId, account.getId());
        } catch (AccountApiException a) {
            fail("Retrieving account failed.");
        }
    }

    @Test(groups = "slow", expectedExceptions = AccountApiException.class)
    public void testGetIdFromKeyForNullKey() throws AccountApiException {
        final String key = null;
        accountDao.getIdFromKey(key);
    }

    @Test(groups = "slow")
    public void testUpdate() throws Exception {
        final Account account = createTestAccount(1);
        accountDao.create(account, context);

        final AccountData accountData = new MockAccountBuilder(account).migrated(false)
                                                                       .isNotifiedForInvoices(false)
                                                                       .timeZone(DateTimeZone.forID("Australia/Darwin"))
                                                                       .locale("FR-CA")
                                                                       .build();

        final Account updatedAccount = new DefaultAccount(account.getId(), accountData);
        accountDao.update(updatedAccount, context);

        final Account savedAccount = accountDao.getAccountByKey(account.getExternalKey());

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getName(), updatedAccount.getName());
        assertEquals(savedAccount.getEmail(), updatedAccount.getEmail());
        assertEquals(savedAccount.getPaymentMethodId(), updatedAccount.getPaymentMethodId());
        assertEquals(savedAccount.getBillCycleDay(), updatedAccount.getBillCycleDay());
        assertEquals(savedAccount.getFirstNameLength(), updatedAccount.getFirstNameLength());
        assertEquals(savedAccount.getTimeZone(), updatedAccount.getTimeZone());
        assertEquals(savedAccount.getLocale(), updatedAccount.getLocale());
        assertEquals(savedAccount.getAddress1(), updatedAccount.getAddress1());
        assertEquals(savedAccount.getAddress2(), updatedAccount.getAddress2());
        assertEquals(savedAccount.getCity(), updatedAccount.getCity());
        assertEquals(savedAccount.getStateOrProvince(), updatedAccount.getStateOrProvince());
        assertEquals(savedAccount.getCountry(), updatedAccount.getCountry());
        assertEquals(savedAccount.getPostalCode(), updatedAccount.getPostalCode());
        assertEquals(savedAccount.getPhone(), updatedAccount.getPhone());
    }

    @Test(groups = "slow")
    public void testAddingContactInformation() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final DefaultAccount account = new DefaultAccount(accountId, "extKey123456", "myemail123456@glam.com",
                                                          "John Smith", 4, Currency.USD, new DefaultBillCycleDay(15), null,
                                                          DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                          null, null, null, null, null, null, null, null, false, false);
        accountDao.create(account, context);

        final String address1 = "123 address 1";
        final String address2 = "456 address 2";
        final String companyName = "Some Company";
        final String city = "Cambridge Bay";
        final String stateOrProvince = "Nunavut";
        final String country = "Canada";
        final String postalCode = "X0B 0C0";
        final String phone = "18001112222";

        final DefaultAccount updatedAccount = new DefaultAccount(accountId, "extKey123456", "myemail123456@glam.com",
                                                                 "John Smith", 4, Currency.USD, new DefaultBillCycleDay(15), null,
                                                                 DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                                 address1, address2, companyName, city, stateOrProvince, country,
                                                                 postalCode, phone, false, false);

        accountDao.update(updatedAccount, context);

        final Account savedAccount = accountDao.getById(accountId);

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getId(), accountId);
        assertEquals(savedAccount.getAddress1(), address1);
        assertEquals(savedAccount.getAddress2(), address2);
        assertEquals(savedAccount.getCompanyName(), companyName);
        assertEquals(savedAccount.getCity(), city);
        assertEquals(savedAccount.getStateOrProvince(), stateOrProvince);
        assertEquals(savedAccount.getCity(), city);
        assertEquals(savedAccount.getPostalCode(), postalCode);
        assertEquals(savedAccount.getPhone(), phone);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateExternalKey() throws Exception {
        final Account account = createTestAccount();
        accountDao.create(account, context);

        final MutableAccountData otherAccount = account.toMutableAccountData();
        otherAccount.setExternalKey(UUID.randomUUID().toString());

        accountDao.update(new DefaultAccount(account.getId(), otherAccount), context);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateCurrency() throws Exception {
        final Account account = createTestAccount();
        accountDao.create(account, context);

        final MutableAccountData otherAccount = account.toMutableAccountData();
        otherAccount.setCurrency(Currency.GBP);

        accountDao.update(new DefaultAccount(account.getId(), otherAccount), context);
    }

    @Test(groups = "slow", expectedExceptions = IllegalArgumentException.class)
    public void testShouldntBeAbleToUpdateBillCycleDay() throws Exception {
        final Account account = createTestAccount();
        accountDao.create(account, context);

        final MutableAccountData otherAccount = account.toMutableAccountData();
        otherAccount.setBillCycleDay(new BillCycleDay() {
            @Override
            public int getDayOfMonthUTC() {
                return account.getBillCycleDay().getDayOfMonthUTC() + 2;
            }

            @Override
            public int getDayOfMonthLocal() {
                return account.getBillCycleDay().getDayOfMonthLocal() + 2;
            }
        });

        accountDao.update(new DefaultAccount(account.getId(), otherAccount), context);
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToUpdateSomeFields() throws Exception {
        final Account account = createTestAccount();
        accountDao.create(account, context);

        final MutableAccountData otherAccount = account.toMutableAccountData();
        otherAccount.setAddress1(UUID.randomUUID().toString());
        otherAccount.setEmail(UUID.randomUUID().toString());

        final DefaultAccount newAccount = new DefaultAccount(account.getId(), otherAccount);
        accountDao.update(newAccount, context);

        Assert.assertEquals(accountDao.getById(account.getId()), newAccount);
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToHandleOtherBCDClass() throws Exception {
        final Account account = createTestAccount();
        accountDao.create(account, context);

        final MutableAccountData otherAccount = account.toMutableAccountData();
        otherAccount.setAddress1(UUID.randomUUID().toString());
        otherAccount.setEmail(UUID.randomUUID().toString());
        // Same BCD, but not .equals method
        otherAccount.setBillCycleDay(new BillCycleDay() {
            @Override
            public int getDayOfMonthUTC() {
                return account.getBillCycleDay().getDayOfMonthUTC();
            }

            @Override
            public int getDayOfMonthLocal() {
                return account.getBillCycleDay().getDayOfMonthLocal();
            }
        });

        final DefaultAccount newAccount = new DefaultAccount(account.getId(), otherAccount);
        accountDao.update(newAccount, context);

        final Account newFetchedAccount = accountDao.getById(account.getId());
        Assert.assertEquals(newFetchedAccount.getAddress1(), newAccount.getAddress1());
        Assert.assertEquals(newFetchedAccount.getEmail(), newAccount.getEmail());
        // Same BCD
        Assert.assertEquals(newFetchedAccount.getBillCycleDay(), account.getBillCycleDay());
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToHandleBCDOfZeroZero() throws Exception {
        final Account account = createTestAccount(0);
        accountDao.create(account, context);
        final Account fetchedAccount = accountDao.getById(account.getId());

        final MutableAccountData otherAccount = account.toMutableAccountData();
        // Set BCD to null
        otherAccount.setBillCycleDay(null);

        final DefaultAccount newAccount = new DefaultAccount(account.getId(), otherAccount);
        accountDao.update(newAccount, context);

        // Same BCD (zero/zero)
        Assert.assertEquals(accountDao.getById(account.getId()), fetchedAccount);
    }

    @Test(groups = "slow")
    public void testAccountEmail() {
        List<AccountEmail> emails = new ArrayList<AccountEmail>();

        // generate random account id
        final UUID accountId = UUID.randomUUID();

        // add a new e-mail
        final AccountEmail email = new DefaultAccountEmail(accountId, "test@gmail.com");
        emails.add(email);
        accountEmailDao.saveEmails(accountId, emails, context);
        emails = accountEmailDao.getEmails(accountId);
        assertEquals(emails.size(), 1);

        // verify that history and audit contain one entry
        verifyAccountEmailAuditAndHistoryCount(accountId, 1);

        // update e-mail
        final AccountEmail updatedEmail = new DefaultAccountEmail(email, "test2@gmail.com");
        emails.clear();
        emails.add(updatedEmail);
        accountEmailDao.saveEmails(accountId, emails, context);
        emails = accountEmailDao.getEmails(accountId);
        assertEquals(emails.size(), 1);

        // verify that history and audit contain three entries
        // two inserts and one delete
        verifyAccountEmailAuditAndHistoryCount(accountId, 3);

        // delete e-mail
        accountEmailDao.saveEmails(accountId, new ArrayList<AccountEmail>(), context);
        emails = accountEmailDao.getEmails(accountId);
        assertEquals(emails.size(), 0);

        // verify that history and audit contain four entries
        verifyAccountEmailAuditAndHistoryCount(accountId, 4);
    }

    @Test(groups = "slow")
    public void testAddAndRemoveAccountEmail() {
        final UUID accountId = UUID.randomUUID();
        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();

        // Verify the original state
        assertEquals(accountEmailDao.getEmails(accountId).size(), 0);

        // Add a new e-mail
        final AccountEmail accountEmail1 = new DefaultAccountEmail(accountId, email1);
        accountEmailDao.addEmail(accountId, accountEmail1, context);
        final List<AccountEmail> firstEmails = accountEmailDao.getEmails(accountId);
        assertEquals(firstEmails.size(), 1);
        assertEquals(firstEmails.get(0).getAccountId(), accountId);
        assertEquals(firstEmails.get(0).getEmail(), email1);

        // Add a second e-mail
        final AccountEmail accountEmail2 = new DefaultAccountEmail(accountId, email2);
        accountEmailDao.addEmail(accountId, accountEmail2, context);
        final List<AccountEmail> secondEmails = accountEmailDao.getEmails(accountId);
        assertEquals(secondEmails.size(), 2);
        assertTrue(secondEmails.get(0).getAccountId().equals(accountId));
        assertTrue(secondEmails.get(1).getAccountId().equals(accountId));
        assertTrue(secondEmails.get(0).getEmail().equals(email1) || secondEmails.get(0).getEmail().equals(email2));
        assertTrue(secondEmails.get(1).getEmail().equals(email1) || secondEmails.get(1).getEmail().equals(email2));

        // Delete the first e-mail
        accountEmailDao.removeEmail(accountId, accountEmail1, context);
        final List<AccountEmail> thirdEmails = accountEmailDao.getEmails(accountId);
        assertEquals(thirdEmails.size(), 1);
        assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        assertEquals(thirdEmails.get(0).getEmail(), email2);

        // Verify that history and audit contain three entries (2 inserts and one delete)
        verifyAccountEmailAuditAndHistoryCount(accountId, 3);
    }

    private void verifyAccountEmailAuditAndHistoryCount(final UUID accountId, final int expectedCount) {
        final Handle handle = dbi.open();

        // verify audit
        StringBuilder sb = new StringBuilder();
        sb.append("select * from audit_log a ");
        sb.append("inner join account_email_history aeh on a.record_id = aeh.history_record_id ");
        sb.append("where a.table_name = 'account_email_history' ");
        sb.append(String.format("and aeh.account_id='%s'", accountId.toString()));
        List<Map<String, Object>> result = handle.select(sb.toString());
        assertEquals(result.size(), expectedCount);

        // ***** NOT IDEAL
        // ... but this works after the email record has been deleted; will likely fail when multiple emails exist for the same account
        // verify history table
        sb = new StringBuilder();
        sb.append("select * from account_email_history aeh ");
        sb.append(String.format("where aeh.account_id='%s'", accountId.toString()));
        result = handle.select(sb.toString());
        assertEquals(result.size(), expectedCount);

        handle.close();
    }
}
