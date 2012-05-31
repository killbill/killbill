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
package com.ning.billing.payment.dao;

import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterSuite;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class TestPaymentDao {
    
    static private CallContext context = new TestCallContext("PaymentTests");
    
    private PaymentDao paymentDao;
    private MysqlTestingHelper helper;
    private IDBI dbi;
    private Clock clock;
    
    @BeforeSuite(groups = { "slow"})
    public void startMysql() throws IOException {
        final String paymentddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        
        clock = new DefaultClock();
        
        setupDb();
        
        helper.startMysql();
        helper.initDb(paymentddl);
        helper.initDb(utilddl);

        paymentDao = new AuditedPaymentDao(dbi);
    }
    
    private void setupDb() {
        helper = new MysqlTestingHelper();
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
    }

    @AfterSuite(groups = { "slow"})
    public void stopMysql() {
        helper.stopMysql();
    }
    
    @BeforeTest(groups = { "slow"})
    public void cleanupDb() {
        helper.cleanupAllTables();
    }
    
    
    
    @Test(groups={"slow"})
    public void testUpdateStatus() {
        
        UUID accountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID(); 
        BigDecimal amount = new BigDecimal(13);
        Currency currency = Currency.USD;
        DateTime effectiveDate = clock.getUTCNow();
        
        PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, amount, currency, effectiveDate);
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId());
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, context);
        
        PaymentStatus paymentStatus = PaymentStatus.SUCCESS;
        String paymentError = "No error";
        
        paymentDao.updateStatusForPaymentWithAttempt(payment.getId(), paymentStatus, paymentError, attempt.getId(), context);
        
        List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);        
        assertEquals(savedPayment.getInvoiceId(), invoiceId);        
        assertEquals(savedPayment.getPaymentMethodId(), null);         
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);        
        assertEquals(savedPayment.getCurrency(), currency);         
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0); 
        assertEquals(savedPayment.getPaymentNumber(), new Integer(1));
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.SUCCESS); 
        
        List<PaymentAttemptModelDao> attempts =  paymentDao.getAttemptsForPayment(payment.getId());
        assertEquals(attempts.size(), 1);
        PaymentAttemptModelDao savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId); 
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedAttempt.getPaymentError(), paymentError);        
    }
    
    @Test(groups={"slow"})
    public void testPaymentWithAttempt() {
        
        UUID accountId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID(); 
        BigDecimal amount = new BigDecimal(13);
        Currency currency = Currency.USD;
        DateTime effectiveDate = clock.getUTCNow();
        
        PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, amount, currency, effectiveDate);
        PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId());
        
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, context);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);        
        assertEquals(savedPayment.getInvoiceId(), invoiceId);        
        assertEquals(savedPayment.getPaymentMethodId(), null);         
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);        
        assertEquals(savedPayment.getCurrency(), currency);         
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0); 
        assertEquals(savedPayment.getPaymentNumber(), new Integer(1));
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN); 

        
        PaymentAttemptModelDao savedAttempt = paymentDao.getPaymentAttempt(attempt.getId());
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId); 
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.UNKNOWN);

        List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);        
        assertEquals(savedPayment.getInvoiceId(), invoiceId);        
        assertEquals(savedPayment.getPaymentMethodId(), null);         
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);        
        assertEquals(savedPayment.getCurrency(), currency);         
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0); 
        assertEquals(savedPayment.getPaymentNumber(), new Integer(1));
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN); 
        
        List<PaymentAttemptModelDao> attempts =  paymentDao.getAttemptsForPayment(payment.getId());
        assertEquals(attempts.size(), 1);
        savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId); 
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.UNKNOWN);
        
    }

    @Test(groups={"slow"})
    public void testPaymentMethod() {
        
        UUID paymentMethodId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String pluginName = "nobody";
        Boolean isActive = Boolean.TRUE;
        
        PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, accountId, pluginName, isActive);
        
        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, context);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);        
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);
        
        List<PaymentMethodModelDao> result =  paymentDao.getPaymentMethods(accountId);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);        
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);
        
    }
}
