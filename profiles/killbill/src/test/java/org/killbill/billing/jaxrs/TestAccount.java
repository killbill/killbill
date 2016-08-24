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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Accounts;
import org.killbill.billing.client.model.AuditLog;
import org.killbill.billing.client.model.CustomField;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.Tag;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAccount extends TestJaxrsBase {

    @Inject
    protected OSGIServiceRegistration<PaymentPluginApi> registry;

    private MockPaymentProviderPlugin mockPaymentProviderPlugin;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        mockPaymentProviderPlugin = (MockPaymentProviderPlugin) registry.getServiceForName(PLUGIN_NAME);
    }

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        mockPaymentProviderPlugin.clear();
    }

    @Test(groups = "slow", description = "Verify no PII data is required")
    public void testEmptyAccount() throws Exception {
        final Account emptyAccount = new Account();

        final Account account = killBillClient.createAccount(emptyAccount, createdBy, reason, comment);
        Assert.assertNotNull(account.getExternalKey());
        Assert.assertNull(account.getName());
        Assert.assertNull(account.getEmail());
    }

    @Test(groups = "slow", description = "Verify external key is unique")
    public void testUniqueExternalKey() throws Exception {
        // Verify the external key is not mandatory
        final Account inputWithNoExternalKey = getAccount(UUID.randomUUID().toString(), null, UUID.randomUUID().toString());
        Assert.assertNull(inputWithNoExternalKey.getExternalKey());

        final Account account = killBillClient.createAccount(inputWithNoExternalKey, createdBy, reason, comment);
        Assert.assertNotNull(account.getExternalKey());

        final Account inputWithSameExternalKey = getAccount(UUID.randomUUID().toString(), account.getExternalKey(), UUID.randomUUID().toString());
        try {
            killBillClient.createAccount(inputWithSameExternalKey, createdBy, reason, comment);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode());
        }
    }

    @Test(groups = "slow", description = "Can create, retrieve, search and update accounts")
    public void testAccountOk() throws Exception {
        final Account input = createAccount();

        // Retrieves by external key
        final Account retrievedAccount = killBillClient.getAccount(input.getExternalKey());
        Assert.assertTrue(retrievedAccount.equals(input));

        // Try search endpoint
        searchAccount(input, retrievedAccount);

        // Update Account
        final Account newInput = new Account(input.getAccountId(),
                                             "zozo", 4, input.getExternalKey(), "rr@google.com", 18,
                                             "USD", null, false, null, "UTC",
                                             "bl1", "bh2", "", "", "ca", "San Francisco", "usa", "en", "415-255-2991",
                                             "notes", false, false, null, null);
        final Account updatedAccount = killBillClient.updateAccount(newInput, createdBy, reason, comment);
        Assert.assertTrue(updatedAccount.equals(newInput));

        // Try search endpoint
        searchAccount(input, null);
    }

    @Test(groups = "slow", description = "Can retrieve the account balance")
    public void testAccountWithBalance() throws Exception {
        final Account accountJson = createAccountNoPMBundleAndSubscriptionAndWaitForFirstInvoice();

        final Account accountWithBalance = killBillClient.getAccount(accountJson.getAccountId(), true, false);
        final BigDecimal accountBalance = accountWithBalance.getAccountBalance();
        Assert.assertTrue(accountBalance.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test(groups = "slow", description = "Cannot update a non-existent account")
    public void testUpdateNonExistentAccount() throws Exception {
        final Account input = getAccount();

        Assert.assertNull(killBillClient.updateAccount(input, createdBy, reason, comment));
    }

    @Test(groups = "slow", description = "Cannot retrieve non-existent account")
    public void testAccountNonExistent() throws Exception {
        Assert.assertNull(killBillClient.getAccount(UUID.randomUUID()));
        Assert.assertNull(killBillClient.getAccount(UUID.randomUUID().toString()));
    }

    @Test(groups = "slow", description = "Can CRUD payment methods")
    public void testAccountPaymentMethods() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(getPaymentMethodCCProperties());
        PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), accountJson.getAccountId(), true, PLUGIN_NAME, info);
        final PaymentMethod paymentMethodCC = killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        assertTrue(paymentMethodCC.getIsDefault());

        //
        // Add another payment method
        //
        final PaymentMethodPluginDetail info2 = new PaymentMethodPluginDetail();
        info2.setProperties(getPaymentMethodPaypalProperties());
        paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), accountJson.getAccountId(), false, PLUGIN_NAME, info2);
        final PaymentMethod paymentMethodPP = killBillClient.createPaymentMethod(paymentMethodJson, createdBy, reason, comment);
        assertFalse(paymentMethodPP.getIsDefault());

        //
        // FETCH ALL PAYMENT METHODS
        //
        List<PaymentMethod> paymentMethods = killBillClient.getPaymentMethodsForAccount(accountJson.getAccountId());
        assertEquals(paymentMethods.size(), 2);

        //
        // CHANGE DEFAULT
        //
        assertTrue(killBillClient.getPaymentMethod(paymentMethodCC.getPaymentMethodId()).getIsDefault());
        assertFalse(killBillClient.getPaymentMethod(paymentMethodPP.getPaymentMethodId()).getIsDefault());
        killBillClient.updateDefaultPaymentMethod(accountJson.getAccountId(), paymentMethodPP.getPaymentMethodId(), createdBy, reason, comment);
        assertTrue(killBillClient.getPaymentMethod(paymentMethodPP.getPaymentMethodId()).getIsDefault());
        assertFalse(killBillClient.getPaymentMethod(paymentMethodCC.getPaymentMethodId()).getIsDefault());

        //
        // DELETE NON DEFAULT PM
        //
        killBillClient.deletePaymentMethod(paymentMethodCC.getPaymentMethodId(), false, false, createdBy, reason, comment);

        //
        // FETCH ALL PAYMENT METHODS
        //
        paymentMethods = killBillClient.getPaymentMethodsForAccount(accountJson.getAccountId());
        assertEquals(paymentMethods.size(), 1);

        //
        // DELETE DEFAULT PAYMENT METHOD (without special flag first)
        //
        try {
            killBillClient.deletePaymentMethod(paymentMethodPP.getPaymentMethodId(), false, false, createdBy, reason, comment);
            fail();
        } catch (final KillBillClientException e) {
        }

        //
        // RETRY TO DELETE DEFAULT PAYMENT METHOD (with special flag this time)
        //
        killBillClient.deletePaymentMethod(paymentMethodPP.getPaymentMethodId(), true, false, createdBy, reason, comment);

        // CHECK ACCOUNT IS NOW AUTO_PAY_OFF
        final List<Tag> tagsJson = killBillClient.getAccountTags(accountJson.getAccountId());
        Assert.assertEquals(tagsJson.size(), 1);
        final Tag tagJson = tagsJson.get(0);
        Assert.assertEquals(tagJson.getTagDefinitionName(), "AUTO_PAY_OFF");
        Assert.assertEquals(tagJson.getTagDefinitionId(), new UUID(0, 1));

        // FETCH ACCOUNT AGAIN AND CHECK THERE IS NO DEFAULT PAYMENT METHOD SET
        final Account updatedAccount = killBillClient.getAccount(accountJson.getAccountId());
        Assert.assertEquals(updatedAccount.getAccountId(), accountJson.getAccountId());
        Assert.assertNull(updatedAccount.getPaymentMethodId());

        //
        // FINALLY TRY TO REMOVE AUTO_PAY_OFF WITH NO DEFAULT PAYMENT METHOD ON ACCOUNT
        //
        try {
            killBillClient.deleteAccountTag(accountJson.getAccountId(), new UUID(0, 1), createdBy, reason, comment);
        } catch (final KillBillClientException e) {
        }
    }

    @Test(groups = "slow")
    public void testAccountPaymentsWithRefund() throws Exception {
        final Account accountJson = createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // Verify payments
        final InvoicePayments objFromJson = killBillClient.getInvoicePaymentsForAccount(accountJson.getAccountId());
        Assert.assertEquals(objFromJson.size(), 1);
    }

    @Test(groups = "slow", description = "Add tags to account")
    public void testTags() throws Exception {
        final Account input = createAccount();
        // Use tag definition for AUTO_PAY_OFF
        final UUID autoPayOffId = new UUID(0, 1);

        // Add a tag
        killBillClient.createAccountTag(input.getAccountId(), autoPayOffId, createdBy, reason, comment);

        // Retrieves all tags
        final List<Tag> tags1 = killBillClient.getAccountTags(input.getAccountId(), AuditLevel.FULL);
        Assert.assertEquals(tags1.size(), 1);
        Assert.assertEquals(tags1.get(0).getTagDefinitionId(), autoPayOffId);

        // Verify adding the same tag a second time doesn't do anything
        killBillClient.createAccountTag(input.getAccountId(), autoPayOffId, createdBy, reason, comment);

        // Retrieves all tags again
        killBillClient.createAccountTag(input.getAccountId(), autoPayOffId, createdBy, reason, comment);
        final List<Tag> tags2 = killBillClient.getAccountTags(input.getAccountId(), AuditLevel.FULL);
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

        final Collection<CustomField> customFields = new LinkedList<CustomField>();
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "1", "value1", null));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "2", "value2", null));
        customFields.add(new CustomField(null, accountJson.getAccountId(), ObjectType.ACCOUNT, "3", "value3", null));

        killBillClient.createAccountCustomFields(accountJson.getAccountId(), customFields, createdBy, reason, comment);

        final List<CustomField> accountCustomFields = killBillClient.getAccountCustomFields(accountJson.getAccountId());
        assertEquals(accountCustomFields.size(), 3);

        // Delete all custom fields for account
        killBillClient.deleteAccountCustomFields(accountJson.getAccountId(), createdBy, reason, comment);

        final List<CustomField> remainingCustomFields = killBillClient.getAccountCustomFields(accountJson.getAccountId());
        assertEquals(remainingCustomFields.size(), 0);
    }

    @Test(groups = "slow", description = "refresh payment methods")
    public void testRefreshPaymentMethods() throws Exception {
        Account account = createAccountWithDefaultPaymentMethod("someExternalKey");

        final PaymentMethods paymentMethodsBeforeRefreshing = killBillClient.getPaymentMethodsForAccount(account.getAccountId());
        assertEquals(paymentMethodsBeforeRefreshing.size(), 1);
        assertEquals(paymentMethodsBeforeRefreshing.get(0).getExternalKey(), "someExternalKey");

        // WITH NAME OF AN EXISTING PLUGIN
        killBillClient.refreshPaymentMethods(account.getAccountId(), PLUGIN_NAME, ImmutableMap.<String, String>of(), createdBy, reason, comment);

        final PaymentMethods paymentMethodsAfterExistingPluginCall = killBillClient.getPaymentMethodsForAccount(account.getAccountId());

        assertEquals(paymentMethodsAfterExistingPluginCall.size(), 1);
        assertEquals(paymentMethodsAfterExistingPluginCall.get(0).getExternalKey(), "someExternalKey");

        // WITHOUT PLUGIN NAME
        killBillClient.refreshPaymentMethods(account.getAccountId(), ImmutableMap.<String, String>of(), createdBy, reason, comment);

        final PaymentMethods paymentMethodsAfterNoPluginNameCall = killBillClient.getPaymentMethodsForAccount(account.getAccountId());
        assertEquals(paymentMethodsAfterNoPluginNameCall.size(), 1);
        assertEquals(paymentMethodsAfterNoPluginNameCall.get(0).getExternalKey(), "someExternalKey");

        // WITH WRONG PLUGIN NAME
        try {
            killBillClient.refreshPaymentMethods(account.getAccountId(), "GreatestPluginEver", ImmutableMap.<String, String>of(), createdBy, reason, comment);
            Assert.fail();
        } catch (KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getCode(), (Integer) ErrorCode.PAYMENT_NO_SUCH_PAYMENT_PLUGIN.getCode());
        }
    }

    @Test(groups = "slow", description = "Can paginate through all accounts")
    public void testAccountsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createAccount();
        }

        final Accounts allAccounts = killBillClient.getAccounts();
        Assert.assertEquals(allAccounts.size(), 5);

        Accounts page = killBillClient.getAccounts(0L, 1L);
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
        final List<Account> accountsByExternalKey = killBillClient.searchAccounts(input.getExternalKey());
        Assert.assertEquals(accountsByExternalKey.size(), 1);
        Assert.assertEquals(accountsByExternalKey.get(0).getAccountId(), input.getAccountId());
        Assert.assertEquals(accountsByExternalKey.get(0).getExternalKey(), input.getExternalKey());
    }

    private void doSearchAccount(final String key, @Nullable final Account output) throws Exception {
        final List<Account> accountsByKey = killBillClient.searchAccounts(key);
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
        final Account childAccount = killBillClient.createAccount(childInput, createdBy, reason, comment);

        // Retrieves child account by external key
        final Account retrievedAccount = killBillClient.getAccount(childAccount.getExternalKey());
        Assert.assertTrue(retrievedAccount.equals(childAccount));
        Assert.assertEquals(retrievedAccount.getParentAccountId(), parentAccount.getAccountId());
        Assert.assertTrue(retrievedAccount.getIsPaymentDelegatedToParent());
    }

    @Test(groups = "slow", description = "retrieve children accounts by parent account id")
    public void testGetChildrenAccounts() throws Exception {

        final Account parentAccount = createAccount();

        final Account childInput = getAccount();
        childInput.setParentAccountId(parentAccount.getAccountId());
        childInput.setIsPaymentDelegatedToParent(true);
        Account childAccount = killBillClient.createAccount(childInput, createdBy, reason, comment);
        childAccount = killBillClient.getAccount(childAccount.getAccountId(), true, true, basicRequestOptions());

        final Account childInput2 = getAccount();
        childInput2.setParentAccountId(parentAccount.getAccountId());
        childInput2.setIsPaymentDelegatedToParent(true);
        Account childAccount2 = killBillClient.createAccount(childInput2, createdBy, reason, comment);
        childAccount2 = killBillClient.getAccount(childAccount2.getAccountId(), true, true, basicRequestOptions());

        // Retrieves children accounts by parent account id
        final Accounts childrenAccounts = killBillClient.getChildrenAccounts(parentAccount.getAccountId(), true, true, requestOptions);
        Assert.assertEquals(childrenAccounts.size(), 2);

        Assert.assertTrue(childrenAccounts.get(0).equals(childAccount));
        Assert.assertTrue(childrenAccounts.get(1).equals(childAccount2));
    }

    @Test(groups = "slow", description = "retrieve an empty children accounts list by a non parent account id")
    public void testEmptyGetChildrenAccounts() throws Exception {

        // Retrieves children accounts by parent account id
        final Accounts childrenAccounts = killBillClient.getChildrenAccounts(UUID.randomUUID(), false, false, requestOptions);
        Assert.assertEquals(childrenAccounts.size(), 0);

    }

    @Test(groups = "slow", description = "retrieve an empty children accounts list by a null id")
    public void testGetChildrenAccountsByNullId() throws Exception {

        // Retrieves children accounts by parent account id
        final Accounts childrenAccounts = killBillClient.getChildrenAccounts(null, true, true, requestOptions);
        Assert.assertEquals(childrenAccounts.size(), 0);

    }

}
