/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.ParentInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.DefaultControlTag;
import org.killbill.billing.util.tag.Tag;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;


//
// Regression suite to verify that our optimized logic to either read invoice state per account recordId or per invoiceId
// produces the same result.
//
public class TestInvoiceDaoHelper extends InvoiceTestSuiteWithEmbeddedDB {

    @Inject
    private CacheControllerDispatcher cacheControllerDispatcher;

    private Account account;
    private InternalCallContext internalAccountContext;
    private EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private DateTime now;
    private LocalDate today;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }

        account = invoiceUtil.createAccount(callContext);
        now = clock.getNow(account.getTimeZone());
        today = now.toLocalDate();
        internalAccountContext = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    @Test(groups = "slow")
    public void testPopulateChildrenSimple() throws Exception {
        final UUID accountId = account.getId();
        final Invoice inputInvoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(inputInvoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test", "test-plan", "test-phase", null,
                                                                 today, today, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);

        inputInvoice.addInvoiceItem(invoiceItem);
        invoiceUtil.createInvoice(inputInvoice, internalAccountContext);

        final List<Tag> tags = ImmutableList.of();
        final InvoiceModelDao invoice1 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByInvoiceId(invoice1, tags);

        final InvoiceModelDao invoice2 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByAccountRecordId(invoice2, tags);

        Assert.assertEquals(invoice1, invoice2);
    }

    @Test(groups = "slow")
    public void testPopulateChildrenWithPayments() throws Exception {
        final UUID accountId = account.getId();
        final Invoice inputInvoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(inputInvoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test", "test-plan", "test-phase", null,
                                                                 today, today, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);

        inputInvoice.addInvoiceItem(invoiceItem);
        invoiceUtil.createInvoice(inputInvoice, internalAccountContext);

        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), inputInvoice.getId(), new DateTime(), BigDecimal.TEN, Currency.USD, Currency.USD, null, true);
        invoiceUtil.createPayment(payment, internalAccountContext);

        final List<Tag> tags = ImmutableList.of();
        final InvoiceModelDao invoice1 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByInvoiceId(invoice1, tags);

        final InvoiceModelDao invoice2 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByAccountRecordId(invoice2, tags);

        Assert.assertEquals(new DefaultInvoice(invoice1).getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(new DefaultInvoice(invoice1).getPaidAmount().compareTo(BigDecimal.TEN), 0);

        Assert.assertEquals(new DefaultInvoice(invoice2).getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals(new DefaultInvoice(invoice2).getPaidAmount().compareTo(BigDecimal.TEN), 0);

        Assert.assertEquals(invoice1, invoice2);
    }


    @Test(groups = "slow")
    public void testPopulateChildrenWithTrackingIds() throws Exception {
        final UUID accountId = account.getId();
        final Invoice inputInvoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(inputInvoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test", "test-plan", "test-phase", null,
                                                                 today, today, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);

        inputInvoice.addInvoiceItem(invoiceItem);
        invoiceUtil.createInvoice(inputInvoice, internalAccountContext);

        final InvoiceTrackingSqlDao trackingSqlDao = dbi.onDemand(InvoiceTrackingSqlDao.class);
        trackingSqlDao.create(ImmutableList.of(new InvoiceTrackingModelDao("12345", inputInvoice.getId(), UUID.randomUUID(), "foo", today)), internalAccountContext);


        final List<Tag> tags = ImmutableList.of();
        final InvoiceModelDao invoice1 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByInvoiceId(invoice1, tags);

        final InvoiceModelDao invoice2 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByAccountRecordId(invoice2, tags);

        Assert.assertEquals(new DefaultInvoice(invoice1).getTrackingIds().size(), 1);
        Assert.assertEquals(new DefaultInvoice(invoice2).getTrackingIds().size(), 1);

        Assert.assertEquals(invoice1, invoice2);
    }



    @Test(groups = "slow")
    public void testPopulateChildrenWith_WRITTEN_OFF() throws Exception {
        final UUID accountId = account.getId();
        final Invoice inputInvoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(inputInvoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test", "test-plan", "test-phase", null,
                                                                 today, today, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);

        inputInvoice.addInvoiceItem(invoiceItem);

        invoiceUtil.createInvoice(inputInvoice, internalAccountContext);

        final List<Tag> tags = ImmutableList.of(new DefaultControlTag(ControlTagType.WRITTEN_OFF, ObjectType.INVOICE, inputInvoice.getId(), clock.getUTCNow()));
        final InvoiceModelDao invoice1 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByInvoiceId(invoice1, tags);

        final InvoiceModelDao invoice2 = getRawInvoice(inputInvoice.getId(), internalAccountContext);
        populateChildrenByAccountRecordId(invoice2, tags);

        Assert.assertEquals(invoice1, invoice2);
        Assert.assertEquals((new DefaultInvoice(invoice1)).getBalance().compareTo(BigDecimal.ZERO), 0);
        Assert.assertEquals((new DefaultInvoice(invoice2)).getBalance().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testPopulateChildrenWithParent() throws Exception {

        final Account parentAccount = invoiceUtil.createAccount(callContext);

        final UUID childAccountId = account.getId();
        final UUID parentAccountId = parentAccount.getId();

        final InternalCallContext parentContext = internalCallContextFactory.createInternalCallContext(parentAccountId, callContext);
        final InvoiceModelDao parentInvoice = new InvoiceModelDao(parentAccountId, today, account.getCurrency(), InvoiceStatus.DRAFT, true);
        final InvoiceItem parentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), now, parentInvoice.getId(), parentAccountId, childAccountId, BigDecimal.TEN, account.getCurrency(), "");
        parentInvoice.addInvoiceItem(new InvoiceItemModelDao(parentInvoiceItem));
        invoiceUtil.createInvoice(new DefaultInvoice(parentInvoice), parentContext);

        final UUID accountId = account.getId();
        final Invoice childInvoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(childInvoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test", "test-plan", "test-phase", null,
                                                                 today, today, BigDecimal.TEN, BigDecimal.TEN, Currency.USD);

        childInvoice.addInvoiceItem(invoiceItem);

        invoiceUtil.createInvoice(childInvoice, internalAccountContext);

        InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(parentInvoice.getId(), childInvoice.getId(), childAccountId);
        invoiceDao.createParentChildInvoiceRelation(invoiceRelation, internalAccountContext);

        ////

        final List<Tag> tags = ImmutableList.of();
        final InvoiceModelDao invoice1 = getRawInvoice(childInvoice.getId(), internalAccountContext);
        populateChildrenByInvoiceId(invoice1, tags);

        final InvoiceModelDao invoice2 = getRawInvoice(childInvoice.getId(), internalAccountContext);
        populateChildrenByAccountRecordId(invoice2, tags);

        Assert.assertEquals(invoice1, invoice2);
    }

    private InvoiceModelDao getRawInvoice(final UUID invoiceId, final InternalTenantContext context) {
        final InvoiceSqlDao dao = dbi.onDemand(InvoiceSqlDao.class);
        return dao.getById(invoiceId.toString(), context);
    }

    private void populateChildrenByAccountRecordId(final InvoiceModelDao invoice, final List<Tag> tags) {
        transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                invoiceDaoHelper.populateChildren(ImmutableList.of(invoice), tags, entitySqlDaoWrapperFactory, internalAccountContext);
                return null;
            }
        });
    }

    private void populateChildrenByInvoiceId(final InvoiceModelDao invoice, final List<Tag> tags) {
        transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                invoiceDaoHelper.populateChildren(invoice, tags, entitySqlDaoWrapperFactory, internalAccountContext);
                return null;
            }
        });
    }
}
