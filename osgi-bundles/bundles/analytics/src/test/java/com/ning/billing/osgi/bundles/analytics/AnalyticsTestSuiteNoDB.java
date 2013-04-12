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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.osgi.bundles.analytics.api.BusinessEntityBase;
import com.ning.billing.osgi.bundles.analytics.dao.TestCallContext;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao.BusinessInvoiceItemType;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessModelDaoBase.ReportGroup;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillDataSource;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

import com.google.common.collect.ImmutableList;

public abstract class AnalyticsTestSuiteNoDB {

    protected final Logger logger = LoggerFactory.getLogger(AnalyticsTestSuiteNoDB.class);

    protected final Long accountRecordId = 1L;
    protected final Long subscriptionEventRecordId = 2L;
    protected final Long invoiceRecordId = 3L;
    protected final Long invoiceItemRecordId = 4L;
    protected final Long secondInvoiceItemRecordId = 24L;
    protected final Long invoicePaymentRecordId = 5L;
    protected final Long blockingStateRecordId = 6L;
    protected final Long fieldRecordId = 7L;
    protected final Long tagRecordId = 8L;
    protected final Long tenantRecordId = 9L;

    protected final ReportGroup reportGroup = ReportGroup.partner;
    protected final BusinessInvoiceItemType invoiceItemType = BusinessInvoiceItemType.INVOICE_ITEM_ADJUSTMENT;

    protected Account account;
    protected SubscriptionBundle bundle;
    protected Plan plan;
    protected PlanPhase phase;
    protected PriceList priceList;
    protected SubscriptionTransition subscriptionTransition;
    protected BlockingState blockingState;
    protected Invoice invoice;
    protected InvoiceItem invoiceItem;
    protected Boolean recognizable;
    protected InvoicePayment invoicePayment;
    protected PaymentAttempt paymentAttempt;
    protected PaymentMethod paymentMethod;
    protected Payment payment;
    protected Refund refund;
    protected CustomField customField;
    protected Tag tag;
    protected TagDefinition tagDefinition;
    protected AuditLog auditLog;
    protected CallContext callContext;
    protected OSGIKillbillLogService logService;
    protected OSGIKillbillAPI killbillAPI;
    protected OSGIKillbillDataSource killbillDataSource;

    protected void verifyBusinessEntityBase(final BusinessEntityBase businessEntityBase) {
        Assert.assertEquals(businessEntityBase.getCreatedBy(), auditLog.getUserName());
        Assert.assertEquals(businessEntityBase.getCreatedReasonCode(), auditLog.getReasonCode());
        Assert.assertEquals(businessEntityBase.getCreatedComments(), auditLog.getComment());
        Assert.assertEquals(businessEntityBase.getAccountId(), account.getId());
        Assert.assertEquals(businessEntityBase.getAccountName(), account.getName());
        Assert.assertEquals(businessEntityBase.getAccountExternalKey(), account.getExternalKey());
        Assert.assertEquals(businessEntityBase.getReportGroup(), reportGroup.toString());
    }

    protected void verifyBusinessModelDaoBase(final BusinessModelDaoBase businessModelDaoBase,
                                              final Long accountRecordId,
                                              final Long tenantRecordId) {
        Assert.assertEquals(businessModelDaoBase.getCreatedBy(), auditLog.getUserName());
        Assert.assertEquals(businessModelDaoBase.getCreatedReasonCode(), auditLog.getReasonCode());
        Assert.assertEquals(businessModelDaoBase.getCreatedComments(), auditLog.getComment());
        Assert.assertEquals(businessModelDaoBase.getAccountId(), account.getId());
        Assert.assertEquals(businessModelDaoBase.getAccountName(), account.getName());
        Assert.assertEquals(businessModelDaoBase.getAccountExternalKey(), account.getExternalKey());
        Assert.assertEquals(businessModelDaoBase.getAccountRecordId(), accountRecordId);
        Assert.assertEquals(businessModelDaoBase.getTenantRecordId(), tenantRecordId);
        Assert.assertEquals(businessModelDaoBase.getReportGroup(), reportGroup.toString());
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
        Mockito.when(account.getLocale()).thenReturn(UUID.randomUUID().toString().substring(0, 5));
        Mockito.when(account.getAddress1()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getAddress2()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getCompanyName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getCity()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getStateOrProvince()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getPostalCode()).thenReturn(UUID.randomUUID().toString().substring(0, 16));
        Mockito.when(account.getCountry()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(account.getPhone()).thenReturn(UUID.randomUUID().toString().substring(0, 25));
        Mockito.when(account.isMigrated()).thenReturn(true);
        Mockito.when(account.isNotifiedForInvoices()).thenReturn(true);
        Mockito.when(account.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 47, DateTimeZone.UTC));
        Mockito.when(account.getUpdatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 48, DateTimeZone.UTC));
        final UUID accountId = account.getId();

        bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(bundle.getAccountId()).thenReturn(accountId);
        Mockito.when(bundle.getExternalKey()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(bundle.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 48, DateTimeZone.UTC));
        final UUID bundleId = bundle.getId();

        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(product.isRetired()).thenReturn(true);
        Mockito.when(product.getCategory()).thenReturn(ProductCategory.STANDALONE);
        Mockito.when(product.getCatalogName()).thenReturn(UUID.randomUUID().toString());

        plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getProduct()).thenReturn(product);
        Mockito.when(plan.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(plan.isRetired()).thenReturn(true);
        Mockito.when(plan.getBillingPeriod()).thenReturn(BillingPeriod.QUARTERLY);
        Mockito.when(plan.getEffectiveDateForExistingSubscriptons()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 59, DateTimeZone.UTC).toDate());
        final String planName = plan.getName();

        phase = Mockito.mock(PlanPhase.class);
        Mockito.when(phase.getBillingPeriod()).thenReturn(BillingPeriod.QUARTERLY);
        Mockito.when(phase.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(phase.getPlan()).thenReturn(plan);
        Mockito.when(phase.getPhaseType()).thenReturn(PhaseType.DISCOUNT);
        final String phaseName = phase.getName();

        priceList = Mockito.mock(PriceList.class);
        Mockito.when(priceList.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(priceList.isRetired()).thenReturn(false);

        subscriptionTransition = Mockito.mock(SubscriptionTransition.class);
        Mockito.when(subscriptionTransition.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionTransition.getBundleId()).thenReturn(bundleId);
        Mockito.when(subscriptionTransition.getNextState()).thenReturn(SubscriptionState.ACTIVE);
        Mockito.when(subscriptionTransition.getNextPlan()).thenReturn(plan);
        Mockito.when(subscriptionTransition.getNextPhase()).thenReturn(phase);
        Mockito.when(subscriptionTransition.getNextPriceList()).thenReturn(priceList);
        Mockito.when(subscriptionTransition.getRequestedTransitionTime()).thenReturn(new DateTime(2010, 1, 2, 3, 4, 5, DateTimeZone.UTC));
        Mockito.when(subscriptionTransition.getEffectiveTransitionTime()).thenReturn(new DateTime(2011, 2, 3, 4, 5, 6, DateTimeZone.UTC));
        Mockito.when(subscriptionTransition.getTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(subscriptionTransition.getNextEventCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 49, DateTimeZone.UTC));
        Mockito.when(subscriptionTransition.getNextEventId()).thenReturn(UUID.randomUUID());
        final UUID subscriptionId = subscriptionTransition.getSubscriptionId();
        final UUID nextEventId = subscriptionTransition.getNextEventId();

        blockingState = Mockito.mock(BlockingState.class);
        Mockito.when(blockingState.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(blockingState.getBlockedId()).thenReturn(bundleId);
        Mockito.when(blockingState.getStateName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(blockingState.getType()).thenReturn(Type.SUBSCRIPTION_BUNDLE);
        Mockito.when(blockingState.getTimestamp()).thenReturn(new DateTime(2010, 2, 2, 4, 22, 22, DateTimeZone.UTC));
        Mockito.when(blockingState.isBlockBilling()).thenReturn(true);
        Mockito.when(blockingState.isBlockChange()).thenReturn(false);
        Mockito.when(blockingState.isBlockEntitlement()).thenReturn(true);
        Mockito.when(blockingState.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(blockingState.getService()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(blockingState.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 0, DateTimeZone.UTC));
        final UUID blockingStateId = blockingState.getId();

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
        Mockito.when(invoiceItem.getBundleId()).thenReturn(bundleId);
        Mockito.when(invoiceItem.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(invoiceItem.getPlanName()).thenReturn(planName);
        Mockito.when(invoiceItem.getPhaseName()).thenReturn(phaseName);
        Mockito.when(invoiceItem.getRate()).thenReturn(new BigDecimal("1203"));
        Mockito.when(invoiceItem.getLinkedItemId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 51, DateTimeZone.UTC));
        final UUID invoiceItemId = invoiceItem.getId();

        recognizable = false;

        final UUID invoiceId = UUID.randomUUID();

        invoicePayment = Mockito.mock(InvoicePayment.class);
        Mockito.when(invoicePayment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getType()).thenReturn(InvoicePaymentType.ATTEMPT);
        Mockito.when(invoicePayment.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(invoicePayment.getPaymentDate()).thenReturn(new DateTime(2003, 4, 12, 3, 34, 52, DateTimeZone.UTC));
        Mockito.when(invoicePayment.getAmount()).thenReturn(BigDecimal.ONE);
        Mockito.when(invoicePayment.getCurrency()).thenReturn(Currency.MXN);
        Mockito.when(invoicePayment.getLinkedInvoicePaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getPaymentCookieId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoicePayment.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 53, DateTimeZone.UTC));
        final UUID invoicePaymentId = invoicePayment.getId();

        invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getId()).thenReturn(invoiceId);
        Mockito.when(invoice.getInvoiceItems()).thenReturn(ImmutableList.<InvoiceItem>of(invoiceItem));
        Mockito.when(invoice.getNumberOfItems()).thenReturn(1);
        Mockito.when(invoice.getPayments()).thenReturn(ImmutableList.<InvoicePayment>of(invoicePayment));
        Mockito.when(invoice.getNumberOfPayments()).thenReturn(1);
        Mockito.when(invoice.getAccountId()).thenReturn(accountId);
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
        Mockito.when(invoice.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 53, DateTimeZone.UTC));

        paymentAttempt = Mockito.mock(PaymentAttempt.class);
        Mockito.when(paymentAttempt.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(paymentAttempt.getEffectiveDate()).thenReturn(new DateTime(2019, 12, 30, 10, 10, 10, DateTimeZone.UTC));
        Mockito.when(paymentAttempt.getGatewayErrorCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentAttempt.getGatewayErrorMsg()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentAttempt.getPaymentStatus()).thenReturn(PaymentStatus.SUCCESS);
        Mockito.when(paymentAttempt.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 54, DateTimeZone.UTC));

        final PaymentMethodPlugin paymentMethodPlugin = Mockito.mock(PaymentMethodPlugin.class);
        Mockito.when(paymentMethodPlugin.getExternalPaymentMethodId()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentMethodPlugin.isDefaultPaymentMethod()).thenReturn(true);

        paymentMethod = Mockito.mock(PaymentMethod.class);
        Mockito.when(paymentMethod.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(paymentMethod.getAccountId()).thenReturn(accountId);
        Mockito.when(paymentMethod.isActive()).thenReturn(true);
        Mockito.when(paymentMethod.getPluginName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(paymentMethod.getPluginDetail()).thenReturn(paymentMethodPlugin);
        Mockito.when(paymentMethod.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 55, DateTimeZone.UTC));
        final UUID paymentMethodId = paymentMethod.getId();

        payment = Mockito.mock(Payment.class);
        Mockito.when(payment.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(payment.getAccountId()).thenReturn(accountId);
        Mockito.when(payment.getInvoiceId()).thenReturn(invoiceId);
        Mockito.when(payment.getPaymentMethodId()).thenReturn(paymentMethodId);
        Mockito.when(payment.getPaymentNumber()).thenReturn(1);
        Mockito.when(payment.getAmount()).thenReturn(new BigDecimal("199999"));
        Mockito.when(payment.getPaidAmount()).thenReturn(new BigDecimal("199998"));
        Mockito.when(payment.getEffectiveDate()).thenReturn(new DateTime(2019, 2, 3, 12, 12, 12, DateTimeZone.UTC));
        Mockito.when(payment.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(payment.getPaymentStatus()).thenReturn(PaymentStatus.AUTO_PAY_OFF);
        Mockito.when(payment.getAttempts()).thenReturn(ImmutableList.<PaymentAttempt>of(paymentAttempt));
        Mockito.when(payment.getExtFirstPaymentIdRef()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(payment.getExtSecondPaymentIdRef()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(payment.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 56, DateTimeZone.UTC));

        refund = Mockito.mock(Refund.class);
        Mockito.when(refund.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(refund.getPaymentId()).thenReturn(UUID.randomUUID());
        Mockito.when(refund.isAdjusted()).thenReturn(true);
        Mockito.when(refund.getRefundAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(refund.getCurrency()).thenReturn(Currency.BRL);
        Mockito.when(refund.getEffectiveDate()).thenReturn(new DateTime(2015, 2, 2, 10, 56, 5, DateTimeZone.UTC));

        customField = Mockito.mock(CustomField.class);
        Mockito.when(customField.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(customField.getObjectId()).thenReturn(UUID.randomUUID());
        Mockito.when(customField.getObjectType()).thenReturn(ObjectType.TENANT);
        Mockito.when(customField.getFieldName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(customField.getFieldValue()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(customField.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 57, DateTimeZone.UTC));
        final UUID fieldId = customField.getId();

        tag = Mockito.mock(Tag.class);
        Mockito.when(tag.getObjectId()).thenReturn(UUID.randomUUID());
        Mockito.when(tag.getObjectType()).thenReturn(ObjectType.ACCOUNT);
        Mockito.when(tag.getTagDefinitionId()).thenReturn(UUID.randomUUID());
        Mockito.when(tag.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 58, DateTimeZone.UTC));
        final UUID tagId = tag.getId();

        tagDefinition = Mockito.mock(TagDefinition.class);
        Mockito.when(tagDefinition.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(tagDefinition.getName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(tagDefinition.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(tagDefinition.isControlTag()).thenReturn(false);
        Mockito.when(tagDefinition.getApplicableObjectTypes()).thenReturn(ImmutableList.<ObjectType>of(ObjectType.INVOICE));
        Mockito.when(tagDefinition.getCreatedDate()).thenReturn(new DateTime(2016, 1, 22, 10, 56, 59, DateTimeZone.UTC));

        auditLog = Mockito.mock(AuditLog.class);
        Mockito.when(auditLog.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(auditLog.getChangeType()).thenReturn(ChangeType.INSERT);
        Mockito.when(auditLog.getUserName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getCreatedDate()).thenReturn(new DateTime(2012, 12, 31, 23, 59, 59, DateTimeZone.UTC));
        Mockito.when(auditLog.getReasonCode()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getUserToken()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(auditLog.getComment()).thenReturn(UUID.randomUUID().toString());

        // Real class for the binding to work with JDBI
        callContext = new TestCallContext();
        final UUID tenantId = callContext.getTenantId();

        final RecordIdApi recordIdApi = Mockito.mock(RecordIdApi.class);
        Mockito.when(recordIdApi.getRecordId(accountId, ObjectType.ACCOUNT, callContext)).thenReturn(accountRecordId);
        Mockito.when(recordIdApi.getRecordId(nextEventId, ObjectType.SUBSCRIPTION_EVENT, callContext)).thenReturn(subscriptionEventRecordId);
        Mockito.when(recordIdApi.getRecordId(invoiceId, ObjectType.INVOICE, callContext)).thenReturn(invoiceRecordId);
        Mockito.when(recordIdApi.getRecordId(invoiceItemId, ObjectType.INVOICE_ITEM, callContext)).thenReturn(invoiceItemRecordId);
        Mockito.when(recordIdApi.getRecordId(invoicePaymentId, ObjectType.INVOICE_PAYMENT, callContext)).thenReturn(invoicePaymentRecordId);
        Mockito.when(recordIdApi.getRecordId(blockingStateId, ObjectType.BLOCKING_STATES, callContext)).thenReturn(blockingStateRecordId);
        Mockito.when(recordIdApi.getRecordId(fieldId, ObjectType.CUSTOM_FIELD, callContext)).thenReturn(fieldRecordId);
        Mockito.when(recordIdApi.getRecordId(tagId, ObjectType.TAG, callContext)).thenReturn(tagRecordId);
        Mockito.when(recordIdApi.getRecordId(tenantId, ObjectType.TENANT, callContext)).thenReturn(tenantRecordId);

        killbillAPI = Mockito.mock(OSGIKillbillAPI.class);
        Mockito.when(killbillAPI.getRecordIdApi()).thenReturn(recordIdApi);

        killbillDataSource = Mockito.mock(OSGIKillbillDataSource.class);
    }
}
