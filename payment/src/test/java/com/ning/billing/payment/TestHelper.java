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

package com.ning.billing.payment;

import java.util.UUID;

import org.apache.commons.lang.RandomStringUtils;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceCreationEvent;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.glue.PaymentTestModuleWithMocks;
import com.ning.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.entity.EntityPersistenceException;

public class TestHelper {

    protected final AccountUserApi accountUserApi;
    protected final InvoicePaymentApi invoicePaymentApi;
    protected PaymentApi paymentApi;
    private final CallContext context;
    private final Bus eventBus;

    @Inject
    public TestHelper(CallContextFactory factory, AccountUserApi accountUserApi, InvoicePaymentApi invoicePaymentApi, PaymentApi paymentApi, Bus eventBus) {
        this.eventBus = eventBus;
        this.accountUserApi = accountUserApi;
        this.invoicePaymentApi = invoicePaymentApi;
        this.paymentApi = paymentApi;
        context = factory.createCallContext("Princess Buttercup", CallOrigin.TEST, UserType.TEST);
    }

  
    
    public Invoice createTestInvoice(Account account,
                                     DateTime targetDate,
                                     Currency currency,
                                     InvoiceItem... items) throws EventBusException {
        Invoice invoice = new MockInvoice(account.getId(), new DateTime(), targetDate, currency);

        for (InvoiceItem item : items) {
            if (item instanceof MockRecurringInvoiceItem) {
                MockRecurringInvoiceItem recurringInvoiceItem = (MockRecurringInvoiceItem) item;
                invoice.addInvoiceItem(new MockRecurringInvoiceItem(invoice.getId(),
                                                               account.getId(),
                                                               recurringInvoiceItem.getBundleId(),
                                                               recurringInvoiceItem.getSubscriptionId(),
                                                               recurringInvoiceItem.getPlanName(),
                                                               recurringInvoiceItem.getPhaseName(),
                                                               recurringInvoiceItem.getStartDate(),
                                                               recurringInvoiceItem.getEndDate(),
                                                               recurringInvoiceItem.getAmount(),
                                                               recurringInvoiceItem.getRate(),
                                                               recurringInvoiceItem.getCurrency()));
            }
        }

        ((ZombieControl)invoicePaymentApi).addResult("getInvoice", invoice);
        InvoiceCreationEvent event = new MockInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                invoice.getBalance(), invoice.getCurrency(),
                invoice.getInvoiceDate(),
                context.getUserToken());
        
        eventBus.post(event);
        return invoice;
    }

    public Account createTestAccount(String email) throws Exception {
        final String name = "First" + RandomStringUtils.randomAlphanumeric(5) + " " + "Last" + RandomStringUtils.randomAlphanumeric(5);
        final String externalKey = RandomStringUtils.randomAlphanumeric(10);

        Account account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ZombieControl zombie = (ZombieControl) account;
        zombie.addResult("getId", UUID.randomUUID());
        zombie.addResult("getExternalKey", externalKey);
        zombie.addResult("getName", name);
        zombie.addResult("getFirstNameLength", 10);
        zombie.addResult("getPhone", "123-456-7890");
        zombie.addResult("getEmail", email);
        zombie.addResult("getCurrency", Currency.USD);
        zombie.addResult("getBillCycleDay", 1);
        zombie.addResult("isMigrated", false);
        zombie.addResult("isNotifiedForInvoices", false);        
        zombie.addResult("getTimeZone", BrainDeadProxyFactory.ZOMBIE_VOID);
        zombie.addResult("getLocale", BrainDeadProxyFactory.ZOMBIE_VOID);        
        zombie.addResult("getAddress1", BrainDeadProxyFactory.ZOMBIE_VOID);
        zombie.addResult("getAddress2", BrainDeadProxyFactory.ZOMBIE_VOID);        
        zombie.addResult("getCompanyName", BrainDeadProxyFactory.ZOMBIE_VOID);
        zombie.addResult("getCity", BrainDeadProxyFactory.ZOMBIE_VOID);        
        zombie.addResult("getStateOrProvince", BrainDeadProxyFactory.ZOMBIE_VOID);
        zombie.addResult("getCountry", BrainDeadProxyFactory.ZOMBIE_VOID);        
        zombie.addResult("getPostalCode", BrainDeadProxyFactory.ZOMBIE_VOID);
        zombie.addResult("getPhone", BrainDeadProxyFactory.ZOMBIE_VOID);        
        zombie.addResult("getPaymentMethodId", BrainDeadProxyFactory.ZOMBIE_VOID);
        
        ((ZombieControl)accountUserApi).addResult("getAccountById", account);
        ((ZombieControl)accountUserApi).addResult("getAccountByKey", account);
        ((ZombieControl)accountUserApi).addResult("updateAccount", BrainDeadProxyFactory.ZOMBIE_VOID);        
        //updateAccount

        PaymentMethodPlugin pm = new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), true, null);
        addTestPaymentMethod(account, pm);
        return account;
    }
    
    private void addTestPaymentMethod(Account account, PaymentMethodPlugin paymentMethodInfo) throws Exception {
        UUID paymentMethodId = paymentApi.addPaymentMethod(PaymentTestModuleWithMocks.PLUGIN_TEST_NAME, account, true, paymentMethodInfo, context);
        ZombieControl zombie = (ZombieControl) account;
        zombie.addResult("getPaymentMethodId", paymentMethodId); 
    }

}
