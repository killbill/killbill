/*
 * Copyright 2010-2013 Ning, Inc.
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
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.AccountTestSuiteWithEmbeddedDB;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.CustomFieldApiException;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.StringCustomField;
import com.ning.billing.util.customfield.dao.CustomFieldModelDao;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.Pagination;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagModelDao;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.account.AccountTestUtils.checkAccountsEqual;
import static com.ning.billing.account.AccountTestUtils.createTestAccount;

public class TestAccountDao extends AccountTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Test Account: basic DAO calls")
    public void testBasic() throws AccountApiException {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        // Retrieve by key
        AccountModelDao retrievedAccount = accountDao.getAccountByKey(account.getExternalKey(), internalCallContext);
        checkAccountsEqual(retrievedAccount, account);

        // Retrieve by id
        retrievedAccount = accountDao.getById(retrievedAccount.getId(), internalCallContext);
        checkAccountsEqual(retrievedAccount, account);

        // Retrieve all
        final Pagination<AccountModelDao> allAccounts = accountDao.getAll(internalCallContext);
        final List<AccountModelDao> all = ImmutableList.<AccountModelDao>copyOf(allAccounts);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        checkAccountsEqual(all.get(0), account);

        // Verify audits
        final List<AuditLog> auditLogsForAccount = auditDao.getAuditLogsForId(TableName.ACCOUNT, account.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsForAccount.size(), 1);
        Assert.assertEquals(auditLogsForAccount.get(0).getChangeType(), ChangeType.INSERT);
    }

    // Simple test to ensure long phone numbers can be stored
    @Test(groups = "slow", description = "Test Account DAO: long numbers")
    public void testLongPhoneNumber() throws AccountApiException {
        final AccountModelDao account = createTestAccount("123456789012345678901234");
        accountDao.create(account, internalCallContext);

        final AccountModelDao retrievedAccount = accountDao.getAccountByKey(account.getExternalKey(), internalCallContext);
        checkAccountsEqual(retrievedAccount, account);
    }

    // Simple test to ensure excessively long phone numbers cannot be stored
    @Test(groups = "slow", description = "Test Account DAO: very long numbers")
    public void testOverlyLongPhoneNumber() throws AccountApiException {
        final AccountModelDao account = createTestAccount("12345678901234567890123456");
        try {
            accountDao.create(account, internalCallContext);
            Assert.fail();
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getCause() instanceof SQLException);
        }
    }

    @Test(groups = "slow", description = "Test Account DAO: custom fields")
    public void testCustomFields() throws CustomFieldApiException {
        final UUID accountId = UUID.randomUUID();
        final String fieldName = UUID.randomUUID().toString().substring(0, 4);
        final String fieldValue = UUID.randomUUID().toString();

        final CustomField field = new StringCustomField(fieldName, fieldValue, ObjectType.ACCOUNT, accountId, internalCallContext.getCreatedDate());
        customFieldDao.create(new CustomFieldModelDao(field), internalCallContext);

        final List<CustomFieldModelDao> customFieldMap = customFieldDao.getCustomFieldsForObject(accountId, ObjectType.ACCOUNT, internalCallContext);
        Assert.assertEquals(customFieldMap.size(), 1);

        final CustomFieldModelDao customField = customFieldMap.get(0);
        Assert.assertEquals(customField.getFieldName(), fieldName);
        Assert.assertEquals(customField.getFieldValue(), fieldValue);
    }

    @Test(groups = "slow", description = "Test Account DAO: tags")
    public void testTags() throws TagApiException, TagDefinitionApiException {
        final AccountModelDao account = createTestAccount();
        final TagDefinitionModelDao tagDefinition = tagDefinitionDao.create(UUID.randomUUID().toString().substring(0, 4), UUID.randomUUID().toString(), internalCallContext);
        final Tag tag = new DescriptiveTag(tagDefinition.getId(), ObjectType.ACCOUNT, account.getId(), internalCallContext.getCreatedDate());
        tagDao.create(new TagModelDao(tag), internalCallContext);

        final List<TagModelDao> tags = tagDao.getTagsForObject(account.getId(), ObjectType.ACCOUNT, false, internalCallContext);
        Assert.assertEquals(tags.size(), 1);
        Assert.assertEquals(tags.get(0).getTagDefinitionId(), tagDefinition.getId());
        Assert.assertEquals(tags.get(0).getObjectId(), account.getId());
        Assert.assertEquals(tags.get(0).getObjectType(), ObjectType.ACCOUNT);
    }

    @Test(groups = "slow", description = "Test Account DAO: retrieve by externalKey")
    public void testGetIdFromKey() throws AccountApiException {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final UUID accountId = accountDao.getIdFromKey(account.getExternalKey(), internalCallContext);
        Assert.assertEquals(accountId, account.getId());
    }

    @Test(groups = "slow", expectedExceptions = AccountApiException.class, description = "Test Account DAO: retrieve by null externalKey throws an exception")
    public void testGetIdFromKeyForNullKey() throws AccountApiException {
        accountDao.getIdFromKey(null, internalCallContext);
    }

    @Test(groups = "slow", description = "Test Account DAO: basic update (1)")
    public void testUpdate() throws Exception {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final AccountData accountData = new MockAccountBuilder(new DefaultAccount(account)).migrated(false)
                                                                                           .isNotifiedForInvoices(false)
                                                                                           .timeZone(DateTimeZone.forID("Australia/Darwin"))
                                                                                           .locale("FR-CA")
                                                                                           .build();
        final AccountModelDao updatedAccount = new AccountModelDao(account.getId(), accountData);
        accountDao.update(updatedAccount, internalCallContext);

        final AccountModelDao retrievedAccount = accountDao.getAccountByKey(account.getExternalKey(), internalCallContext);
        checkAccountsEqual(retrievedAccount, updatedAccount);
    }

    @Test(groups = "slow", description = "Test Account DAO: payment method update")
    public void testUpdatePaymentMethod() throws Exception {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final UUID newPaymentMethodId = UUID.randomUUID();
        accountDao.updatePaymentMethod(account.getId(), newPaymentMethodId, internalCallContext);

        final AccountModelDao newAccount = accountDao.getById(account.getId(), internalCallContext);
        Assert.assertEquals(newAccount.getPaymentMethodId(), newPaymentMethodId);

        // And then set it to null (delete the default payment method)
        accountDao.updatePaymentMethod(account.getId(), null, internalCallContext);

        final AccountModelDao newAccountWithPMNull = accountDao.getById(account.getId(), internalCallContext);
        Assert.assertNull(newAccountWithPMNull.getPaymentMethodId());
    }

    @Test(groups = "slow", description = "Test Account DAO: basic update (2)")
    public void testShouldBeAbleToUpdateSomeFields() throws Exception {
        final AccountModelDao account = createTestAccount();
        accountDao.create(account, internalCallContext);

        final MutableAccountData otherAccount = new DefaultAccount(account).toMutableAccountData();
        otherAccount.setAddress1(UUID.randomUUID().toString());
        otherAccount.setEmail(UUID.randomUUID().toString());
        final AccountModelDao newAccount = new AccountModelDao(account.getId(), otherAccount);
        accountDao.update(newAccount, internalCallContext);

        final AccountModelDao retrievedAccount = accountDao.getById(account.getId(), internalCallContext);
        checkAccountsEqual(retrievedAccount, newAccount);
    }

    @Test(groups = "slow", description = "Test Account DAO: BCD of 0")
    public void testShouldBeAbleToHandleBCDOfZero() throws Exception {
        final AccountModelDao account = createTestAccount(0);
        accountDao.create(account, internalCallContext);

        // Same BCD (zero)
        final AccountModelDao retrievedAccount = accountDao.getById(account.getId(), internalCallContext);
        checkAccountsEqual(retrievedAccount, account);
    }

    @Test(groups = "slow", description = "Test Account DAO: duplicate emails throws an exception")
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
        } catch (AccountApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.ACCOUNT_EMAIL_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow", description = "Test Account DAO: add and remove email")
    public void testAddRemoveAccountEmail() throws AccountApiException {
        final UUID accountId = UUID.randomUUID();

        // Add a new e-mail
        final AccountEmail email = new DefaultAccountEmail(accountId, "test@gmail.com");
        accountDao.addEmail(new AccountEmailModelDao(email), internalCallContext);

        final List<AccountEmailModelDao> accountEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        Assert.assertEquals(accountEmails.size(), 1);
        Assert.assertEquals(accountEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(accountEmails.get(0).getEmail(), email.getEmail());

        // Verify audits
        final List<AuditLog> auditLogsForAccountEmail = auditDao.getAuditLogsForId(TableName.ACCOUNT_EMAIL, email.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsForAccountEmail.size(), 1);
        Assert.assertEquals(auditLogsForAccountEmail.get(0).getChangeType(), ChangeType.INSERT);

        // Delete the e-mail
        accountDao.removeEmail(new AccountEmailModelDao(email), internalCallContext);
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, internalCallContext).size(), 0);
    }

    @Test(groups = "slow", description = "Test Account DAO: add and remove multiple emails")
    public void testAddAndRemoveMultipleAccountEmails() throws AccountApiException {
        final UUID accountId = UUID.randomUUID();
        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();

        // Verify the original state
        Assert.assertEquals(accountDao.getEmailsByAccountId(accountId, internalCallContext).size(), 0);

        // Add a new e-mail
        final AccountEmail accountEmail1 = new DefaultAccountEmail(accountId, email1);
        accountDao.addEmail(new AccountEmailModelDao(accountEmail1), internalCallContext);
        final List<AccountEmailModelDao> firstEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        Assert.assertEquals(firstEmails.size(), 1);
        Assert.assertEquals(firstEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(firstEmails.get(0).getEmail(), email1);

        // Add a second e-mail
        final AccountEmail accountEmail2 = new DefaultAccountEmail(accountId, email2);
        accountDao.addEmail(new AccountEmailModelDao(accountEmail2), internalCallContext);
        final List<AccountEmailModelDao> secondEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        Assert.assertEquals(secondEmails.size(), 2);
        Assert.assertTrue(secondEmails.get(0).getAccountId().equals(accountId));
        Assert.assertTrue(secondEmails.get(1).getAccountId().equals(accountId));
        Assert.assertTrue(secondEmails.get(0).getEmail().equals(email1) || secondEmails.get(0).getEmail().equals(email2));
        Assert.assertTrue(secondEmails.get(1).getEmail().equals(email1) || secondEmails.get(1).getEmail().equals(email2));

        // Delete the first e-mail
        accountDao.removeEmail(new AccountEmailModelDao(accountEmail1), internalCallContext);
        final List<AccountEmailModelDao> thirdEmails = accountDao.getEmailsByAccountId(accountId, internalCallContext);
        Assert.assertEquals(thirdEmails.size(), 1);
        Assert.assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(thirdEmails.get(0).getEmail(), email2);

        // Verify audits
        final List<AuditLog> auditLogsForAccountEmail1 = auditDao.getAuditLogsForId(TableName.ACCOUNT_EMAIL, accountEmail1.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsForAccountEmail1.size(), 2);
        Assert.assertEquals(auditLogsForAccountEmail1.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(auditLogsForAccountEmail1.get(1).getChangeType(), ChangeType.DELETE);
        final List<AuditLog> auditLogsForAccountEmail2 = auditDao.getAuditLogsForId(TableName.ACCOUNT_EMAIL, accountEmail2.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsForAccountEmail2.size(), 1);
        Assert.assertEquals(auditLogsForAccountEmail2.get(0).getChangeType(), ChangeType.INSERT);
    }
}
