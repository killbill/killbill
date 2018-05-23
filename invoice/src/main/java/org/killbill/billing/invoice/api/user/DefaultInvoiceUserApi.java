/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.api.user;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceDispatcher;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceApiHelper;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.api.WithAccountLock;
import org.killbill.billing.invoice.calculator.InvoiceCalculatorUtils;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.template.HtmlInvoice;
import org.killbill.billing.invoice.template.HtmlInvoiceGenerator;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultInvoiceUserApi implements InvoiceUserApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceUserApi.class);

    private final InvoiceDao dao;
    private final InvoiceDispatcher dispatcher;
    private final AccountInternalApi accountUserApi;
    private final TagInternalApi tagApi;
    private final InvoiceApiHelper invoiceApiHelper;
    private final HtmlInvoiceGenerator generator;
    private final InternalCallContextFactory internalCallContextFactory;
    private final PersistentBus eventBus;

    private final CatalogInternalApi catalogInternalApi;

    @Inject
    public DefaultInvoiceUserApi(final InvoiceDao dao,
                                 final InvoiceDispatcher dispatcher,
                                 final AccountInternalApi accountUserApi,
                                 final PersistentBus eventBus,
                                 final TagInternalApi tagApi,
                                 final InvoiceApiHelper invoiceApiHelper,
                                 final HtmlInvoiceGenerator generator,
                                 final CatalogInternalApi catalogInternalApi,
                                 final InternalCallContextFactory internalCallContextFactory) {
        this.dao = dao;
        this.dispatcher = dispatcher;
        this.accountUserApi = accountUserApi;
        this.tagApi = tagApi;
        this.invoiceApiHelper = invoiceApiHelper;
        this.generator = generator;
        this.catalogInternalApi = catalogInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.eventBus = eventBus;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, boolean includesMigrated, final boolean includeVoidedInvoices, final TenantContext context) {

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> invoicesByAccount = includesMigrated ?
                                                        dao.getAllInvoicesByAccount(includeVoidedInvoices, internalTenantContext) :
                                                        dao.getInvoicesByAccount(includeVoidedInvoices, internalTenantContext);

        return fromInvoiceModelDao(invoicesByAccount, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final boolean includeVoidedInvoices, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> invoicesByAccount = dao.getInvoicesByAccount(includeVoidedInvoices, fromDate, internalTenantContext);
        return fromInvoiceModelDao(invoicesByAccount, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Invoice getInvoiceByPayment(final UUID paymentId, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(paymentId, ObjectType.PAYMENT, context);
        final UUID invoiceId = dao.getInvoiceIdByPaymentId(paymentId, internalTenantContext);
        if (invoiceId == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, paymentId);
        }
        final InvoiceModelDao invoiceModelDao = dao.getById(invoiceId, internalTenantContext);
        return new DefaultInvoice(invoiceModelDao, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Pagination<Invoice> getInvoices(final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<InvoiceModelDao, InvoiceApiException>() {
                                                  @Override
                                                  public Pagination<InvoiceModelDao> build() {
                                                      // Invoices will be shallow, i.e. won't contain items nor payments
                                                      return dao.get(offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
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
                                                      return dao.searchInvoices(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
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
        return getInvoiceInternal(invoiceId, context);
    }

    private DefaultInvoice getInvoiceInternal(final UUID invoiceId, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(invoiceId, ObjectType.INVOICE, context);
        return new DefaultInvoice(dao.getById(invoiceId, internalTenantContext), getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Invoice getInvoiceByNumber(final Integer number, final TenantContext context) throws InvoiceApiException {
        // The account record id will be populated in the DAO
        final InternalTenantContext internalTenantContextWithoutAccountRecordId = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        final InvoiceModelDao invoice = dao.getByNumber(number, internalTenantContextWithoutAccountRecordId);

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
        return new DefaultInvoice(invoice, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> unpaidInvoicesByAccountId = dao.getUnpaidInvoicesByAccountId(accountId, upToDate, internalTenantContext);
        return fromInvoiceModelDao(unpaidInvoicesByAccountId, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Invoice triggerInvoiceGeneration(final UUID accountId, @Nullable final LocalDate targetDate, final DryRunArguments dryRunArguments,
                                            final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);

        final Invoice result = dispatcher.processAccount(true, accountId, targetDate, dryRunArguments, false, internalContext);
        if (result == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOTHING_TO_DO, accountId, targetDate != null ? targetDate : "null");
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
                                             externalChargeItem.getDescription(), externalChargeItem.getStartDate(), externalChargeItem.getEndDate(),
                                             externalChargeItem.getAmount(), externalChargeItem.getCurrency(), externalChargeItem.getItemDetails());
    }

    @Override
    public List<InvoiceItem> insertExternalCharges(final UUID accountId, final LocalDate effectiveDate, final Iterable<InvoiceItem> charges, final boolean autoCommit, final CallContext context) throws InvoiceApiException {
        for (final InvoiceItem charge : charges) {
            if (charge.getAmount() == null || charge.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new InvoiceApiException(ErrorCode.EXTERNAL_CHARGE_AMOUNT_INVALID, charge.getAmount());
            }
        }

        final WithAccountLock withAccountLock = new WithAccountLock() {

            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
                final LocalDate invoiceDate = internalTenantContext.toLocalDate(context.getCreatedDate());

                // Group all new external charges on the same invoice (per currency)
                final Map<Currency, DefaultInvoice> newInvoicesForExternalCharges = new HashMap<Currency, DefaultInvoice>();
                final Map<UUID, DefaultInvoice> existingInvoicesForExternalCharges = new HashMap<UUID, DefaultInvoice>();

                for (final InvoiceItem charge : charges) {
                    final Invoice invoiceForExternalCharge;
                    final UUID invoiceIdForExternalCharge = charge.getInvoiceId();
                    // Create an invoice for that external charge if it doesn't exist
                    if (invoiceIdForExternalCharge == null) {
                        final Currency currency = charge.getCurrency();
                        if (newInvoicesForExternalCharges.get(currency) == null) {
                            final InvoiceStatus status = autoCommit ? InvoiceStatus.COMMITTED : InvoiceStatus.DRAFT;
                            final DefaultInvoice newInvoiceForExternalCharge = new DefaultInvoice(accountId, invoiceDate, effectiveDate, currency, status);
                            newInvoicesForExternalCharges.put(currency, newInvoiceForExternalCharge);
                        }
                        invoiceForExternalCharge = newInvoicesForExternalCharges.get(currency);
                    } else {
                        if (existingInvoicesForExternalCharges.get(invoiceIdForExternalCharge) == null) {
                            final DefaultInvoice existingInvoiceForExternalCharge = getInvoiceInternal(invoiceIdForExternalCharge, context);
                            if (InvoiceStatus.COMMITTED.equals(existingInvoiceForExternalCharge.getStatus())) {
                                throw new InvoiceApiException(ErrorCode.INVOICE_ALREADY_COMMITTED, existingInvoiceForExternalCharge.getId());
                            }
                            existingInvoicesForExternalCharges.put(invoiceIdForExternalCharge, existingInvoiceForExternalCharge);
                        }
                        invoiceForExternalCharge = existingInvoicesForExternalCharges.get(invoiceIdForExternalCharge);
                    }

                    final LocalDate startDate = MoreObjects.firstNonNull(charge.getStartDate(), effectiveDate);
                    final LocalDate endDate = charge.getEndDate();

                    final InvoiceItem externalCharge = new ExternalChargeInvoiceItem(UUIDs.randomUUID(),
                                                                                     context.getCreatedDate(),
                                                                                     invoiceForExternalCharge.getId(),
                                                                                     accountId,
                                                                                     charge.getBundleId(),
                                                                                     charge.getSubscriptionId(),
                                                                                     charge.getProductName(),
                                                                                     charge.getPlanName(),
                                                                                     charge.getPhaseName(),
                                                                                     charge.getPrettyProductName(),
                                                                                     charge.getPrettyPlanName(),
                                                                                     charge.getPrettyPhaseName(),
                                                                                     charge.getDescription(),
                                                                                     startDate,
                                                                                     endDate,
                                                                                     charge.getAmount(),
                                                                                     charge.getRate(),
                                                                                     charge.getCurrency(),
                                                                                     charge.getLinkedItemId(),
                                                                                     charge.getItemDetails());

                    invoiceForExternalCharge.addInvoiceItem(externalCharge);
                }

                return Iterables.<DefaultInvoice>concat(newInvoicesForExternalCharges.values(), existingInvoicesForExternalCharges.values());
            }
        };

        return invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, context);
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId, final TenantContext context) throws InvoiceApiException {
        final InvoiceItem creditItem = InvoiceItemFactory.fromModelDao(dao.getCreditById(creditId, internalCallContextFactory.createInternalTenantContext(creditId, ObjectType.INVOICE_ITEM, context)));
        if (creditItem == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NO_SUCH_CREDIT, creditId);
        }

        return new CreditAdjInvoiceItem(creditItem.getId(), creditItem.getCreatedDate(), creditItem.getInvoiceId(), creditItem.getAccountId(),
                                        creditItem.getStartDate(), creditItem.getDescription(), creditItem.getAmount().negate(), creditItem.getCurrency(), creditItem.getItemDetails());
    }

    @Override
    public InvoiceItem insertCredit(final UUID accountId, final BigDecimal amount, final LocalDate effectiveDate,
                                    final Currency currency, final boolean autoCommit, final String description, final String itemDetails, final CallContext context) throws InvoiceApiException {
        return insertCreditForInvoice(accountId, null, amount, effectiveDate, currency, autoCommit, description, itemDetails, context);
    }

    @Override
    public InvoiceItem insertCreditForInvoice(final UUID accountId, final UUID invoiceId, final BigDecimal amount,
                                              final LocalDate effectiveDate, final Currency currency, final String description, final String itemDetails, final CallContext context) throws InvoiceApiException {
        return insertCreditForInvoice(accountId, invoiceId, amount, effectiveDate, currency, false, description, itemDetails, context);
    }

    private InvoiceItem insertCreditForInvoice(final UUID accountId, final UUID invoiceId, final BigDecimal amount, final LocalDate effectiveDate,
                                               final Currency currency, final boolean autoCommit, final String description, final String itemDetails, final CallContext context) throws InvoiceApiException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.CREDIT_AMOUNT_INVALID, amount);
        }

        final WithAccountLock withAccountLock = new WithAccountLock() {

            private InvoiceItem creditItem;

            @Override
            public List<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
                final LocalDate invoiceDate = internalTenantContext.toLocalDate(context.getCreatedDate());

                // Create an invoice for that credit if it doesn't exist
                final DefaultInvoice invoiceForCredit;
                if (invoiceId == null) {
                    final InvoiceStatus status = autoCommit ? InvoiceStatus.COMMITTED : InvoiceStatus.DRAFT;
                    invoiceForCredit = new DefaultInvoice(accountId, invoiceDate, effectiveDate, currency, status);
                } else {
                    invoiceForCredit = getInvoiceAndCheckCurrency(invoiceId, currency, context);
                    if (InvoiceStatus.COMMITTED.equals(invoiceForCredit.getStatus())) {
                        throw new InvoiceApiException(ErrorCode.INVOICE_ALREADY_COMMITTED, invoiceId);
                    }
                }

                // Create the new credit
                creditItem = new CreditAdjInvoiceItem(UUIDs.randomUUID(),
                                                      context.getCreatedDate(),
                                                      invoiceForCredit.getId(),
                                                      accountId,
                                                      effectiveDate,
                                                      description,
                                                      // Note! The amount is negated here!
                                                      amount.negate(),
                                                      currency,
                                                      itemDetails);
                invoiceForCredit.addInvoiceItem(creditItem);

                return ImmutableList.<DefaultInvoice>of(invoiceForCredit);
            }
        };

        final Collection<InvoiceItem> creditInvoiceItems = Collections2.<InvoiceItem>filter(invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, context),
                                                                                            new Predicate<InvoiceItem>() {
                                                                                                @Override
                                                                                                public boolean apply(final InvoiceItem invoiceItem) {
                                                                                                    return InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType());
                                                                                                }
                                                                                            });
        Preconditions.checkState(creditInvoiceItems.size() == 1, "Should have created a single credit invoice item: " + creditInvoiceItems);

        return creditInvoiceItems.iterator().next();
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, final String description, final String itemDetails, final CallContext context) throws InvoiceApiException {
        return insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, null, null, description, itemDetails, context);
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, @Nullable final BigDecimal amount,
                                                   @Nullable final Currency currency, final String description, final String itemDetails, final CallContext context) throws InvoiceApiException {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_SHOULD_BE_POSITIVE, amount);
        }

        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final DefaultInvoice invoice = getInvoiceAndCheckCurrency(invoiceId, currency, context);
                final InvoiceItem adjustmentItem = invoiceApiHelper.createAdjustmentItem(invoice,
                                                                                         invoiceItemId,
                                                                                         amount,
                                                                                         currency,
                                                                                         effectiveDate,
                                                                                         description,
                                                                                         itemDetails,
                                                                                         internalCallContextFactory.createInternalCallContext(accountId, context));
                if (adjustmentItem != null) {
                    invoice.addInvoiceItem(adjustmentItem);
                }

                return ImmutableList.<DefaultInvoice>of(invoice);
            }
        };

        final Collection<InvoiceItem> adjustmentInvoiceItems = Collections2.<InvoiceItem>filter(invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, context),
                                                                                                new Predicate<InvoiceItem>() {
                                                                                                    @Override
                                                                                                    public boolean apply(final InvoiceItem invoiceItem) {
                                                                                                        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType());
                                                                                                    }
                                                                                                });
        Preconditions.checkState(adjustmentInvoiceItems.size() <= 1, "Should have created a single adjustment item: " + adjustmentInvoiceItems);

        return adjustmentInvoiceItems.iterator().hasNext() ? adjustmentInvoiceItems.iterator().next() : null;
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

        final HtmlInvoice htmlInvoice = generator.generateInvoice(account, invoice, manualPay, internalContext);
        return htmlInvoice.getBody();
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final CallContext context) {
        dao.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public UUID createMigrationInvoice(final UUID accountId, final LocalDate targetDate, final Iterable<InvoiceItem> items, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(accountId, context);
        final LocalDate invoiceDate = internalCallContext.toLocalDate(internalCallContext.getCreatedDate());
        final InvoiceModelDao migrationInvoice = new InvoiceModelDao(accountId, invoiceDate, targetDate, items.iterator().next().getCurrency(), true);

        final List<InvoiceItemModelDao> itemModelDaos = ImmutableList.copyOf(Iterables.transform(items, new Function<InvoiceItem, InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao apply(final InvoiceItem input) {
                return new InvoiceItemModelDao(internalCallContext.getCreatedDate(),
                                               input.getInvoiceItemType(),
                                               migrationInvoice.getId(),
                                               accountId,
                                               input.getBundleId(),
                                               input.getSubscriptionId(),
                                               input.getDescription(),
                                               input.getProductName(),
                                               input.getPlanName(),
                                               input.getPhaseName(),
                                               input.getUsageName(),
                                               input.getStartDate(),
                                               input.getEndDate(),
                                               input.getAmount(),
                                               input.getRate(),
                                               input.getCurrency(),
                                               input.getLinkedItemId());

            }
        }));
        migrationInvoice.addInvoiceItems(itemModelDaos);

        dao.createInvoices(ImmutableList.<InvoiceModelDao>of(migrationInvoice), internalCallContext);
        return migrationInvoice.getId();
    }

    private void notifyBusOfInvoiceAdjustment(final UUID invoiceId, final UUID accountId, final InternalCallContext context) {
        final DefaultInvoiceAdjustmentEvent event = new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private List<Invoice> fromInvoiceModelDao(final Collection<InvoiceModelDao> invoiceModelDaos, final Catalog catalog) {
        return ImmutableList.<Invoice>copyOf(Collections2.transform(invoiceModelDaos,
                                                                    new Function<InvoiceModelDao, Invoice>() {
                                                                        @Override
                                                                        public Invoice apply(final InvoiceModelDao input) {
                                                                            return new DefaultInvoice(input, catalog);
                                                                        }
                                                                    }));
    }

    private DefaultInvoice getInvoiceAndCheckCurrency(final UUID invoiceId, @Nullable final Currency currency, final TenantContext context) throws InvoiceApiException {
        final DefaultInvoice invoice = getInvoiceInternal(invoiceId, context);
        // Check the specified currency matches the one of the existing invoice
        if (currency != null && invoice.getCurrency() != currency) {
            throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, currency, invoice.getCurrency());
        }
        return invoice;
    }

    @Override
    public void commitInvoice(final UUID invoiceId, final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
        dao.changeInvoiceStatus(invoiceId, InvoiceStatus.COMMITTED, internalCallContext);
    }

    @Override
    public void transferChildCreditToParent(final UUID childAccountId, final CallContext context) throws InvoiceApiException {

        final Account childAccount;
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(childAccountId, ObjectType.ACCOUNT, context);
        try {
            childAccount = accountUserApi.getAccountById(childAccountId, internalCallContext);
        } catch (AccountApiException e) {
            throw new InvoiceApiException(e);
        }

        if (childAccount.getParentAccountId() == null) {
            throw new InvoiceApiException(ErrorCode.ACCOUNT_DOES_NOT_HAVE_PARENT_ACCOUNT, childAccountId);
        }

        final BigDecimal accountCBA = getAccountCBA(childAccountId, context);
        if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.CHILD_ACCOUNT_MISSING_CREDIT, childAccountId);
        }

        dao.transferChildCreditToParent(childAccount, internalCallContext);

    }


    @Override
    public List<InvoiceItem> getInvoiceItemsByParentInvoice(final UUID parentInvoiceId, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(parentInvoiceId, ObjectType.INVOICE, context);

        final Catalog catalog  = getCatalogSafelyForPrettyNames(internalTenantContext);
        return ImmutableList.copyOf(Collections2.transform(dao.getInvoiceItemsByParentInvoice(parentInvoiceId, internalTenantContext),
                                                           new Function<InvoiceItemModelDao, InvoiceItem>() {
                                                               @Override
                                                               public InvoiceItem apply(final InvoiceItemModelDao input) {
                                                                   return InvoiceItemFactory.fromModelDaoWithCatalog(input, catalog);
                                                               }
                                                           }));
    }

    private Catalog getCatalogSafelyForPrettyNames(final InternalTenantContext internalTenantContext) {

        try {
            return catalogInternalApi.getFullCatalog(true, true, internalTenantContext);
        } catch (final CatalogApiException e) {
            log.warn(String.format("Failed to extract catalog to fill invoice item pretty names for tenantRecordId='%s', ignoring...", internalTenantContext.getTenantRecordId()), internalTenantContext.getTenantRecordId());
            return null;
        }
    }

    @Override
    public void voidInvoice(final UUID invoiceId, final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
        Invoice invoice = getInvoice(invoiceId, context);

        if (invoice.getNumberOfPayments() > 0) {
            canInvoiceBeVoided(invoice);
        }

        dao.changeInvoiceStatus(invoiceId, InvoiceStatus.VOID, internalCallContext);
    }

    private void canInvoiceBeVoided(final Invoice invoice) throws InvoiceApiException {
        final List<InvoicePayment> invoicePayments = invoice.getPayments();
        final BigDecimal amountPaid = InvoiceCalculatorUtils.computeInvoiceAmountPaid(invoice.getCurrency(), invoicePayments)
                                                            .add(InvoiceCalculatorUtils.computeInvoiceAmountRefunded(invoice.getCurrency(), invoicePayments));

        if (amountPaid.compareTo(BigDecimal.ZERO) != 0) {
            throw new InvoiceApiException(ErrorCode.CAN_NOT_VOID_INVOICE_THAT_IS_PAID, invoice.getId().toString());
        }
    }
}
