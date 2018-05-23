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

package org.killbill.billing.jaxrs;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Accounts;
import org.killbill.billing.client.model.AuditLogs;
import org.killbill.billing.client.model.CustomFields;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.CustomField;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.Tag;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAccount extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        if (hasFailed()) {
            return;
        }

        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow", description = "Verify no PII data is required")
    public void testEmptyAccount() throws Exception {
        final Account emptyAccount = new Account();

        final Account account = accountApi.createAccount(emptyAccount, requestOptions);
        Assert.assertNotNull(account.getExternalKey());
        Assert.assertNull(account.getName());
        Assert.assertNull(account.getEmail());
    }

    @Test(groups = "slow", description = "Verify external key is unique")
    public void testUniqueExternalKey() throws Exception {
        // Verify the external key is not mandatory
        final Account inputWithNoExternalKey = getAccount(UUID.randomUUID().toString(), null, UUID.randomUUID().toString());
        Assert.assertNull(inputWithNoExternalKey.getExternalKey());

        final Account account = accountApi.createAccount(inputWithNoExternalKey, requestOptions);
        Assert.assertNotNull(account.getExternalKey());

        final Account inputWithSameExternalKey = getAccount(UUID.randomUUID().toString(), account.getExternalKey(), UUID.randomUUID().toString());
        try {
            accountApi.createAccount(inputWithSameExternalKey, requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow", description = "Can create, retrieve, search and update accounts")
    public void testAccountOk() throws Exception {
        final Account input = createAccount();

        // Retrieves by external key
        final Account retrievedAccount = accountApi.getAccountByKey(input.getExternalKey(), requestOptions);
        Assert.assertTrue(retrievedAccount.equals(input));

        // Try search endpoint
        searchAccount(input, retrievedAccount);

        // Update Account
        final Account newInput = new Account(input.getAccountId(),
                                             "zozo", 4, input.getExternalKey(), "rr@google.com", 18,
                                             Currency.USD, null, false, null, null, "UTC",
                                             "bl1", "bh2", "", "", "ca", "San Francisco", "usa", "en", "415-255-2991",
                                             "notes", false, false, null, null, EMPTY_AUDIT_LOGS);

        accountApi.updateAccount(input.getAccountId(), newInput, requestOptions);
        final Account updatedAccount = accountApi.getAccount(input.getAccountId(), requestOptions);
        // referenceTime is set automatically by system, no way to guess it
        newInput.setReferenceTime(updatedAccount.getReferenceTime());
        Assert.assertTrue(updatedAccount.equals(newInput));

        // Try search endpoint
        searchAccount(input, null);
    }

    @Test(groups = "slow", description = "Can reset account notes using flag treatNullAsReset")
    public void testResetAccountNotes() throws Exception {

        final Account input = createAccount();
        Assert.assertNotNull(input.getExternalKey());
        Assert.assertNotNull(input.getNotes());
        Assert.assertEquals(input.getNotes(), "notes");
        Assert.assertEquals(input.getTimeZone(), "UTC");
        Assert.assertEquals(input.getAddress1(), "12 rue des ecoles");
        Assert.assertEquals(input.getAddress2(), "Poitier");
        Assert.assertEquals(input.getCity(), "Quelque part");
        Assert.assertEquals(input.getState(), "Poitou");
        Assert.assertEquals(input.getCountry(), "France");
        Assert.assertEquals(input.getLocale(), "fr");

        // Set notes to something else
        final Account newInput = new Account(input.getAccountId(),
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             "notes2",
                                             null,
                                             null,
                                             null,
                                             null,
                                             EMPTY_AUDIT_LOGS);

        // Update notes, all other fields remaining the same (value set to null but treatNullAsReset defaults to false)
        accountApi.updateAccount(newInput.getAccountId(), newInput, requestOptions);
        Account updatedAccount = accountApi.getAccount(input.getAccountId(), requestOptions);

        Assert.assertNotNull(updatedAccount.getExternalKey());
        Assert.assertNotNull(updatedAccount.getNotes());
        Assert.assertEquals(updatedAccount.getNotes(), "notes2");
        Assert.assertEquals(updatedAccount.getTimeZone(), "UTC");
        Assert.assertEquals(updatedAccount.getAddress1(), "12 rue des ecoles");
        Assert.assertEquals(updatedAccount.getAddress2(), "Poitier");
        Assert.assertEquals(updatedAccount.getCity(), "Quelque part");
        Assert.assertEquals(updatedAccount.getState(), "Poitou");
        Assert.assertEquals(updatedAccount.getCountry(), "France");
        Assert.assertEquals(updatedAccount.getLocale(), "fr");

        // Reset notes, all other fields remaining the same
        updatedAccount.setNotes(null);
        accountApi.updateAccount(updatedAccount.getAccountId(), updatedAccount, true, requestOptions);
        updatedAccount = accountApi.getAccount(input.getAccountId(), requestOptions);

        Assert.assertNotNull(updatedAccount.getExternalKey());
        Assert.assertNull(updatedAccount.getNotes());
        Assert.assertEquals(updatedAccount.getTimeZone(), "UTC");
        Assert.assertEquals(updatedAccount.getAddress1(), "12 rue des ecoles");
        Assert.assertEquals(updatedAccount.getAddress2(), "Poitier");
        Assert.assertEquals(updatedAccount.getCity(), "Quelque part");
        Assert.assertEquals(updatedAccount.getState(), "Poitou");
        Assert.assertEquals(updatedAccount.getCountry(), "France");
        Assert.assertEquals(updatedAccount.getLocale(), "fr");
    }

    @Test(groups = "slow", description = "Can retrieve the account balance")
    public void testAccountWithBalance() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final Account accountWithBalance = accountApi.getAccount(accountJson.getAccountId(), true, false, AuditLevel.NONE, requestOptions);
        final BigDecimal accountBalance = accountWithBalance.getAccountBalance();
        Assert.assertTrue(accountBalance.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test(groups = "slow", description = "Cannot update a non-existent account")
    public void testUpdateNonExistentAccount() throws Exception {
        final Account input = getAccount();
        accountApi.updateAccount(input.getAccountId(), input, requestOptions);
    }

    @Test(groups = "slow", description = "Cannot retrieve non-existent account")
    public void testAccountNonExistent() throws Exception {
        Assert.assertNull(accountApi.getAccount(UUID.randomUUID(), requestOptions));
    }

    @Test(groups = "slow", description = "Can CRUD payment methods")
    public void testAccountPaymentMethods() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(getPaymentMethodCCProperties());
        PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), accountJson.getAccountId(), true, PLUGIN_NAME, info, EMPTY_AUDIT_LOGS);
        final PaymentMethod paymentMethodCC = accountApi.createPaymentMethod(accountJson.getAccountId(), paymentMethodJson, true, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertTrue(paymentMethodCC.isDefault());

        //
        // Add another payment method
        //
        final PaymentMethodPluginDetail info2 = new PaymentMethodPluginDetail();
        info2.setProperties(getPaymentMethodPaypalProperties());
        paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), accountJson.getAccountId(), false, PLUGIN_NAME, info2, EMPTY_AUDIT_LOGS);
        final PaymentMethod paymentMethodPP = accountApi.createPaymentMethod(accountJson.getAccountId(), paymentMethodJson, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertFalse(paymentMethodPP.isDefault());

        //
        // FETCH ALL PAYMENT METHODS
        //
        List<PaymentMethod> paymentMethods = accountApi.getPaymentMethodsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paymentMethods.size(), 2);

        //
        // CHANGE DEFAULT
        //
        assertTrue(paymentMethodApi.getPaymentMethod(paymentMethodCC.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions).isDefault());
        assertFalse(paymentMethodApi.getPaymentMethod(paymentMethodPP.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions).isDefault());
        accountApi.setDefaultPaymentMethod(accountJson.getAccountId(), paymentMethodPP.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertTrue(paymentMethodApi.getPaymentMethod(paymentMethodPP.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions).isDefault());
        assertFalse(paymentMethodApi.getPaymentMethod(paymentMethodCC.getPaymentMethodId(), NULL_PLUGIN_PROPERTIES, requestOptions).isDefault());

        //
        // DELETE NON DEFAULT PM
        //
        paymentMethodApi.deletePaymentMethod(paymentMethodCC.getPaymentMethodId(), false, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        //
        // FETCH ALL PAYMENT METHODS
        //
        paymentMethods = accountApi.getPaymentMethodsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paymentMethods.size(), 1);

        //
        // DELETE DEFAULT PAYMENT METHOD (without special flag first)
        //
        try {
            paymentMethodApi.deletePaymentMethod(paymentMethodPP.getPaymentMethodId(), false, false, NULL_PLUGIN_PROPERTIES, requestOptions);
            fail();
        } catch (final KillBillClientException e) {
        }

        //
        // RETRY TO DELETE DEFAULT PAYMENT METHOD (with special flag this time)
        //
        paymentMethodApi.deletePaymentMethod(paymentMethodPP.getPaymentMethodId(), true, false, NULL_PLUGIN_PROPERTIES, requestOptions);

        // CHECK ACCOUNT IS NOW AUTO_PAY_OFF
        final List<Tag> tagsJson = accountApi.getAccountTags(accountJson.getAccountId(), requestOptions);
        Assert.assertEquals(tagsJson.size(), 1);
        final Tag tagJson = tagsJson.get(0);
        Assert.assertEquals(tagJson.getTagDefinitionName(), "AUTO_PAY_OFF");
        Assert.assertEquals(tagJson.getTagDefinitionId(), new UUID(0, 1));

        // FETCH ACCOUNT AGAIN AND CHECK THERE IS NO DEFAULT PAYMENT METHOD SET
        final Account updatedAccount = accountApi.getAccount(accountJson.getAccountId(), requestOptions);
        Assert.assertEquals(updatedAccount.getAccountId(), accountJson.getAccountId());
        Assert.assertNull(updatedAccount.getPaymentMethodId());

        //
        // FINALLY TRY TO REMOVE AUTO_PAY_OFF WITH NO DEFAULT PAYMENT METHOD ON ACCOUNT
        //
        try {
            accountApi.deleteAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(new UUID(0, 1)), requestOptions);
        } catch (final KillBillClientException e) {
            Assert.assertTrue(e.getBillingException().getCode() == ErrorCode.TAG_CANNOT_BE_REMOVED.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAccountPaymentsWithRefund() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify payments
        final InvoicePayments objFromJson = accountApi.getInvoicePayments(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(objFromJson.size(), 1);
    }

    @Test(groups = "slow", description = "Add tags to account")
    public void testTags() throws Exception {
        final Account input = createAccount();
        // Use tag definition for AUTO_PAY_OFF
        final UUID autoPayOffId = new UUID(0, 1);

        // Add a tag
        accountApi.createAccountTags(input.getAccountId(), ImmutableList.<UUID>of(autoPayOffId), requestOptions);

        // Retrieves all tags
        final List<Tag> tags1 = accountApi.getAccountTags(input.getAccountId(), false, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(tags1.size(), 1);
        Assert.assertEquals(tags1.get(0).getTagDefinitionId(), autoPayOffId);

        // Verify adding the same tag a second time doesn't do anything
        accountApi.createAccountTags(input.getAccountId(), ImmutableList.<UUID>of(autoPayOffId), requestOptions);

        // Retrieves all tags again
        accountApi.createAccountTags(input.getAccountId(), ImmutableList.<UUID>of(autoPayOffId), requestOptions);
        final List<Tag> tags2 = accountApi.getAccountTags(input.getAccountId(), true, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(tags2, tags1);

        // Verify audit logs
        Assert.assertEquals(tags2.get(0).getAuditLogs().size(), 1);
        final AuditLog auditLogJson = tags2.get(0).getAuditLogs().get(0);
        Assert.assertEquals(auditLogJson.getChangeType(), "INSERT");
        Assert.assertEquals(auditLogJson.getChangedBy(), createdBy);
        Assert.assertEquals(auditLogJson.getReasonCode(), reason);
        Assert.assertEquals(auditLogJson.getComments(), comment);
        Assert.assertNotNull(auditLogJson.getChangeDate());
        Assert.assertNotNull(auditLogJson.getUserToken());
    }

    @Test(groups = "slow", description = "Add custom fields to account")
    public void testCustomFields() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        final CustomFields customFields = new CustomFields();
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "1", "value1", EMPTY_AUDIT_LOGS));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "2", "value2", EMPTY_AUDIT_LOGS));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "3", "value3", EMPTY_AUDIT_LOGS));

        accountApi.createAccountCustomFields(accountJson.getAccountId(), customFields, requestOptions);

        final List<CustomField> accountCustomFields = accountApi.getAccountCustomFields(accountJson.getAccountId(), requestOptions);
        assertEquals(accountCustomFields.size(), 3);

        // Delete all custom fields for account
        accountApi.deleteAccountCustomFields(accountJson.getAccountId(), null, requestOptions);

        final List<CustomField> remainingCustomFields = accountApi.getAccountCustomFields(accountJson.getAccountId(), requestOptions);
        assertEquals(remainingCustomFields.size(), 0);
    }

    @Test(groups = "slow", description = "refresh payment methods")
    public void testRefreshPaymentMethods() throws Exception {
        final Account account = createAccountWithDefaultPaymentMethod("someExternalKey");

        final PaymentMethods paymentMethodsBeforeRefreshing = accountApi.getPaymentMethodsForAccount(account.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paymentMethodsBeforeRefreshing.size(), 1);
        assertEquals(paymentMethodsBeforeRefreshing.get(0).getExternalKey(), "someExternalKey");

        // WITH NAME OF AN EXISTING PLUGIN
        accountApi.refreshPaymentMethods(account.getAccountId(), PLUGIN_NAME, NULL_PLUGIN_PROPERTIES, requestOptions);

        final PaymentMethods paymentMethodsAfterExistingPluginCall = accountApi.getPaymentMethodsForAccount(account.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        assertEquals(paymentMethodsAfterExistingPluginCall.size(), 1);
        assertEquals(paymentMethodsAfterExistingPluginCall.get(0).getExternalKey(), "someExternalKey");

        // WITHOUT PLUGIN NAME
        accountApi.refreshPaymentMethods(account.getAccountId(), PLUGIN_NAME, NULL_PLUGIN_PROPERTIES, requestOptions);

        final PaymentMethods paymentMethodsAfterNoPluginNameCall = accountApi.getPaymentMethodsForAccount(account.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paymentMethodsAfterNoPluginNameCall.size(), 1);
        assertEquals(paymentMethodsAfterNoPluginNameCall.get(0).getExternalKey(), "someExternalKey");

        // WITH WRONG PLUGIN NAME
        try {
            accountApi.refreshPaymentMethods(account.getAccountId(), "GreatestPluginEver", NULL_PLUGIN_PROPERTIES, requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN.getCode());
        }
    }

    @Test(groups = "slow", description = "Can paginate through all accounts")
    public void testAccountsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createAccount();
        }

        final Accounts allAccounts = accountApi.getAccounts(requestOptions);
        Assert.assertEquals(allAccounts.size(), 5);

        Accounts page = accountApi.getAccounts(0L, 1L, false, false, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allAccounts.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }

    private void searchAccount(final Account input, @Nullable final Account output) throws Exception {
        // Search by id
        if (output != null) {
            doSearchAccount(input.getAccountId().toString(), output);
        }

        // Search by name
        doSearchAccount(input.getName(), output);

        // Search by email
        doSearchAccount(input.getEmail(), output);

        // Search by company name
        doSearchAccount(input.getCompany(), output);

        // Search by external key.
        // Note: we will always find a match since we don't update it
        final List<Account> accountsByExternalKey = accountApi.searchAccounts(input.getExternalKey(), requestOptions);
        Assert.assertEquals(accountsByExternalKey.size(), 1);
        Assert.assertEquals(accountsByExternalKey.get(0).getAccountId(), input.getAccountId());
        Assert.assertEquals(accountsByExternalKey.get(0).getExternalKey(), input.getExternalKey());
    }

    private void doSearchAccount(final String key, @Nullable final Account output) throws Exception {
        final List<Account> accountsByKey = accountApi.searchAccounts(key, requestOptions);
        if (output == null) {
            Assert.assertEquals(accountsByKey.size(), 0);
        } else {
            Assert.assertEquals(accountsByKey.size(), 1);
            Assert.assertEquals(accountsByKey.get(0), output);
        }
    }

    @Test(groups = "slow", description = "Can create and retrieve parent/children accounts")
    public void testParentAccountOk() throws Exception {

        final Account parentAccount = createAccount();

        final Account childInput = getAccount();
        childInput.setParentAccountId(parentAccount.getAccountId());
        childInput.setIsPaymentDelegatedToParent(true);
        final Account childAccount = accountApi.createAccount(childInput, requestOptions);

        // Retrieves child account by external key
        final Account retrievedAccount = accountApi.getAccountByKey(childAccount.getExternalKey(), requestOptions);
        Assert.assertTrue(retrievedAccount.equals(childAccount));
        Assert.assertEquals(retrievedAccount.getParentAccountId(), parentAccount.getAccountId());
        Assert.assertTrue(retrievedAccount.isPaymentDelegatedToParent());
    }

    @Test(groups = "slow", description = "retrieve children accounts by parent account id")
    public void testGetChildrenAccounts() throws Exception {

        final Account parentAccount = createAccount();

        final Account childInput = getAccount();
        childInput.setParentAccountId(parentAccount.getAccountId());
        childInput.setIsPaymentDelegatedToParent(true);
        Account childAccount = accountApi.createAccount(childInput, requestOptions);
        childAccount = accountApi.getAccount(childAccount.getAccountId(), true, true, AuditLevel.NONE, requestOptions);

        final Account childInput2 = getAccount();
        childInput2.setParentAccountId(parentAccount.getAccountId());
        childInput2.setIsPaymentDelegatedToParent(true);
        Account childAccount2 = accountApi.createAccount(childInput2, requestOptions);
        childAccount2 = accountApi.getAccount(childAccount2.getAccountId(), true, true, AuditLevel.NONE, requestOptions);

        // Retrieves children accounts by parent account id
        final Accounts childrenAccounts = accountApi.getChildrenAccounts(parentAccount.getAccountId(), true, true, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(childrenAccounts.size(), 2);

        Assert.assertTrue(childrenAccounts.get(0).equals(childAccount));
        Assert.assertTrue(childrenAccounts.get(1).equals(childAccount2));
    }

    @Test(groups = "slow", description = "retrieve an empty children accounts list by a non parent account id")
    public void testEmptyGetChildrenAccounts() throws Exception {

        // Retrieves children accounts by parent account id
        final Accounts childrenAccounts = accountApi.getChildrenAccounts(UUID.randomUUID(), false, false, AuditLevel.NONE, requestOptions);
        Assert.assertEquals(childrenAccounts.size(), 0);

    }
    @Test(groups = "slow", description = "retrieve account logs")
    public void testGetAccountAuditLogs() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // generate more log data
        final CustomFields customFields = new CustomFields();
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "1", "value1", null));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "2", "value2", null));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "3", "value3", null));

        accountApi.createAccountCustomFields(accountJson.getAccountId(), customFields, requestOptions);

        final CustomFields accountCustomFields = accountApi.getAccountCustomFields(accountJson.getAccountId(), requestOptions);
        assertEquals(accountCustomFields.size(), 3);

        final AuditLogs auditLogsJson = accountApi.getAccountAuditLogs(accountJson.getAccountId(), requestOptions);
        assertEquals(auditLogsJson.size(), 4);
        assertEquals(auditLogsJson.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(auditLogsJson.get(0).getObjectType(), ObjectType.ACCOUNT);
        assertEquals(auditLogsJson.get(0).getObjectId(), accountJson.getAccountId());

        assertEquals(auditLogsJson.get(1).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(auditLogsJson.get(1).getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(auditLogsJson.get(1).getObjectId(), accountCustomFields.get(0).getCustomFieldId());

        assertEquals(auditLogsJson.get(2).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(auditLogsJson.get(2).getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(auditLogsJson.get(2).getObjectId(), accountCustomFields.get(1).getCustomFieldId());

        assertEquals(auditLogsJson.get(3).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(auditLogsJson.get(3).getObjectType(), ObjectType.CUSTOM_FIELD);
        assertEquals(auditLogsJson.get(3).getObjectId(), accountCustomFields.get(2).getCustomFieldId());
    }

    @Test(groups = "slow", description = "retrieve account logs")
    public void testGetAccountAuditLogsWithHistory() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // Update Account
        final Account newInput = new Account()
                .setAccountId(accountJson.getAccountId())
                .setExternalKey(accountJson.getExternalKey())
                .setName("zozo");


        accountApi.updateAccount(accountJson.getAccountId(), newInput, requestOptions);

        final List<AuditLog> auditLogWithHistories = accountApi.getAccountAuditLogsWithHistory(accountJson.getAccountId(), requestOptions);
        assertEquals(auditLogWithHistories.size(), 2);
        assertEquals(auditLogWithHistories.get(0).getChangeType(), ChangeType.INSERT.toString());
        assertEquals(auditLogWithHistories.get(0).getObjectType(), ObjectType.ACCOUNT);
        assertEquals(auditLogWithHistories.get(0).getObjectId(), accountJson.getAccountId());

        final LinkedHashMap<String, Object> history1 = (LinkedHashMap<String, Object>) auditLogWithHistories.get(0).getHistory();
        assertNotNull(history1);
        assertEquals(history1.get("externalKey"), accountJson.getExternalKey());
        assertEquals(history1.get("name"), accountJson.getName());

        final LinkedHashMap history2 = (LinkedHashMap) auditLogWithHistories.get(1).getHistory();
        assertNotNull(history2);
        assertEquals(history2.get("externalKey"), accountJson.getExternalKey());
        assertNotEquals(history2.get("name"), accountJson.getName());
        assertEquals(history2.get("name"), "zozo");
    }

}
