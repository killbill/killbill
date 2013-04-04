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

package com.ning.billing.osgi.bundles.analytics;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

import com.google.common.collect.ImmutableList;

public abstract class AnalyticsTestSuiteNoDB {

    protected Account account;
    protected Invoice invoice;
    protected InvoiceItem invoiceItem;
    protected InvoicePayment invoicePayment;
    protected PaymentAttempt paymentAttempt;
    protected Payment payment;
    protected CustomField customField;
    protected Tag tag;
    protected TagDefinition tagDefinition;
    protected AuditLog auditLog;

    protected void verifyBusinessModelDaoBase(final BusinessModelDaoBase businessModelDaoBase) {
        //Assert.assertEquals(businessModelDaoBase.getRecordId(), /* TODO */);
        Assert.assertEquals(businessModelDaoBase.getCreatedDate(), account.getCreatedDate());
        Assert.assertEquals(businessModelDaoBase.getCreatedBy(), auditLog.getUserName());
        Assert.assertEquals(businessModelDaoBase.getCreatedReasonCode(), auditLog.getReasonCode());
        Assert.assertEquals(businessModelDaoBase.getCreatedComments(), auditLog.getComment());
        Assert.assertEquals(businessModelDaoBase.getAccountId(), account.getId());
        Assert.assertEquals(businessModelDaoBase.getAccountName(), account.getName());
        Assert.assertEquals(businessModelDaoBase.getAccountExternalKey(), account.getExternalKey());
        //Assert.assertEquals(businessModelDaoBase.getAccountRecordId(), /* TODO */);
        //Assert.assertEquals(businessModelDaoBase.getTenantRecordId(), /* TODO */);
    }

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getExternalKey()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getFirstNameLength()).thenReturn(4);
        Mockito.when(account.getEmail()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getBillCycleDayLocal()).thenReturn(2);
        Mockito.when(account.getCurrency()).thenReturn(Currency.BRL);
        Mockito.when(account.getPaymentMethodId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.forID("Europe/London"));
        Mockito.when(account.getLocale()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getAddress1()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getAddress2()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getCompanyName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getCity()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getStateOrProvince()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getPostalCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getCountry()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getPhone()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.isMigrated()).thenReturn(true);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(true);

        invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getInvoiceItemType()).thenReturn(InvoiceItemType.EXTERNAL_CHARGE);
        Mockito.when(invoiceItem.getInvoiceId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getAccountId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getStartDate()).thenReturn(new LocalDate(1999, 9, 9));
        Mockito.when(invoiceItem.getEndDate()).thenReturn(new LocalDate(2048, 1, 1));
        Mockito.when(invoiceItem.getAmount()).thenReturn(new BigDecimal("12000"));
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.EUR);
        Mockito.when(invoiceItem.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getBundleId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getPlanName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getPhaseName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getRate()).thenReturn(new BigDecimal("1203"));
        Mockito.when(invoiceItem.getLinkedItemId()).thenReturn(UUID.randomUUID());

        invoicePayment = Mockito.mock(InvoicePayment.class);
        Mockito.when(invoicePayment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        Mockito.when(invoicePayment.getInvoiceId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getPaymentDate()).thenReturn(new DateTime(2003, 4, 12, 3, 34, 52, DateTimeZone.UTC));
        Mockito.when(invoicePayment.getAmount()).thenReturn(BigDecimal.ONE);
        Mockito.when(invoicePayment.getCurrency()).thenReturn(Currency.MXN);
        Mockito.when(invoicePayment.getLinkedInvoicePaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getPaymentCookieId()).thenReturn(UUID.randomUUID());

        invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getInvoiceItems()).thenReturn(ImmutableList.<InvoiceItem>of(invoiceItem));
        Mockito.when(invoice.getNumberOfItems()).thenReturn(6);
        Mockito.when(invoice.getPayments()).thenReturn(ImmutableList.<InvoicePayment>of(invoicePayment));
        Mockito.when(invoice.getNumberOfPayments()).thenReturn(3);
        Mockito.when(invoice.getAccountId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getInvoiceNumber()).thenReturn(42);
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new LocalDate(1954, 12, 1));
        Mockito.when(invoice.getTargetDate()).thenReturn(new LocalDate(2017, 3, 4));
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.AUD);
        Mockito.when(invoice.getPaidAmount()).thenReturn(BigDecimal.ZERO);
        Mockito.when(invoice.getOriginalChargedAmount()).thenReturn(new BigDecimal("1922"));
        Mockito.when(invoice.getChargedAmount()).thenReturn(new BigDecimal("100293"));
        Mockito.when(invoice.getCBAAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getTotalAdjAmount()).thenReturn(new BigDecimal("192"));
        Mockito.when(invoice.getCreditAdjAmount()).thenReturn(new BigDecimal("283"));
        Mockito.when(invoice.getRefundAdjAmount()).thenReturn(new BigDecimal("384"));
        Mockito.when(invoice.getBalance()).thenReturn(new BigDecimal("18376"));
        Mockito.when(invoice.isMigrationInvoice()).thenReturn(false);

        paymentAttempt = Mockito.mock(PaymentAttempt.class);
        Mockito.when(paymentAttempt.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(paymentAttempt.getEffectiveDate()).thenReturn(new DateTime(2019, 12, 30, 10, 10, 10, DateTimeZone.UTC));
        Mockito.when(paymentAttempt.getGatewayErrorCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentAttempt.getGatewayErrorMsg()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentAttempt.getPaymentStatus()).thenReturn(PaymentStatus.SUCCESS);

        payment = Mockito.mock(Payment.class);
        Mockito.when(payment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getAccountId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getInvoiceId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentMethodId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getPaymentNumber()).thenReturn(1);
        Mockito.when(payment.getAmount()).thenReturn(new BigDecimal("199999"));
        Mockito.when(payment.getPaidAmount()).thenReturn(new BigDecimal("199998"));
        Mockito.when(payment.getEffectiveDate()).thenReturn(new DateTime(2019, 2, 3, 12, 12, 12, DateTimeZone.UTC));
        Mockito.when(payment.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(payment.getPaymentStatus()).thenReturn(PaymentStatus.AUTO_PAY_OFF);
        Mockito.when(payment.getAttempts()).thenReturn(ImmutableList.<PaymentAttempt>of(paymentAttempt));
        Mockito.when(payment.getExtFirstPaymentIdRef()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(payment.getExtSecondPaymentIdRef()).thenReturn(UUID.randomUUID().toString());

        customField = Mockito.mock(CustomField.class);
        Mockito.when(customField.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(customField.getObjectId()).thenReturn(UUID.randomUUID());
        Mockito.when(customField.getObjectType()).thenReturn(ObjectType.TENANT);
        Mockito.when(customField.getFieldName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(customField.getFieldValue()).thenReturn(UUID.randomUUID().toString());

        tag = Mockito.mock(Tag.class);
        Mockito.when(tag.getObjectId()).thenReturn(UUID.randomUUID());
        Mockito.when(tag.getObjectType()).thenReturn(ObjectType.ACCOUNT);
        Mockito.when(tag.getTagDefinitionId()).thenReturn(UUID.randomUUID());

        tagDefinition = Mockito.mock(TagDefinition.class);
        Mockito.when(tagDefinition.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(tagDefinition.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(tagDefinition.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(tagDefinition.isControlTag()).thenReturn(false);
        Mockito.when(tagDefinition.getApplicableObjectTypes()).thenReturn(ImmutableList.<ObjectType>of(ObjectType.INVOICE));

        auditLog = Mockito.mock(AuditLog.class);
        Mockito.when(auditLog.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(auditLog.getChangeType()).thenReturn(ChangeType.INSERT);
        Mockito.when(auditLog.getUserName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getCreatedDate()).thenReturn(new DateTime(2012, 31, 31, 23, 59, 59, DateTimeZone.UTC));
        Mockito.when(auditLog.getReasonCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getUserToken()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getComment()).thenReturn(UUID.randomUUID().toString());
    }
}
