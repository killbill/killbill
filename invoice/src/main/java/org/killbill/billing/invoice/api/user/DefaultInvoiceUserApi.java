/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.VersionedCatalog;
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
import org.killbill.billing.invoice.model.SearchInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.TaxInvoiceItem;
import org.killbill.billing.invoice.template.HtmlInvoice;
import org.killbill.billing.invoice.template.HtmlInvoiceGenerator;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultInvoiceUserApi implements InvoiceUserApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceUserApi.class);

    private static final String INVOICE_OPERATION = "INVOICE_OPERATION";

    private final InvoiceDao dao;
    private final InvoiceDispatcher dispatcher;
    private final AccountInternalApi accountUserApi;
    private final TagInternalApi tagApi;
    private final InvoiceApiHelper invoiceApiHelper;
    private final HtmlInvoiceGenerator generator;
    private final InternalCallContextFactory internalCallContextFactory;
    private final BusOptimizer eventBus;

    private final CatalogInternalApi catalogInternalApi;

    @Inject
    public DefaultInvoiceUserApi(final InvoiceDao dao,
                                 final InvoiceDispatcher dispatcher,
                                 final AccountInternalApi accountUserApi,
                                 final BusOptimizer eventBus,
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

    private static List<InvoiceItem> negateCreditItems(final List<InvoiceItem> input) {
        return input.stream()
                    .map(creditItem -> new CreditAdjInvoiceItem(creditItem.getId(),
                                                                creditItem.getCreatedDate(),
                                                                creditItem.getInvoiceId(),
                                                                creditItem.getAccountId(),
                                                                creditItem.getStartDate(),
                                                                creditItem.getDescription(),
                                                                creditItem.getAmount().negate(),
                                                                creditItem.getRate(),
                                                                creditItem.getCurrency(),
                                                                creditItem.getQuantity(),
                                                                creditItem.getItemDetails()))
                    .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final boolean includesMigrated, final boolean includeVoidedInvoices, final boolean includeInvoiceComponents, final TenantContext context) {

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> invoicesByAccount = includesMigrated ?
                                                        dao.getAllInvoicesByAccount(includeVoidedInvoices, includeInvoiceComponents, internalTenantContext) :
                                                        dao.getInvoicesByAccount(includeVoidedInvoices, includeInvoiceComponents, internalTenantContext);

        return fromInvoiceModelDao(invoicesByAccount, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final LocalDate upToDate, final boolean includeVoidedInvoices, final boolean includeInvoiceComponents, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> invoicesByAccount = dao.getInvoicesByAccount(includeVoidedInvoices, fromDate, upToDate, includeInvoiceComponents, internalTenantContext);
        return fromInvoiceModelDao(invoicesByAccount, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Pagination<Invoice> getInvoicesByAccount(final UUID accountId, final Long offset, final Long limit, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<InvoiceModelDao, AccountApiException>() {
                                                  @Override
                                                  public Pagination<InvoiceModelDao> build() {
                                                      // Invoices will be shallow, i.e. won't contain items nor payments
                                                      return dao.getByAccountRecordId(offset, limit, internalTenantContext);
                                                  }
                                              },
                                              DefaultInvoice::new
                                             );
    }


    @Override
    public List<Invoice> getInvoicesByGroup(final UUID accountId, final UUID groupId, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> invoicesByAccount = dao.getInvoicesByGroup(groupId, internalTenantContext);
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
                                              DefaultInvoice::new
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
                                              SearchInvoice::new
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
        final InvoiceModelDao invoice = dao.getByNumber(number, true, internalTenantContextWithoutAccountRecordId);

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
        return new DefaultInvoice(invoice, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Invoice getInvoiceByInvoiceItem(final UUID invoiceItemId, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContextWithoutAccountRecordId = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        final InvoiceModelDao invoice = dao.getByInvoiceItem(invoiceItemId, internalTenantContextWithoutAccountRecordId);

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
        return new DefaultInvoice(invoice, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate startDate, final LocalDate upToDate, final TenantContext context) {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final List<InvoiceModelDao> unpaidInvoicesByAccountId = dao.getUnpaidInvoicesByAccountId(accountId, startDate, upToDate, internalTenantContext);
        return fromInvoiceModelDao(unpaidInvoicesByAccountId, getCatalogSafelyForPrettyNames(internalTenantContext));
    }

    @Override
    public Invoice triggerDryRunInvoiceGeneration(final UUID accountId, final LocalDate targetDate, final DryRunArguments dryRunArguments, final Iterable<PluginProperty> properties, final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);

        final List<Invoice> result = dispatcher.processAccount(true, accountId, targetDate, dryRunArguments, false, false, properties, internalContext);
        if (result.isEmpty()) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOTHING_TO_DO, accountId, targetDate != null ? targetDate : "null");
        } else {
            Preconditions.checkState(result.size() == 1, "Expected one invoice but got %d", result.size());
            return result.get(0);
        }
    }

    @Override
    public Invoice triggerInvoiceGeneration(final UUID accountId, @Nullable final LocalDate targetDate, final Iterable<PluginProperty> properties, final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);
        final List<Invoice> result = dispatcher.processAccount(true, accountId, targetDate, null, false, false, properties, internalContext);
        if (result.isEmpty()) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NOTHING_TO_DO, accountId, targetDate != null ? targetDate : "null");
        } else {
            Preconditions.checkState(result.size() == 1);
            return result.get(0);
        }
    }

    @Override
    public Iterable<Invoice> triggerInvoiceGroupGeneration(final UUID accountId, final LocalDate targetDate, final Iterable<PluginProperty> properties, final CallContext context) throws InvoiceApiException {
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(accountId, context);
        final List<Invoice> result = dispatcher.processAccount(true, accountId, targetDate, null, false, true, properties, internalContext);
        if (result.isEmpty()) {
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
    public List<InvoiceItem> insertExternalCharges(final UUID accountId,
                                                   final LocalDate effectiveDate,
                                                   final Iterable<InvoiceItem> charges,
                                                   final boolean autoCommit,
                                                   final Iterable<PluginProperty> originalProperties,
                                                   final CallContext context) throws InvoiceApiException {
        final LinkedList<PluginProperty> properties = new LinkedList<>();
        if (originalProperties != null) {
            originalProperties.forEach(properties::add);
        }
        return insertItems(accountId, effectiveDate, InvoiceItemType.EXTERNAL_CHARGE, charges, autoCommit, properties, context);
    }

    @Override
    public List<InvoiceItem> insertTaxItems(final UUID accountId,
                                            final LocalDate effectiveDate,
                                            final Iterable<InvoiceItem> taxItems,
                                            final boolean autoCommit,
                                            final Iterable<PluginProperty> originalProperties,
                                            final CallContext context) throws InvoiceApiException {
        final LinkedList<PluginProperty> properties = new LinkedList<>();
        if (originalProperties != null) {
            originalProperties.forEach(properties::add);
        }

        return insertItems(accountId, effectiveDate, InvoiceItemType.TAX, taxItems, autoCommit, properties, context);
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId, final TenantContext context) throws InvoiceApiException {
        final InvoiceItem creditItem = InvoiceItemFactory.fromModelDao(dao.getCreditById(creditId, internalCallContextFactory.createInternalTenantContext(creditId, ObjectType.INVOICE_ITEM, context)));
        if (creditItem == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_NO_SUCH_CREDIT, creditId);
        }
        return negateCreditItems(List.of(creditItem)).get(0);
    }

    @Override
    public List<InvoiceItem> insertCredits(final UUID accountId,
                                           final LocalDate effectiveDate,
                                           final Iterable<InvoiceItem> creditItems,
                                           final boolean autoCommit,
                                           final Iterable<PluginProperty> originalProperties,
                                           final CallContext context) throws InvoiceApiException {
        final LinkedList<PluginProperty> properties = new LinkedList<>();
        if (originalProperties != null) {
            originalProperties.forEach(properties::add);
        }

        final List<InvoiceItem> items = insertItems(accountId, effectiveDate, InvoiceItemType.CREDIT_ADJ, creditItems, autoCommit, properties, context);
        return negateCreditItems(items);
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, final String description, final String itemDetails, final Iterable<PluginProperty> properties, final CallContext context) throws InvoiceApiException {
        return insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, null, null, description, itemDetails, properties, context);
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId,
                                                   final UUID invoiceId,
                                                   final UUID invoiceItemId,
                                                   final LocalDate effectiveDate,
                                                   @Nullable final BigDecimal amount,
                                                   @Nullable final Currency currency,
                                                   final String description,
                                                   final String itemDetails,
                                                   final Iterable<PluginProperty> originalProperties,
                                                   final CallContext context) throws InvoiceApiException {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_AMOUNT_SHOULD_BE_POSITIVE, amount);
        }

        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {

                final DefaultInvoice invoice = getInvoiceInternal(invoiceId, context);
                if (InvoiceStatus.VOID == invoice.getStatus()) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_VOID_UPDATED, invoice.getId());
                }

                // Check the specified currency matches the one of the existing invoice
                if (currency != null && invoice.getCurrency() != currency) {
                    throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, currency, invoice.getCurrency());
                }

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

                return List.<DefaultInvoice>of(invoice);
            }
        };

        final LinkedList<PluginProperty> properties = new LinkedList<>();

        if (originalProperties != null) {
            originalProperties.forEach(properties::add);
        }

        final Collection<InvoiceItem> dispatchedInvoiceItems = invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, properties, true, context);

        final Collection<InvoiceItem> adjustmentInvoiceItems = dispatchedInvoiceItems.stream()
                .filter(invoiceItem -> InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()))
                .collect(Collectors.toUnmodifiableList());

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
    public void consumeExistingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final CallContext context) {
        dao.consumeExstingCBAOnAccountWithUnpaidInvoices(accountId, internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public UUID createMigrationInvoice(final UUID accountId, final LocalDate targetDate, final Iterable<InvoiceItem> items, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(accountId, context);
        final LocalDate invoiceDate = internalCallContext.toLocalDate(internalCallContext.getCreatedDate());
        final InvoiceModelDao migrationInvoice = new InvoiceModelDao(accountId, invoiceDate, targetDate, items.iterator().next().getCurrency(), true);
        final List<InvoiceItemModelDao> itemModelDaos = Iterables.toStream(items)
                .map(input -> new InvoiceItemModelDao(internalCallContext.getCreatedDate(),
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
                                                      input.getCatalogEffectiveDate(),
                                                      input.getStartDate(),
                                                      input.getEndDate(),
                                                      input.getAmount(),
                                                      input.getRate(),
                                                      input.getCurrency(),
                                                      input.getLinkedItemId()))
                .collect(Collectors.toUnmodifiableList());

        migrationInvoice.addInvoiceItems(itemModelDaos);

        dao.createInvoices(List.of(migrationInvoice), null, Collections.emptySet(), null, null, false, internalCallContext);
        return migrationInvoice.getId();
    }

    private List<InvoiceItem> insertItems(final UUID accountId,
                                          final LocalDate effectiveDate,
                                          final InvoiceItemType itemType,
                                          final Iterable<InvoiceItem> inputItems,
                                          final boolean autoCommit,
                                          final LinkedList<PluginProperty> properties,
                                          final CallContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(accountId, context);
        final ImmutableAccountData accountData;
        try {
            accountData = accountUserApi.getImmutableAccountDataById(accountId, internalTenantContext);
        } catch (final AccountApiException e) {
            throw new InvoiceApiException(e);
        }

        final Currency accountCurrency = accountData.getCurrency();

        final WithAccountLock withAccountLock = new WithAccountLock() {

            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final LocalDate invoiceDate = internalTenantContext.toLocalDate(context.getCreatedDate());

                final Map<UUID, DefaultInvoice> newAndExistingInvoices = new HashMap<UUID, DefaultInvoice>();

                UUID newInvoiceId = null;
                for (final InvoiceItem inputItem : inputItems) {

                    if (inputItem.getAmount() == null || inputItem.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                        if (itemType == InvoiceItemType.EXTERNAL_CHARGE) {
                            throw new InvoiceApiException(ErrorCode.EXTERNAL_CHARGE_AMOUNT_INVALID, inputItem.getAmount());
                        } else if (itemType == InvoiceItemType.CREDIT_ADJ) {
                            throw new InvoiceApiException(ErrorCode.CREDIT_AMOUNT_INVALID, inputItem.getAmount());
                        }
                    }

                    if (inputItem.getCurrency() != null && !inputItem.getCurrency().equals(accountCurrency)) {
                        throw new InvoiceApiException(ErrorCode.CURRENCY_INVALID, inputItem.getCurrency(), accountCurrency);
                    }

                    final UUID invoiceIdForItem = inputItem.getInvoiceId();

                    final Invoice curInvoiceForItem;
                    if (invoiceIdForItem == null) {
                        final Currency currency = inputItem.getCurrency();
                        final InvoiceStatus status = autoCommit ? InvoiceStatus.COMMITTED : InvoiceStatus.DRAFT;

                        if (newInvoiceId == null) {
                            final DefaultInvoice newInvoiceForItems = new DefaultInvoice(accountId, invoiceDate, effectiveDate, currency, status);
                            newInvoiceId = newInvoiceForItems.getId();
                            newAndExistingInvoices.put(newInvoiceId, newInvoiceForItems);
                        }
                        curInvoiceForItem = newAndExistingInvoices.get(newInvoiceId);
                    } else {
                        if (newAndExistingInvoices.get(invoiceIdForItem) == null) {
                            final DefaultInvoice existingInvoiceForExternalCharge = getInvoiceInternal(invoiceIdForItem, context);
                            switch (existingInvoiceForExternalCharge.getStatus()) {
                                case COMMITTED:
                                    throw new InvoiceApiException(ErrorCode.INVOICE_ALREADY_COMMITTED, existingInvoiceForExternalCharge.getId());
                                case VOID:
                                    // TODO Add missing error https://github.com/killbill/killbill/issues/1501
                                    throw new IllegalStateException(String.format("Cannot add credit or external charge for invoice id %s because it is in \" + InvoiceStatus.VOID + \" status\"",
                                                                                  existingInvoiceForExternalCharge.getId()));
                                case DRAFT:
                                default:
                                    break;
                            }

                            newAndExistingInvoices.put(invoiceIdForItem, existingInvoiceForExternalCharge);
                        }
                        curInvoiceForItem = newAndExistingInvoices.get(invoiceIdForItem);
                    }

                    final InvoiceItem newInvoiceItem;
                    switch (itemType) {
                        case EXTERNAL_CHARGE:
                            newInvoiceItem = new ExternalChargeInvoiceItem(UUIDs.randomUUID(),
                                                                           context.getCreatedDate(),
                                                                           curInvoiceForItem.getId(),
                                                                           accountId,
                                                                           inputItem.getBundleId(),
                                                                           inputItem.getSubscriptionId(),
                                                                           inputItem.getProductName(),
                                                                           inputItem.getPlanName(),
                                                                           inputItem.getPhaseName(),
                                                                           inputItem.getPrettyProductName(),
                                                                           inputItem.getPrettyPlanName(),
                                                                           inputItem.getPrettyPhaseName(),
                                                                           inputItem.getDescription(),
                                                                           Objects.requireNonNullElse(inputItem.getStartDate(), effectiveDate),
                                                                           inputItem.getEndDate(),
                                                                           inputItem.getAmount(),
                                                                           inputItem.getRate(),
                                                                           accountCurrency,
                                                                           inputItem.getLinkedItemId(),
                                                                           inputItem.getQuantity(),
                                                                           inputItem.getItemDetails());

                            break;
                        case CREDIT_ADJ:

                            newInvoiceItem = new CreditAdjInvoiceItem(UUIDs.randomUUID(),
                                                                      context.getCreatedDate(),
                                                                      curInvoiceForItem.getId(),
                                                                      accountId,
                                                                      effectiveDate,
                                                                      inputItem.getDescription(),
                                                                      // Note! The amount is negated here!
                                                                      inputItem.getAmount().negate(),
                                                                      inputItem.getRate(),
                                                                      inputItem.getCurrency(),
                                                                      inputItem.getQuantity(),
                                                                      inputItem.getItemDetails());
                            break;
                        case TAX:
                            newInvoiceItem = new TaxInvoiceItem(UUIDs.randomUUID(),
                                                                curInvoiceForItem.getId(),
                                                                accountId,
                                                                inputItem.getBundleId(),
                                                                inputItem.getDescription(),
                                                                Objects.requireNonNullElse(inputItem.getStartDate(), effectiveDate),
                                                                inputItem.getAmount(),
                                                                accountCurrency);
                            break;
                        default:
                            throw new IllegalStateException(String.format("Unsupported to add item of type '%s'", itemType));
                    }

                    curInvoiceForItem.addInvoiceItem(newInvoiceItem);
                }
                return newAndExistingInvoices.values();
            }
        };

        return invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, properties, true, context);
    }

    private void notifyBusOfInvoiceAdjustment(final UUID invoiceId, final UUID accountId, final InternalCallContext context) {
        final DefaultInvoiceAdjustmentEvent event = new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken());
        try {
            eventBus.post(event);
        } catch (final EventBusException e) {
            log.warn("Failed to post event {}", event, e);
        }
    }

    private List<Invoice> fromInvoiceModelDao(final Iterable<InvoiceModelDao> invoiceModelDaos, final VersionedCatalog catalog) {
        final List<Invoice> invoices = new LinkedList<Invoice>();
        for (final InvoiceModelDao invoiceModelDao : invoiceModelDaos) {
            invoices.add(new DefaultInvoice(invoiceModelDao, catalog));
        }
        return invoices;
    }

    @Override
    public void commitInvoice(final UUID invoiceId, final CallContext context) throws InvoiceApiException {
        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
                // Update invoice status first prior we update CTD as we typically don't update CTD for non committed invoices.
                dao.changeInvoiceStatus(invoiceId, InvoiceStatus.COMMITTED, internalCallContext);
                final DefaultInvoice invoice = getInvoiceInternal(invoiceId, context);
                dispatcher.setChargedThroughDates(invoice, internalCallContext);
                return List.of(invoice);
            }
        };

        final UUID accountId = getInvoiceInternal(invoiceId, context).getAccountId();

        final LinkedList<PluginProperty> properties = new LinkedList<PluginProperty>();
        properties.add(new PluginProperty(INVOICE_OPERATION, "commit", false));

        invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, properties, false, context);
    }
    @Override
    public void transferChildCreditToParent(final UUID childAccountId, final CallContext context) throws InvoiceApiException {

        final Account childAccount;
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(childAccountId, ObjectType.ACCOUNT, context);
        try {
            childAccount = accountUserApi.getAccountById(childAccountId, internalCallContext);
        } catch (final AccountApiException e) {
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
        final VersionedCatalog catalog = getCatalogSafelyForPrettyNames(internalTenantContext);
        final List<InvoiceItemModelDao> invoiceItems = dao.getInvoiceItemsByParentInvoice(parentInvoiceId, internalTenantContext);
        return invoiceItems.stream()
                .map(input -> InvoiceItemFactory.fromModelDaoWithCatalog(input, catalog))
                .collect(Collectors.toUnmodifiableList());
    }

    private VersionedCatalog getCatalogSafelyForPrettyNames(final InternalTenantContext internalTenantContext) {

        try {
            return catalogInternalApi.getFullCatalog(true, true, internalTenantContext);
        } catch (final CatalogApiException e) {
            log.warn(String.format("Failed to extract catalog to fill invoice item pretty names for tenantRecordId='%s', ignoring...", internalTenantContext.getTenantRecordId()), internalTenantContext.getTenantRecordId());
            return null;
        }
    }

    private void checkInvoiceNotRepaired(final InvoiceModelDao invoice) throws InvoiceApiException {
        if (invoice.getIsRepaired()) {
            throw new InvoiceApiException(ErrorCode.CAN_NOT_VOID_INVOICE_THAT_IS_REPAIRED, invoice.getId());
        }
    }

    private void checkInvoiceDoesContainUsedGeneratedCredit(final UUID accountId, final InvoiceModelDao invoice, final CallContext context) throws InvoiceApiException {
        final BigDecimal accountCBA = dao.getAccountCBA(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
        final boolean largeCreditGenExist = invoice.getInvoiceItems().stream()
                .anyMatch(invoiceItemModelDao -> /* Positive CBA */
                                  InvoiceItemType.CBA_ADJ == invoiceItemModelDao.getType() && /* CBA item */
                                  invoiceItemModelDao.getAmount().compareTo(BigDecimal.ZERO) > 0 && /* Credit generation */
                                  invoiceItemModelDao.getAmount().compareTo(accountCBA) > 0 /* Some of it was used already */);
        if (largeCreditGenExist) {
            throw new InvoiceApiException(ErrorCode.CAN_NOT_VOID_INVOICE_THAT_GENERATED_USED_CREDIT, invoice.getId());
        }
    }

    @Override
    public void voidInvoice(final UUID invoiceId, final CallContext context) throws InvoiceApiException {

        final UUID accountId = getInvoiceInternal(invoiceId, context).getAccountId();
        final WithAccountLock withAccountLock = new WithAccountLock() {
            @Override
            public Iterable<DefaultInvoice> prepareInvoices() throws InvoiceApiException {
                final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(invoiceId, ObjectType.INVOICE, context);
                final InvoiceModelDao rawInvoice = dao.getById(invoiceId, true, internalCallContext);
                if (rawInvoice.getStatus() == InvoiceStatus.COMMITTED) {
                    checkInvoiceNotRepaired(rawInvoice);
                    checkInvoiceDoesContainUsedGeneratedCredit(accountId, rawInvoice, context);
                }

                final Invoice currentInvoice = new DefaultInvoice(rawInvoice, getCatalogSafelyForPrettyNames(internalCallContext));
                checkInvoiceNotPaid(currentInvoice);

                dao.changeInvoiceStatus(invoiceId, InvoiceStatus.VOID, internalCallContext);

                final DefaultInvoice invoice = getInvoiceInternal(invoiceId, context);
                return List.of(invoice);
            }
        };

        final LinkedList<PluginProperty> properties = new LinkedList<PluginProperty>();
        properties.add(new PluginProperty(INVOICE_OPERATION, "void", false));

        invoiceApiHelper.dispatchToInvoicePluginsAndInsertItems(accountId, false, withAccountLock, properties, false, context);
    }
    @Override
    public List<AuditLogWithHistory> getInvoiceAuditLogsWithHistoryForId(final UUID invoiceId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return dao.getInvoiceAuditLogsWithHistoryForId(invoiceId, auditLevel, internalCallContextFactory.createInternalTenantContext(invoiceId, ObjectType.INVOICE, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getInvoiceItemAuditLogsWithHistoryForId(final UUID invoiceItemId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return dao.getInvoiceItemAuditLogsWithHistoryForId(invoiceItemId, auditLevel, internalCallContextFactory.createInternalTenantContext(invoiceItemId, ObjectType.INVOICE_ITEM, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getInvoicePaymentAuditLogsWithHistoryForId(final UUID invoicePaymentId, final AuditLevel auditLevel, final TenantContext tenantContext) {
        return dao.getInvoicePaymentAuditLogsWithHistoryForId(invoicePaymentId, auditLevel, internalCallContextFactory.createInternalTenantContext(invoicePaymentId, ObjectType.INVOICE_PAYMENT, tenantContext));
    }

    private void checkInvoiceNotPaid(final Invoice invoice) throws InvoiceApiException {
        if (invoice.getNumberOfPayments() > 0) {
            final List<InvoicePayment> invoicePayments = invoice.getPayments();
            final BigDecimal amountPaid = InvoiceCalculatorUtils.computeInvoiceAmountPaid(invoice.getCurrency(), invoicePayments)
                                                                .add(InvoiceCalculatorUtils.computeInvoiceAmountRefunded(invoice.getCurrency(), invoicePayments));

            if (amountPaid.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvoiceApiException(ErrorCode.CAN_NOT_VOID_INVOICE_THAT_IS_PAID, invoice.getId().toString());
            }
        }
    }

}
