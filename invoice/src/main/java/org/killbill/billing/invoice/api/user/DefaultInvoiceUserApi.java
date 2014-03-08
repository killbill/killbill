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

package org.killbill.billing.invoice.api.user;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceDispatcher;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.template.HtmlInvoiceGenerator;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultInvoiceUserApi implements InvoiceUserApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceUserApi.class);

    private final InvoiceDao dao;
    private final InvoiceDispatcher dispatcher;
    private final AccountInternalApi accountUserApi;
    private final TagInternalApi tagApi;
    private final HtmlInvoiceGenerator generator;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PersistentBus eventBus;

    @Inject
    public DefaultInvoiceUserApi(final InvoiceDao dao, final InvoiceDispatcher dispatcher, final AccountInternalApi accountUserApi, final PersistentBus eventBus,
                                 final TagInternalApi tagApi, final HtmlInvoiceGenerator generator, final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.dispatcher = dispatcher;
        this.accountUserApi = accountUserApi;
        this.tagApi = tagApi;
        this.generator = generator;
        this.internalCallContextFactory = internalCallContextFactory;
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final TenantContext context) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(dao.getInvoicesByAccount(internalCallContextFactory.createInternalTenantContext(accountId, context)),
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input);
                                                                        }
                                                                    }));
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final TenantContext context) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(dao.getInvoicesByAccount(fromDate, internalCallContextFactory.createInternalTenantContext(accountId, context)),
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input);
                                                                        }
                                                                    }));
    }

    @Override
    public Pagination<Invoice> getInvoices(final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<InvoiceModelDao, InvoiceApiException>() {
                                                  @Override
                                                  public Pagination<InvoiceModelDao> build() {
                                                      // Invoices will be shallow, i.e. won't contain items nor payments
                                                      return dao.get(offset, limit, internalCallContextFactory.createInternalTenantContext(context));
                                                  }
                                              },
                                              new Function<InvoiceModelDao, Invoice>() {
                                                  @Override
                                                  public Invoice apply(final InvoiceModelDao invoiceModelDao) {
                                                      return new DefaultInvoice(invoiceModelDao);
                                                  }
                                              }
                                             );
    }

    @Override
    public Pagination<Invoice> searchInvoices(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<InvoiceModelDao, AccountApiException>() {
                                                  @Override
                                                  public Pagination<InvoiceModelDao> build() {
                                                      // Invoices will be shallow, i.e. won't contain items nor payments
                                                      return dao.searchInvoices(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContext(context));
                                                  }
                                              },
                                              new Function<InvoiceModelDao, Invoice>() {
                                                  @Override
                                                  public Invoice apply(final InvoiceModelDao invoiceModelDao) {
                                                      return new DefaultInvoice(invoiceModelDao);
                                                  }
                                              }
                                             );
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final TenantContext context) {
        final BigDecimal result = dao.getAccountBalance(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final TenantContext context) {
        final BigDecimal result = dao.getAccountCBA(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
        return result == null ? BigDecimal.ZERO : result;
    }

    @Override
    public Invoice getInvoice(final UUID invoiceId, final TenantContext context) throws InvoiceApiException {
        return new DefaultInvoice(dao.getById(invoiceId, internalCallContextFactory.createInternalTenantContext(invoiceId, ObjectType.INVOICE, context)));
    }

    @Override
    public Invoice getInvoiceByNumber(final Integer number, final TenantContext context) throws InvoiceApiException {
        // The account record id will be populated in the DAO
        return new DefaultInvoice(dao.getByNumber(number, internalCallContextFactory.createInternalTenantContext(context)));
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final TenantContext context) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(dao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalCallContextFactory.createInternalTenantContext(accountId, context)),
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input);
                                                                        }
                                                                    }));
    }

    @Override
    public Invoice triggerInvoiceGeneration(final UUID accountId, final LocalDate targetDate, final boolean dryRun,
                                            final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);

        final Account account;
        try {
            account = accountUserApi.getAccountById(accountId, internalContext);
        } catch (AccountApiException e) {
            throw new InvoiceApiException(e, ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, e.toString());
        }

        final DateTime processingDateTime = targetDate.toDateTimeAtCurrentTime(account.getTimeZone());
        final Invoice result = dispatcher.processAccount(accountId, processingDateTime, dryRun, internalContext);
        if (result == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOTHING_TO_DO, accountId, targetDate);
        } else {
            return result;
        }
    }

    @Override
    public void tagInvoiceAsWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException, InvoiceApiException {
        // Note: the tagApi is audited
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
        tagApi.addTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), internalContext);

        // Retrieve the invoice for the account id
        final Invoice invoice = new DefaultInvoice(dao.getById(invoiceId, internalContext));
        // This is for overdue
        notifyBusOfInvoiceAdjustment(invoiceId, invoice.getAccountId(), internalContext);
    }

    @Override
    public void tagInvoiceAsNotWrittenOff(final UUID invoiceId, final CallContext context) throws TagApiException, InvoiceApiException {
        // Note: the tagApi is audited
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
        tagApi.removeTag(invoiceId, ObjectType.INVOICE, ControlTagType.WRITTEN_OFF.getId(), internalContext);

        // Retrieve the invoice for the account id
        final Invoice invoice = new DefaultInvoice(dao.getById(invoiceId, internalContext));
        // This is for overdue
        notifyBusOfInvoiceAdjustment(invoiceId, invoice.getAccountId(), internalContext);
    }

    @Override
    public InvoiceItem getExternalChargeById(final UUID externalChargeId, final TenantContext context) throws InvoiceApiException {
        final InvoiceItem externalChargeItem = InvoiceItemFactory.fromModelDao(dao.getExternalChargeById(externalChargeId, internalCallContextFactory.createInternalTenantContext(externalChargeId, ObjectType.INVOICE_ITEM, context)));
        if (externalChargeItem == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NO_SUCH_EXTERNAL_CHARGE, externalChargeId);
        }

        return new ExternalChargeInvoiceItem(externalChargeItem.getId(), externalChargeItem.getInvoiceId(), externalChargeItem.getAccountId(),
                                             externalChargeItem.getPlanName(), externalChargeItem.getStartDate(),
                                             externalChargeItem.getAmount(), externalChargeItem.getCurrency());
    }

    @Override
    public InvoiceItem insertExternalCharge(final UUID accountId, final BigDecimal amount, @Nullable final String description,
                                            final LocalDate effectiveDate, final Currency currency, final CallContext context) throws InvoiceApiException {
        return insertExternalChargeForInvoiceAndBundle(accountId, null, null, amount, description, effectiveDate, currency, context);
    }

    @Override
    public InvoiceItem insertExternalChargeForBundle(final UUID accountId, final UUID bundleId, final BigDecimal amount, @Nullable final String description,
                                                     final LocalDate effectiveDate, final Currency currency, final CallContext context) throws InvoiceApiException {
        return insertExternalChargeForInvoiceAndBundle(accountId, null, bundleId, amount, description, effectiveDate, currency, context);
    }

    @Override
    public InvoiceItem insertExternalChargeForInvoice(final UUID accountId, final UUID invoiceId, final BigDecimal amount, @Nullable final String description,
                                                      final LocalDate effectiveDate, final Currency currency, final CallContext context) throws InvoiceApiException {
        return insertExternalChargeForInvoiceAndBundle(accountId, invoiceId, null, amount, description, effectiveDate, currency, context);
    }

    @Override
    public InvoiceItem insertExternalChargeForInvoiceAndBundle(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId,
                                                               final BigDecimal amount, @Nullable final String description, final LocalDate effectiveDate,
                                                               final Currency currency, final CallContext context) throws InvoiceApiException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.EXTERNAL_CHARGE_AMOUNT_INVALID, amount);
        }

        final InvoiceItemModelDao externalCharge = dao.insertExternalCharge(accountId, invoiceId, bundleId, description, amount, effectiveDate, currency, internalCallContextFactory.createInternalCallContext(accountId, context));
        return InvoiceItemFactory.fromModelDao(externalCharge);
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId, final TenantContext context) throws InvoiceApiException {
        final InvoiceItem creditItem = InvoiceItemFactory.fromModelDao(dao.getCreditById(creditId, internalCallContextFactory.createInternalTenantContext(creditId, ObjectType.INVOICE_ITEM, context)));
        if (creditItem == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NO_SUCH_CREDIT, creditId);
        }

        return new CreditAdjInvoiceItem(creditItem.getId(), creditItem.getCreatedDate(), creditItem.getInvoiceId(), creditItem.getAccountId(),
                                        creditItem.getStartDate(), creditItem.getAmount().negate(), creditItem.getCurrency());
    }

    @Override
    public InvoiceItem insertCredit(final UUID accountId, final BigDecimal amount, final LocalDate effectiveDate,
                                    final Currency currency, final CallContext context) throws InvoiceApiException {
        return insertCreditForInvoice(accountId, null, amount, effectiveDate, currency, context);
    }

    @Override
    public InvoiceItem insertCreditForInvoice(final UUID accountId, final UUID invoiceId, final BigDecimal amount,
                                              final LocalDate effectiveDate, final Currency currency, final CallContext context) throws InvoiceApiException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.CREDIT_AMOUNT_INVALID, amount);
        }

        final InvoiceItemModelDao credit = dao.insertCredit(accountId, invoiceId, amount, effectiveDate, currency, internalCallContextFactory.createInternalCallContext(accountId, context));
        return InvoiceItemFactory.fromModelDao(credit);
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, final CallContext context) throws InvoiceApiException {
        return insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, null, null, context);
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, @Nullable final BigDecimal amount,
                                                   @Nullable final Currency currency, final CallContext context) throws InvoiceApiException {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_SHOULD_BE_POSITIVE, amount);
        }

        final InvoiceItemModelDao adjustment = dao.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, amount, currency, internalCallContextFactory.createInternalCallContext(accountId, context));
        return InvoiceItemFactory.fromModelDao(adjustment);
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final CallContext context) throws InvoiceApiException {
        dao.deleteCBA(accountId, invoiceId, invoiceItemId, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public String getInvoiceAsHTML(final UUID invoiceId, final TenantContext context) throws AccountApiException, IOException, InvoiceApiException {
        final Invoice invoice = getInvoice(invoiceId, context);
        if (invoice == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
        }

        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(invoiceId, ObjectType.INVOICE, context);
        final Account account = accountUserApi.getAccountById(invoice.getAccountId(), internalContext);

        // Check if this account has the MANUAL_PAY system tag
        boolean manualPay = false;
        final List<Tag> accountTags = tagApi.getTags(account.getId(), ObjectType.ACCOUNT, internalContext);
        for (final Tag tag : accountTags) {
            if (ControlTagType.MANUAL_PAY.getId().equals(tag.getTagDefinitionId())) {
                manualPay = true;
                break;
            }
        }

        return generator.generateInvoice(account, invoice, manualPay);
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final CallContext context) {
        dao.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    private void notifyBusOfInvoiceAdjustment(final UUID invoiceId, final UUID accountId, final InternalCallContext context) {
        try {
            eventBus.post(new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()));
        } catch (EventBusException e) {
            log.warn("Failed to post adjustment event for invoice " + invoiceId, e);
        }
    }
}
