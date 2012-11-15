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

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.exceptions.TransactionFailedException;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
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
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.audit.dao.DefaultAuditDao;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.customfield.dao.CustomFieldModelDao;
import com.ning.billing.util.customfield.dao.DefaultCustomFieldDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.DefaultTagDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagDefinitionSqlDao;
import com.ning.billing.util.tag.dao.TagModelDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAccountDao extends AccountDaoTestBase {

    @Test(groups = "slow")
    public void testBasic() throws AccountApiException {
        final AccountModelDao a = createTestAccount(5);
        accountDao.create(a, internalCallContext);
        final String key = a.getExternalKey();

        AccountModelDao r = accountDao.getAccountByKey(key, internalCallContext);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        r = accountDao.getById(r.getId(), internalCallContext);
        assertNotNull(r);
        assertEquals(r.getExternalKey(), a.getExternalKey());

        final List<AccountModelDao> all = accountDao.get(internalCallContext);
        assertNotNull(all);
        assertTrue(all.size() >= 1);
    }

    // simple test to ensure long phone numbers can be stored
    @Test(groups = "slow")
    public void testLongPhoneNumber() throws AccountApiException {
        final AccountModelDao account = createTestAccount(1, "123456789012345678901234");
        accountDao.create(account, internalCallContext);

        final AccountModelDao saved = accountDao.getAccountByKey(account.getExternalKey(), internalCallContext);
        assertNotNull(saved);
    }

    // simple test to ensure excessively long phone numbers cannot be stored
    @Test(groups = "slow")
    public void testOverlyLongPhoneNumber() throws AccountApiException {
        final AccountModelDao account = createTestAccount(1, "12345678901234567890123456");
        try {
            accountDao.create(account, internalCallContext);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getCause() instanceof SQLException);
        }
    }

    @Test(groups = "slow")
    public void testGetById() throws AccountApiException {
        AccountModelDao account = createTestAccount(1);
        final UUID id = account.getId();
        final String key = account.getExternalKey();
        final String name = account.getName();
        final Integer firstNameLength = account.getFirstNameLength();

        accountDao.create(account, internalCallContext);

        account = accountDao.getById(id, internalCallContext);
        assertNotNull(account);
        assertEquals(account.getId(), id);
        assertEquals(account.getExternalKey(), key);
        assertEquals(account.getName(), name);
        assertEquals(account.getFirstNameLength(), firstNameLength);
    }

    @Test(groups = "slow")
    public void testCustomFields() throws CustomFieldApiException {
        final String fieldName = "testField1";
        final String fieldValue = "testField1_value";

        final UUID accountId = UUID.randomUUID();

        CustomField field = new StringCustomField(fieldName, fieldValue, ObjectType.ACCOUNT, accountId, internalCallContext.getCreatedDate());
        final CustomFieldDao customFieldDao = new DefaultCustomFieldDao(dbi);
        customFieldDao.create(new CustomFieldModelDao(field), internalCallContext);

        final List<CustomFieldModelDao> customFieldMap = customFieldDao.getCustomFields(accountId, ObjectType.ACCOUNT, internalCallContext);
        assertEquals(customFieldMap.size(), 1);
        final CustomFieldModelDao customField = customFieldMap.get(0);
        assertEquals(customField.getFieldName(), fieldName);
        assertEquals(customField.getFieldValue(), fieldValue);
    }

    @Test(groups = "slow")
    public void testTags() throws EntityPersistenceException, TagApiException {
        final AccountModelDao account = createTestAccount(1);
        final TagDefinition definition = new DefaultTagDefinition("Test Tag", "For testing only", false);
        final TagDefinitionSqlDao tagDefinitionDao = dbi.onDemand(TagDefinitionSqlDao.class);
        tagDefinitionDao.create(new TagDefinitionModelDao(definition), internalCallContext);

        final TagDao tagDao = new DefaultTagDao(dbi, tagEventBuilder, bus);

        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.getById(definition.getId().toString(), internalCallContext);
        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, account.getId(), internalCallContext.getCreatedDate());

        tagDao.create(new TagModelDao(tag), internalCallContext);

        final List<TagModelDao> tags = tagDao.getTags(account.getId(), ObjectType.ACCOUNT, internalCallContext);
        assertEquals(tags.size(), 1);

        assertEquals(tags.get(0).getTagDefinitionId(), definition.getId());
    }

    @Test(groups = "slow")
    public void testGetIdFromKey() throws AccountApiException {
        final AccountModelDao account = createTestAccount(1);
        accountDao.create(account, internalCallContext);

        try {
            final UUID accountId = accountDao.getIdFromKey(account.getExternalKey(), internalCallContext);
            assertEquals(accountId, account.getId());
        } catch (AccountApiException a) {
            fail("Retrieving account failed.");
        }
    }

    @Test(groups = "slow", expectedExceptions = AccountApiException.class)
    public void testGetIdFromKeyForNullKey() throws AccountApiException {
        final String key = null;
        accountDao.getIdFromKey(key, internalCallContext);
    }

    @Test(groups = "slow")
    public void testUpdate() throws Exception {
        final AccountModelDao account = createTestAccount(1);
        accountDao.create(account, internalCallContext);

        final AccountData accountData = new MockAccountBuilder(new DefaultAccount(account)).migrated(false)
                                                                                           .isNotifiedForInvoices(false)
                                                                                           .timeZone(DateTimeZone.forID("Australia/Darwin"))
                                                                                           .locale("FR-CA")
                                                                                           .build();

        final AccountModelDao updatedAccount = new AccountModelDao(account.getId(), accountData);
        accountDao.update(updatedAccount, internalCallContext);

        final AccountModelDao savedAccount = accountDao.getAccountByKey(account.getExternalKey(), internalCallContext);

        assertNotNull(savedAccount);
        assertEquals(savedAccount.getName(), updatedAccount.getName());
        assertEquals(savedAccount.getEmail(), updatedAccount.getEmail());
        assertEquals(savedAccount.getPaymentMethodId(), updatedAccount.getPaymentMethodId());
        assertEquals(savedAccount.getBillingCycleDayLocal(), updatedAccount.getBillingCycleDayLocal());
        assertEquals(savedAccount.getBillingCycleDayUtc(), updatedAccount.getBillingCycleDayUtc());
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
    public void testUpdatePaymentMethod() throws Exception {
        final AccountModelDao account = createTestAccount(1);
        accountDao.create(account, internalCallContext);

        final UUID newPaymentMethodId = UUID.randomUUID();
        accountDao.updatePaymentMethod(account.getId(), newPaymentMethodId, internalCallContext);

        final AccountModelDao newAccount = accountDao.getById(account.getId(), internalCallContext);
        assertEquals(newAccount.getPaymentMethodId(), newPaymentMethodId);

        // And then set it to null
        accountDao.updatePaymentMethod(account.getId(), null, internalCallContext);

        final AccountModelDao newAccountWithPMNull = accountDao.getById(account.getId(), internalCallContext);
        assertNull(newAccountWithPMNull.getPaymentMethodId());

    }

    @Test(groups = "slow")
    public void testAddingContactInformation() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final DefaultAccount account = new DefaultAccount(accountId, "extKey123456", "myemail123456@glam.com",
                                                          "John Smith", 4, Currency.USD, new DefaultBillCycleDay(15), null,
                                                          DateTimeZone.forID("America/Cambridge_Bay"), "EN-CA",
                                                          null, null, null, null, null, null, null, null, false, false);
        accountDao.create(new AccountModelDao(accountId, account), internalCallContext);

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

        accountDao.update(new AccountModelDao(accountId, updatedAccount), internalCallContext);

        final AccountModelDao savedAccount = accountDao.getById(accountId, internalCallContext);

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

    @Test(groups = "slow")
    public void testShouldBeAbleToUpdateSomeFields() throws Exception {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final MutableAccountData otherAccount = new DefaultAccount(account).toMutableAccountData();
        otherAccount.setAddress1(UUID.randomUUID().toString());
        otherAccount.setEmail(UUID.randomUUID().toString());

        final AccountModelDao newAccount = new AccountModelDao(account.getId(), otherAccount);
        accountDao.update(newAccount, internalCallContext);

        final AccountModelDao retrievedAccount = accountDao.getById(account.getId(), internalCallContext);
        Assert.assertEquals(retrievedAccount.getAddress1(), newAccount.getAddress1());
        Assert.assertEquals(retrievedAccount.getEmail(), newAccount.getEmail());
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToHandleOtherBCDClass() throws Exception {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final MutableAccountData otherAccount = new DefaultAccount(account).toMutableAccountData();
        otherAccount.setAddress1(UUID.randomUUID().toString());
        otherAccount.setEmail(UUID.randomUUID().toString());
        // Same BCD, but not .equals method
        otherAccount.setBillCycleDay(new BillCycleDay() {
            @Override
            public int getDayOfMonthUTC() {
                return account.getBillingCycleDayUtc();
            }

            @Override
            public int getDayOfMonthLocal() {
                return account.getBillingCycleDayLocal();
            }
        });

        final AccountModelDao newAccount = new AccountModelDao(account.getId(), otherAccount);
        accountDao.update(newAccount, internalCallContext);

        final AccountModelDao newFetchedAccount = accountDao.getById(account.getId(), internalCallContext);
        Assert.assertEquals(newFetchedAccount.getAddress1(), newAccount.getAddress1());
        Assert.assertEquals(newFetchedAccount.getEmail(), newAccount.getEmail());
        // Same BCD
        Assert.assertEquals(newFetchedAccount.getBillingCycleDayUtc(), account.getBillingCycleDayUtc());
        Assert.assertEquals(newFetchedAccount.getBillingCycleDayLocal(), account.getBillingCycleDayLocal());
    }

    @Test(groups = "slow")
    public void testShouldBeAbleToHandleBCDOfZeroZero() throws Exception {
        final AccountModelDao account = createTestAccount(0);
        accountDao.create(account, internalCallContext);
        final AccountModelDao fetchedAccount = accountDao.getById(account.getId(), internalCallContext);

        final MutableAccountData otherAccount = new DefaultAccount(fetchedAccount).toMutableAccountData();
        // Set BCD to null
        otherAccount.setBillCycleDay(null);

        final AccountModelDao newAccount = new AccountModelDao(account.getId(), otherAccount);
        accountDao.update(newAccount, internalCallContext);

        // Same BCD (zero/zero)
        final AccountModelDao retrievedAccount = accountDao.getById(account.getId(), internalCallContext);
        Assert.assertEquals(retrievedAccount.getBillingCycleDayUtc(), fetchedAccount.getBillingCycleDayUtc());
        Assert.assertEquals(retrievedAccount.getBillingCycleDayLocal(), fetchedAccount.getBillingCycleDayLocal());
    }

    @Test(groups = "slow")
    public void testHandleDuplicateEmails() throws AccountApiException {
        final UUID accountId = UUID.randomUUID();
        final AccountEmail email = new DefaultAccountEmail(accountId, "test@gmail.com");
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, internalCallContext).size(), 0);

        final AccountEmailModelDao accountEmailModelDao = new AccountEmailModelDao(email);
        accountDao.addEmail(accountEmailModelDao, internalCallContext);
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, internalCallContext).size(), 1);

        try {
            accountDao.addEmail(accountEmailModelDao, internalCallContext);
            Assert.fail();
        } catch (TransactionFailedException e) {
            Assert.assertTrue(e.getCause() instanceof AccountApiException);
            Assert.assertEquals(((AccountApiException) e.getCause()).getCode(), ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAccountEmail() throws AccountApiException {
        List<AccountEmailModelDao> emails;

        // generate random account id
        final UUID accountId = UUID.randomUUID();

        // add a new e-mail
        final AccountEmail email = new DefaultAccountEmail(accountId, "test@gmail.com");
        accountDao.addEmail(new AccountEmailModelDao(email), internalCallContext);
        emails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        assertEquals(emails.size(), 1);

        // verify that audit contains one entry
        final AuditDao audit = new DefaultAuditDao(dbi);
        // TODO - uncomment when TableName story is fixed
        //final List<AuditLog> auditLogs = audit.getAuditLogsForId(TableName.ACCOUNT_EMAIL, email.getId(), AuditLevel.FULL, internalCallContext);
        //assertEquals(auditLogs.size(), 1);

        // delete e-mail
        accountDao.removeEmail(new AccountEmailModelDao(email), internalCallContext);

        emails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        assertEquals(emails.size(), 0);
    }

    @Test(groups = "slow")
    public void testAddAndRemoveAccountEmail() throws AccountApiException {
        final UUID accountId = UUID.randomUUID();
        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();

        // Verify the original state
        assertEquals(accountDao.getEmailsByAccountId(accountId, internalCallContext).size(), 0);

        // Add a new e-mail
        final AccountEmail accountEmail1 = new DefaultAccountEmail(accountId, email1);
        accountDao.addEmail(new AccountEmailModelDao(accountEmail1), internalCallContext);
        final List<AccountEmailModelDao> firstEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        assertEquals(firstEmails.size(), 1);
        assertEquals(firstEmails.get(0).getAccountId(), accountId);
        assertEquals(firstEmails.get(0).getEmail(), email1);

        // Add a second e-mail
        final AccountEmail accountEmail2 = new DefaultAccountEmail(accountId, email2);
        accountDao.addEmail(new AccountEmailModelDao(accountEmail2), internalCallContext);
        final List<AccountEmailModelDao> secondEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        assertEquals(secondEmails.size(), 2);
        assertTrue(secondEmails.get(0).getAccountId().equals(accountId));
        assertTrue(secondEmails.get(1).getAccountId().equals(accountId));
        assertTrue(secondEmails.get(0).getEmail().equals(email1) || secondEmails.get(0).getEmail().equals(email2));
        assertTrue(secondEmails.get(1).getEmail().equals(email1) || secondEmails.get(1).getEmail().equals(email2));

        // Delete the first e-mail
        accountDao.removeEmail(new AccountEmailModelDao(accountEmail1), internalCallContext);
        final List<AccountEmailModelDao> thirdEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        assertEquals(thirdEmails.size(), 1);
        assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        assertEquals(thirdEmails.get(0).getEmail(), email2);
    }
}
