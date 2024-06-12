/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoicePluginDispatcher;
import org.killbill.billing.invoice.api.DefaultInvoicePaymentErrorEvent;
import org.killbill.billing.invoice.api.DefaultInvoicePaymentInfoEvent;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePaymentStatus;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import org.killbill.billing.invoice.dao.serialization.BillingEventSerializer;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.notification.NextBillingDatePoster;
import org.killbill.billing.invoice.notification.ParentInvoiceCommitmentPoster;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.entity.dao.SearchQuery;
import org.killbill.billing.util.entity.dao.SqlOperator;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.billing.util.tag.Tag;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.commons.utils.Preconditions;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.commons.utils.collect.Sets;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.killbill.billing.util.entity.dao.SearchQuery.SEARCH_QUERY_MARKER;
import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultInvoiceDao extends EntityDaoBase<InvoiceModelDao, Invoice, InvoiceApiException> implements InvoiceDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceDao.class);

    private static final Comparator<InvoiceModelDao> INVOICE_MODEL_DAO_COMPARATOR = Comparator.comparing(invoice -> invoice.getTargetDate() == null ?
                                                                                                                    invoice.getCreatedDate().toLocalDate() :
                                                                                                                    invoice.getTargetDate());

    private static final Collection<InvoiceItemType> INVOICE_ITEM_TYPES_ADJUSTABLE = List.of(InvoiceItemType.EXTERNAL_CHARGE,
                                                                                             InvoiceItemType.FIXED,
                                                                                             InvoiceItemType.RECURRING,
                                                                                             InvoiceItemType.TAX,
                                                                                             InvoiceItemType.USAGE,
                                                                                             InvoiceItemType.PARENT_SUMMARY);

    private static final Pattern BALANCE_QUERY_PATTERN = Pattern.compile(SEARCH_QUERY_MARKER + "balance\\[(?<comparator>eq|gte|gt|lte|lt|neq)\\]=(?<balance>\\w+)");

    private final NextBillingDatePoster nextBillingDatePoster;
    private final BusOptimizer eventBus;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceDaoHelper invoiceDaoHelper;
    private final CBADao cbaDao;
    private final InvoiceConfig invoiceConfig;
    private final CacheController<String, UUID> objectIdCacheController;
    private final NonEntityDao nonEntityDao;
    private final ParentInvoiceCommitmentPoster parentInvoiceCommitmentPoster;
    private final TagInternalApi tagInternalApi;
    private final AuditDao auditDao;



    @Inject
    public DefaultInvoiceDao(final TagInternalApi tagInternalApi,
                             final IDBI dbi,
                             @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final BusOptimizer eventBus,
                             final Clock clock,
                             final CacheControllerDispatcher cacheControllerDispatcher,
                             final NonEntityDao nonEntityDao,
                             final InvoiceConfig invoiceConfig,
                             final InvoiceDaoHelper invoiceDaoHelper,
                             final CBADao cbaDao,
                             final ParentInvoiceCommitmentPoster parentInvoiceCommitmentPoster,
                             final AuditDao auditDao,
                             final InternalCallContextFactory internalCallContextFactory) {
        super(nonEntityDao, cacheControllerDispatcher, new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), InvoiceSqlDao.class);
        this.tagInternalApi = tagInternalApi;
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.eventBus = eventBus;
        this.invoiceConfig = invoiceConfig;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceDaoHelper = invoiceDaoHelper;
        this.cbaDao = cbaDao;
        this.auditDao = auditDao;
        this.objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        this.nonEntityDao = nonEntityDao;
        this.parentInvoiceCommitmentPoster = parentInvoiceCommitmentPoster;
    }

    @Override
    protected InvoiceApiException generateAlreadyExistsException(final InvoiceModelDao entity, final InternalCallContext context) {
        return new InvoiceApiException(ErrorCode.INVOICE_ACCOUNT_ID_INVALID, entity.getId());
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final Boolean includeInvoiceComponents, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            final List<InvoiceModelDao> invoicesByAccountRecordId = invoiceSqlDao.getByAccountRecordId(context);
            final List<InvoiceModelDao> invoices = invoicesByAccountRecordId.stream()
                                                                            .filter(invoice -> !invoice.isMigrated() &&
                                                                                               (includeVoidedInvoices || !InvoiceStatus.VOID.equals(invoice.getStatus())))
                                                                            .sorted(INVOICE_MODEL_DAO_COMPARATOR)
                                                                            .collect(Collectors.toUnmodifiableList());

            if (includeInvoiceComponents) {
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, false, entitySqlDaoWrapperFactory, context);
            }

            return invoices;
        });
    }

    @Override
    public List<InvoiceModelDao> getAllInvoicesByAccount(final Boolean includeVoidedInvoices, final Boolean includeInvoiceComponents, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(includeVoidedInvoices, includeInvoiceComponents, invoicesTags, entitySqlDaoWrapperFactory, context));
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final LocalDate fromDate, final LocalDate upToDate, final Boolean includeInvoiceComponents, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            final List<InvoiceModelDao> invoices = getAllNonMigratedInvoicesByAccountAfterDate(includeVoidedInvoices, invoiceDao, fromDate, upToDate, context);
            if (includeInvoiceComponents) {
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, false, entitySqlDaoWrapperFactory, context);
            }

            return invoices;
        });
    }

    private List<InvoiceModelDao> getAllNonMigratedInvoicesByAccountAfterDate(final Boolean includeVoidedInvoices,
                                                                              final InvoiceSqlDao invoiceSqlDao,
                                                                              final LocalDate fromDate,
                                                                              final LocalDate upToDate,
                                                                              final InternalTenantContext context) {
        final List<InvoiceModelDao> candidates = fromDate != null ?
                                                 invoiceSqlDao.getInvoiceByAccountRecordIdAfter(fromDate, context) :
                                                 invoiceSqlDao.getByAccountRecordId(context);

        return candidates.stream()
                .filter(invoice -> !invoice.isMigrated() &&
                                   (upToDate == null || invoice.getTargetDate().compareTo(upToDate) <= 0) &&
                                   (includeVoidedInvoices || !InvoiceStatus.VOID.equals(invoice.getStatus())))
                .sorted(INVOICE_MODEL_DAO_COMPARATOR)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public InvoiceModelDao getById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return getById(invoiceId, false, context);
    }

    @Override
    public InvoiceModelDao getById(final UUID invoiceId, final boolean includeRepairStatus, final InternalTenantContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            final InvoiceModelDao invoice = invoiceSqlDao.getById(invoiceId.toString(), context);
            if (invoice == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
            }
            invoiceDaoHelper.populateChildren(invoice, invoicesTags, includeRepairStatus, entitySqlDaoWrapperFactory, context);
            return invoice;
        });
    }

    @Override
    public InvoiceModelDao getByNumber(final Integer number, final Boolean includeInvoiceChildren, final InternalTenantContext context) throws InvoiceApiException {
        if (number == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_NUMBER, "(null)");
        }

        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            final InvoiceModelDao invoice = invoiceDao.getByRecordId(number.longValue(), context);
            if (invoice == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_NUMBER_NOT_FOUND, number.longValue());
            }

            if (includeInvoiceChildren) {
                // The context may not contain the account record id at this point - we couldn't do it in the API above
                // as we couldn't get access to the invoice object until now.
                final InternalTenantContext contextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, false, entitySqlDaoWrapperFactory, contextWithAccountRecordId);
            }
            return invoice;
        });
    }

    @Override
    public InvoiceModelDao getByInvoiceItem(final UUID invoiceItemId, final InternalTenantContext context) throws InvoiceApiException {

        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            final InvoiceModelDao invoice = invoiceSqlDao.getInvoiceByInvoiceItemId(invoiceItemId.toString(), context);
            if (invoice == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
            }

            final InternalTenantContext contextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
            invoiceDaoHelper.populateChildren(invoice, invoicesTags, false, entitySqlDaoWrapperFactory, contextWithAccountRecordId);
            return invoice;
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByGroup(final UUID groupId, final InternalTenantContext context) {
        // TODO_1658 Do we want a special query along with a new index on grpId or is the cardinality small enough that we
        // fetch by accountRecordId. Note that we only 'populate' on the filtered list.
        final List<Tag> invoicesTags = getInvoicesTags(context);
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
                final List<InvoiceModelDao> allInvoices = invoiceSqlDao.getByAccountRecordId(context);
                final List<InvoiceModelDao> invoices = allInvoices.stream()
                                                                  .filter(invoice -> invoice.getGrpId().equals(groupId))
                                                                  .sorted(INVOICE_MODEL_DAO_COMPARATOR)
                                                                  .collect(Collectors.toUnmodifiableList());
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, false, entitySqlDaoWrapperFactory, context);
                return invoices;
            }
        });

    }

    @Override
    public InvoiceStatus getInvoiceStatus(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceStatus>() {
            @Override
            public InvoiceStatus inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao invoice = invoiceSqlDao.getById(invoiceId.toString(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }
                return invoice.getStatus();
            }
        });
    }

    @Override
    public void setFutureAccountNotificationsForEmptyInvoice(final UUID accountId,
                                                             final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                                             final InternalCallContext context) {
        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            notifyOfFutureBillingEvents(entitySqlDaoWrapperFactory, accountId, callbackDateTimePerSubscriptions, context);
            return null;
        });
    }

    @Override
    public void rescheduleInvoiceNotification(final UUID accountId, final DateTime nextRescheduleDt, final InternalCallContext context) {
        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            nextBillingDatePoster.insertNextBillingNotificationFromTransaction(entitySqlDaoWrapperFactory, accountId, Collections.emptySet(), nextRescheduleDt, true, context);
            return null;
        });
    }

    public List<InvoiceItemModelDao> createInvoices(final Iterable<InvoiceModelDao> inputInvoices,
                                                    @Nullable final BillingEventSet billingEvents,
                                                    final Set<InvoiceTrackingModelDao> trackingIds,
                                                    @Nullable final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                                    @Nullable final ExistingInvoiceMetadata existingInvoiceMetadataOrNull,
                                                    final boolean returnCreatedInvoiceItems,
                                                    final InternalCallContext context) {
        // Track invoices that are being created
        final Set<UUID> createdInvoiceIds = new HashSet<UUID>();
        // Track invoices that already exist but are being committed -- AUTO_INVOICING_REUSE_DRAFT mode
        final Set<UUID> committedReusedInvoiceId = new HashSet<UUID>();
        // Track all invoices that are referenced through all invoiceItems
        final Set<UUID> allInvoiceIds = new HashSet<UUID>();
        // Track invoices that are committed but were not created or reused -- to sent the InvoiceAdjustment bus event
        final Set<UUID> adjustedCommittedInvoiceIds = new HashSet<UUID>();

        // Track set of invoices being referenced - note that input invoices can be used as 'containers' with items that belong to them
        // However, if this is the case, we expect the invoice to exist
        final Collection<UUID> invoiceIdsReferencedFromItems = new HashSet<UUID>();
        for (final InvoiceModelDao invoiceModelDao : inputInvoices) {
            for (final InvoiceItemModelDao invoiceItemModelDao : invoiceModelDao.getInvoiceItems()) {
                invoiceIdsReferencedFromItems.add(invoiceItemModelDao.getInvoiceId());
            }
        }

        if (Iterables.isEmpty(inputInvoices)) {
            return Collections.emptyList();
        }
        final UUID accountId = inputInvoices.iterator().next().getAccountId();

        final List<Tag> invoicesTags = getInvoicesTags(context);

        final Map<UUID, InvoiceModelDao> inputInvoicesById = new HashMap<UUID, InvoiceModelDao>();
        return transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<List<InvoiceItemModelDao>>() {
            @Override
            public List<InvoiceItemModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
                final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                final InvoiceBillingEventSqlDao billingEventSqlDao = entitySqlDaoWrapperFactory.become(InvoiceBillingEventSqlDao.class);

                final ExistingInvoiceMetadata existingInvoiceMetadata;
                if (existingInvoiceMetadataOrNull == null) {
                    existingInvoiceMetadata = new ExistingInvoiceMetadata(invoiceSqlDao, transInvoiceItemSqlDao);
                } else {
                    existingInvoiceMetadata = existingInvoiceMetadataOrNull;
                }

                final List<InvoiceItemModelDao> invoiceItemsToCreate = new LinkedList<InvoiceItemModelDao>();
                UUID grpId = null;
                for (final InvoiceModelDao invoiceModelDao : inputInvoices) {
                    inputInvoicesById.put(invoiceModelDao.getId(), invoiceModelDao);
                    final boolean isNotShellInvoice = invoiceIdsReferencedFromItems.remove(invoiceModelDao.getId());

                    final InvoiceModelDao invoiceOnDisk = existingInvoiceMetadata.getExistingInvoice(invoiceModelDao.getId(), context);
                    if (isNotShellInvoice) {
                        // Create the invoice if this is not a shell invoice and it does not already exist
                        if (invoiceOnDisk == null) {
                            if (grpId == null) {
                                grpId = invoiceModelDao.getId();
                            }
                            invoiceModelDao.setGrpId(grpId);
                            createAndRefresh(invoiceSqlDao, invoiceModelDao, context);
                            if (billingEvents != null) {
                                billingEventSqlDao.create(new InvoiceBillingEventModelDao(invoiceModelDao.getId(), BillingEventSerializer.serialize(billingEvents), context.getCreatedDate()), context);
                            }
                            createdInvoiceIds.add(invoiceModelDao.getId());
                        } else {

                            // Allow transition from DRAFT to COMMITTED or keep current status
                            InvoiceStatus newStatus = invoiceOnDisk.getStatus();
                            boolean statusUpdated = false;
                            if (InvoiceStatus.COMMITTED == invoiceModelDao.getStatus() && InvoiceStatus.DRAFT == invoiceOnDisk.getStatus()) {
                                statusUpdated = true;
                                newStatus = InvoiceStatus.COMMITTED;
                            }

                            // Update if target date is specified and prev targetDate was either null or prior to new date
                            LocalDate newTargetDate = invoiceOnDisk.getTargetDate();
                            boolean targetDateUpdated = false;
                            if (invoiceModelDao.getTargetDate() != null &&
                                (invoiceOnDisk.getTargetDate() == null || invoiceOnDisk.getTargetDate().compareTo(invoiceModelDao.getTargetDate()) < 0)) {
                                targetDateUpdated = true;
                                newTargetDate = invoiceModelDao.getTargetDate();
                            }

                            if (statusUpdated || targetDateUpdated) {
                                invoiceSqlDao.updateStatusAndTargetDate(invoiceModelDao.getId().toString(), newStatus.toString(), newTargetDate, context);
                                committedReusedInvoiceId.add(invoiceModelDao.getId());
                            }
                        }
                    }

                    // Create the invoice items if needed (note: they may not necessarily belong to that invoice)
                    for (final InvoiceItemModelDao invoiceItemModelDao : invoiceModelDao.getInvoiceItems()) {
                        final InvoiceItemModelDao existingInvoiceItem = existingInvoiceMetadata.getExistingInvoiceItem(invoiceItemModelDao.getId(), context);
                        // Because of AUTO_INVOICING_REUSE_DRAFT we expect an invoice were items might already exist.
                        // Also for ALLOWED_INVOICE_ITEM_TYPES, we expect plugins to potentially modify the amount
                        if (existingInvoiceItem == null) {
                            invoiceItemsToCreate.add(invoiceItemModelDao);
                            allInvoiceIds.add(invoiceItemModelDao.getInvoiceId());
                        } else if (InvoicePluginDispatcher.ALLOWED_INVOICE_ITEM_TYPES.contains(invoiceItemModelDao.getType()) &&
                                   // The restriction on the amount is to deal with https://github.com/killbill/killbill/issues/993 - and ensure that duplicate
                                   // items would not be re-written
                                   (invoiceItemModelDao.getAmount().compareTo(existingInvoiceItem.getAmount()) != 0)) {
                            if (checkAgainstExistingInvoiceItemState(existingInvoiceItem, invoiceItemModelDao)) {
                                transInvoiceItemSqlDao.updateItemFields(invoiceItemModelDao.getId().toString(), invoiceItemModelDao.getAmount(), invoiceItemModelDao.getRate(), invoiceItemModelDao.getDescription(), invoiceItemModelDao.getQuantity(), invoiceItemModelDao.getItemDetails(), context);
                            }
                        }
                    }
                    final boolean wasInvoiceCreatedOrCommitted = createdInvoiceIds.contains(invoiceModelDao.getId()) ||
                                                                 committedReusedInvoiceId.contains(invoiceModelDao.getId());

                    final boolean hasInvoiceBeenAdjusted = allInvoiceIds.contains(invoiceModelDao.getId());

                    if (InvoiceStatus.COMMITTED.equals(invoiceModelDao.getStatus())) {
                        if (wasInvoiceCreatedOrCommitted) {
                            notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, invoiceModelDao, context);
                        } else if (hasInvoiceBeenAdjusted) {
                            adjustedCommittedInvoiceIds.add(invoiceModelDao.getId());
                        }
                    } else if (wasInvoiceCreatedOrCommitted && invoiceModelDao.isParentInvoice()) {
                        notifyOfParentInvoiceCreation(entitySqlDaoWrapperFactory, invoiceModelDao, context);
                    }

                    // We always add the future notifications when the callbackDateTimePerSubscriptions is not empty (incl. DRAFT invoices containing RECURRING items created using AUTO_INVOICING_DRAFT feature)
                    notifyOfFutureBillingEvents(entitySqlDaoWrapperFactory, invoiceModelDao.getAccountId(), callbackDateTimePerSubscriptions, context);
                }

                // Verify invoices remaining through input items exist (if not already created before)
                for (final UUID invoiceId : invoiceIdsReferencedFromItems) {
                    final InvoiceModelDao tmp = invoiceSqlDao.getById(invoiceId.toString(), context);
                    if (tmp == null) {
                        throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                    }
                }


                // Bulk insert the invoice items
                createInvoiceItemsFromTransaction(transInvoiceItemSqlDao, invoiceItemsToCreate, context);

                // CBA COMPLEXITY...
                //
                // Optimized path where we don't need to refresh invoices
                final CBALogicWrapper cbaWrapper = new CBALogicWrapper(accountId, invoicesTags, context, entitySqlDaoWrapperFactory);
                if (createdInvoiceIds.equals(allInvoiceIds)) {
                    final List<InvoiceModelDao> cbaInvoicesInput = new ArrayList<>();
                    for (final UUID id : createdInvoiceIds) {
                        cbaInvoicesInput.add(inputInvoicesById.get(id));
                    }
                    cbaWrapper.runCBALogicWithNotificationEvents(adjustedCommittedInvoiceIds, createdInvoiceIds, cbaInvoicesInput);
                } else {
                    cbaWrapper.runCBALogicWithNotificationEvents(adjustedCommittedInvoiceIds, createdInvoiceIds, allInvoiceIds);
                }

                if (trackingIds != null && !trackingIds.isEmpty()) {
                    final InvoiceTrackingSqlDao trackingIdsSqlDao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
                    trackingIdsSqlDao.create(trackingIds, context);
                }

                if (returnCreatedInvoiceItems) {
                    if (invoiceItemsToCreate.isEmpty()) {
                        return Collections.emptyList();
                    } else {
                        final List<String> inovoiceIds = invoiceItemsToCreate.stream()
                                                                             .map(input -> input.getId().toString())
                                                                             .collect(Collectors.toUnmodifiableList());
                        return transInvoiceItemSqlDao.getByIds(inovoiceIds, context);
                    }
                } else {
                    return null;
                }
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString(), context);
            invoiceDaoHelper.populateChildren(invoices, invoicesTags, false, entitySqlDaoWrapperFactory, context);

            return invoices;
        });
    }

    @Override
    public Pagination<InvoiceModelDao> searchInvoices(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        Integer invoiceNumberParsed = null;
        try {
            invoiceNumberParsed = Integer.parseInt(searchKey);
        } catch (final NumberFormatException ignored) {
        }

        final Integer invoiceNumber = invoiceNumberParsed;

        final boolean isSearchKeyCurrency = isSearchKeyCurrency(searchKey);

        final SearchQuery searchQuery;
        if (isSearchKeyCurrency) {
            searchQuery = new SearchQuery(SqlOperator.OR);
            searchQuery.addSearchClause("currency", SqlOperator.EQ, searchKey);
        } else if (searchKey.startsWith(SEARCH_QUERY_MARKER)) {
            final Matcher matcher = BALANCE_QUERY_PATTERN.matcher(searchKey);
            if (matcher.matches()) {
                final BigDecimal balance = new BigDecimal(matcher.group("balance"));
                final SqlOperator comparisonOperator = SqlOperator.valueOf(matcher.group("comparator").toUpperCase(Locale.ROOT));
                return searchInvoicesByBalance(balance, comparisonOperator, offset, limit, context);
            }

            searchQuery = new SearchQuery(searchKey,
                                          Set.of("id",
                                                 "account_id",
                                                 "invoice_date",
                                                 "target_date",
                                                 "currency",
                                                 "status",
                                                 "migrated",
                                                 "parent_invoice",
                                                 "grp_id",
                                                 "created_by",
                                                 "created_date",
                                                 "updated_by",
                                                 "updated_date"));
        } else {
            searchQuery = new SearchQuery(SqlOperator.OR);
            searchQuery.addSearchClause("id", SqlOperator.EQ, searchKey);
            searchQuery.addSearchClause("account_id", SqlOperator.EQ, searchKey);
        }

        return paginationHelper.getPagination(InvoiceSqlDao.class,
                                              new PaginationIteratorBuilder<InvoiceModelDao, Invoice, InvoiceSqlDao>() {
                                                  @Override
                                                  public Long getCount(final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
                                                      if (invoiceNumber != null) {
                                                          return (Long) 1L;
                                                      }

                                                      return invoiceSqlDao.getSearchCount(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), context);
                                                  }

                                                  @Override
                                                  public Iterator<InvoiceModelDao> build(final InvoiceSqlDao invoiceSqlDao, final Long offset, final Long limit, final DefaultPaginationSqlDaoHelper.Ordering ordering, final InternalTenantContext context) {
                                                      try {
                                                          if (invoiceNumber != null) {
                                                              return List.<InvoiceModelDao>of(getByNumber(invoiceNumber, false, context)).iterator();
                                                          }
                                                          return invoiceSqlDao.search(searchQuery.getSearchKeysBindMap(), searchQuery.getSearchAttributes(), searchQuery.getLogicalOperator(), offset, limit, ordering.toString(), context);
                                                      } catch (final InvoiceApiException ignored) {
                                                          return Collections.emptyIterator();
                                                      }
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);

    }

    Pagination<InvoiceModelDao> searchInvoicesByBalance(final BigDecimal balance, final SqlOperator comparisonOperator, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(InvoiceSqlDao.class,
                                              new PaginationIteratorBuilder<InvoiceModelDao, Invoice, InvoiceSqlDao>() {
                                                  @Override
                                                  public Long getCount(final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
                                                      return invoiceSqlDao.getSearchInvoicesByBalanceCount(balance, comparisonOperator, context);
                                                  }

                                                  @Override
                                                  public Iterator<InvoiceModelDao> build(final InvoiceSqlDao invoiceSqlDao, final Long offset, final Long limit, final DefaultPaginationSqlDaoHelper.Ordering ordering, final InternalTenantContext context) {
                                                      return invoiceSqlDao.searchInvoicesByBalance(balance, comparisonOperator, offset, limit, ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            BigDecimal cba = BigDecimal.ZERO;

            BigDecimal accountBalance = BigDecimal.ZERO;
            final List<InvoiceModelDao> invoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(false, true, invoicesTags, entitySqlDaoWrapperFactory, context);
            for (final InvoiceModelDao cur : invoices) {

                // Skip DRAFT OR VOID invoices
                if (cur.getStatus().equals(InvoiceStatus.DRAFT) || cur.getStatus().equals(InvoiceStatus.VOID)) {
                    continue;
                }

                final boolean hasZeroParentBalance =
                        cur.getParentInvoice() != null &&
                        (cur.getParentInvoice().isWrittenOff() ||
                         cur.getParentInvoice().getStatus() == InvoiceStatus.DRAFT ||
                         cur.getParentInvoice().getStatus() == InvoiceStatus.VOID ||
                         InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(cur.getParentInvoice()).compareTo(BigDecimal.ZERO) == 0);

                    // invoices that are WRITTEN_OFF or paid children invoices are excluded from balance computation but the cba summation needs to be included
                    final BigDecimal invoiceBalance = cur.isWrittenOff() || hasZeroParentBalance ? BigDecimal.ZERO : InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(cur);
                    accountBalance = accountBalance.add(invoiceBalance);
                    cba = cba.add(InvoiceModelDaoHelper.getCBAAmount(cur));
                }
                return accountBalance.subtract(cba);
        });
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entityWrapperFactory -> cbaDao.getAccountCBAFromTransaction(entityWrapperFactory, context));
    }

    @Override
    public List<InvoiceModelDao> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate startDate, @Nullable final LocalDate upToDate, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, entityWrapperFactory -> invoiceDaoHelper.getUnpaidInvoicesByAccountFromTransaction(accountId, invoicesTags, entityWrapperFactory, startDate, upToDate, context));
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getInvoiceIdByPaymentId(paymentId.toString(), context));
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getInvoicePayments(paymentId.toString(), context));
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByAccount(final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getByAccountRecordId(context));
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByInvoice(final UUID invoiceId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, daoWrapperFactory ->  daoWrapperFactory.become(InvoicePaymentSqlDao.class).getAllPaymentsForInvoiceIncludedInit(invoiceId.toString(), context));
    }

    @Override
    public InvoicePaymentModelDao getInvoicePaymentByCookieId(final String cookieId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, daoWrapperFactory -> daoWrapperFactory.become(InvoicePaymentSqlDao.class).getPaymentForCookieId(cookieId, context));
    }

    @Override
    public InvoicePaymentModelDao getInvoicePayment(final UUID invoicePaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(invoicePaymentId.toString(), context));
    }

    @Override
    public InvoicePaymentModelDao createRefund(final UUID paymentId, final UUID paymentAttemptId,
                                               final BigDecimal requestedRefundAmount, final boolean isInvoiceAdjusted,
                                               final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final String transactionExternalKey,
                                               final InvoicePaymentStatus status, final InternalCallContext context) throws InvoiceApiException {

        if (isInvoiceAdjusted && invoiceItemIdsWithNullAmounts.isEmpty()) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEMS_ADJUSTMENT_MISSING);
        }

        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

            final InvoiceSqlDao transInvoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            final List<InvoicePaymentModelDao> paymentsForId = transactional.getByPaymentId(paymentId.toString(), context);
            final InvoicePaymentModelDao payment = paymentsForId.stream()
                                                                .filter(input -> input.getType() == InvoicePaymentType.ATTEMPT && input.getStatus() == InvoicePaymentStatus.SUCCESS)
                                                                .findFirst()
                                                                .orElseThrow(() -> new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND, paymentId));

            // Retrieve the amounts to adjust, if needed
            final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = invoiceDaoHelper.computeItemAdjustments(payment.getInvoiceId().toString(),
                                                                                                            invoicesTags,
                                                                                                            entitySqlDaoWrapperFactory,
                                                                                                            invoiceItemIdsWithNullAmounts,
                                                                                                            context);

            // Compute the actual amount to refund
            final BigDecimal requestedPositiveAmount = invoiceDaoHelper.computePositiveRefundAmount(payment, requestedRefundAmount, invoiceItemIdsWithAmounts);

            InvoicePaymentModelDao result;

            // Before we go further, check if that refund already got inserted -- the payment system keeps a state machine
            // and so this call may be called several time for the same  paymentCookieId (which is really the refundId)
            final InvoicePaymentModelDao existingRefund = transactional.getPaymentForCookieId(transactionExternalKey, context);
            if (existingRefund != null) {

                Preconditions.checkState(existingRefund.getAmount().compareTo(requestedPositiveAmount.negate()) == 0,
                                         "Found refund for transactionExternalKey=" + transactionExternalKey + ", amount=" + existingRefund.getAmount() +
                                         "and does not match input amount=" + requestedPositiveAmount.negate());

                Preconditions.checkState(existingRefund.getPaymentId().compareTo(paymentId) == 0,
                                         "Found refund for transactionExternalKey=" + transactionExternalKey + ", paymentId=" + existingRefund.getPaymentId() +
                                         "and does not match input paymentId=" + paymentId);

                // The entry already exists, bail out (no need to send events, or compute cba logic)
                if (existingRefund.getStatus() == status) {
                    return existingRefund;
                }

                // We only update date and the status
                existingRefund.setStatus(status);
                existingRefund.setPaymentDate(context.getCreatedDate());
                transactional.updateAttempt(existingRefund.getId().toString(),
                                            existingRefund.getPaymentId().toString(),
                                            existingRefund.getPaymentDate().toDate(),
                                            existingRefund.getAmount(),
                                            existingRefund.getCurrency(),
                                            existingRefund.getProcessedCurrency(),
                                            existingRefund.getPaymentCookieId(),
                                            existingRefund.getLinkedInvoicePaymentId().toString(),
                                            existingRefund.getStatus().toString(),
                                            context);
                result = existingRefund;
            } else {
                final InvoicePaymentModelDao refund = new InvoicePaymentModelDao(UUIDs.randomUUID(), context.getCreatedDate(), InvoicePaymentType.REFUND,
                                                                                 payment.getInvoiceId(), paymentId,
                                                                                 context.getCreatedDate(), requestedPositiveAmount.negate(),
                                                                                 payment.getCurrency(), payment.getProcessedCurrency(), transactionExternalKey, payment.getId(), status);
                result = createAndRefresh(transactional, refund, context);
            }

            if (status == InvoicePaymentStatus.SUCCESS) {
                // Retrieve invoice after the Refund
                final InvoiceModelDao invoice = transInvoiceDao.getById(payment.getInvoiceId().toString(), context);
                Preconditions.checkState(invoice != null, "Invoice shouldn't be null for payment " + payment.getId());
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, false, entitySqlDaoWrapperFactory, context);

                final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

                // At this point, we created the refund which made the invoice balance positive and applied any existing
                // available CBA to that invoice.
                // We now need to adjust the invoice and/or invoice items if needed and specified.
                final Set<UUID> initSet = new HashSet<>();
                if (isInvoiceAdjusted) {
                    // Invoice item adjustment
                    for (final Entry<UUID, BigDecimal> entry : invoiceItemIdsWithAmounts.entrySet()) {
                        final BigDecimal adjAmount = entry.getValue();
                        final InvoiceItemModelDao item = invoiceDaoHelper.createAdjustmentItem(entitySqlDaoWrapperFactory, invoice.getId(), entry.getKey(), adjAmount,
                                                                                               invoice.getCurrency(), context.getCreatedDate().toLocalDate(),
                                                                                               context);

                        createInvoiceItemFromTransaction(transInvoiceItemDao, item, context);
                        invoice.addInvoiceItem(item);
                    }
                    initSet.add(invoice.getId());
                }

                // The invoice object has been kept up-to-date, we can pass it to CBA complexity
                final CBALogicWrapper cbaWrapper = new CBALogicWrapper(invoice.getAccountId(), invoicesTags, context, entitySqlDaoWrapperFactory);
                cbaWrapper.runCBALogicWithNotificationEvents(initSet, Collections.emptySet(), List.of(invoice));

            }
            final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(result.getId().toString(), context);
            notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, result, accountId, paymentAttemptId, context.getUserToken(), context);
            return result;
        });
    }

    @Override
    public InvoicePaymentModelDao postChargeback(final UUID paymentId, final UUID paymentAttemptId, final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

            final List<InvoicePaymentModelDao> invoicePayments = transactional.getByPaymentId(paymentId.toString(), context);
            final InvoicePaymentModelDao invoicePayment = invoicePayments.stream()
                                                                         .filter(input -> input.getType() == InvoicePaymentType.ATTEMPT)
                                                                         .findFirst()
                                                                         .orElseThrow(() -> new InvoiceApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId));

            // We expect the code to correctly pass the account currency -- the payment code, more generic accept chargeBack in different currencies,
            // but this is only for direct payment (no invoice)
            Preconditions.checkArgument(invoicePayment.getCurrency() == currency, String.format("Invoice payment currency %s doesn't match chargeback currency %s", invoicePayment.getCurrency(), currency));

            final UUID invoicePaymentId = invoicePayment.getId();
            final BigDecimal maxChargedBackAmount = invoiceDaoHelper.getRemainingAmountPaidFromTransaction(invoicePaymentId, entitySqlDaoWrapperFactory, context);
            final BigDecimal requestedChargedBackAmount = (amount == null) ? maxChargedBackAmount : amount;
            if (requestedChargedBackAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_IS_NEGATIVE);
            }
            if (requestedChargedBackAmount.compareTo(maxChargedBackAmount) > 0) {
                throw new InvoiceApiException(ErrorCode.CHARGE_BACK_AMOUNT_TOO_HIGH, requestedChargedBackAmount, maxChargedBackAmount);
            }

            final InvoicePaymentModelDao payment = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(invoicePaymentId.toString(), context);
            if (payment == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND, invoicePaymentId.toString());
            }
            final InvoicePaymentModelDao chargeBack = new InvoicePaymentModelDao(UUIDs.randomUUID(), context.getCreatedDate(), InvoicePaymentType.CHARGED_BACK,
                                                                                 payment.getInvoiceId(), payment.getPaymentId(), context.getCreatedDate(),
                                                                                 requestedChargedBackAmount.negate(), payment.getCurrency(), payment.getProcessedCurrency(),
                                                                                 chargebackTransactionExternalKey, payment.getId(), InvoicePaymentStatus.SUCCESS);
            createAndRefresh(transactional, chargeBack, context);

            // Notify the bus since the balance of the invoice changed
            final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargeBack.getId().toString(), context);

            final CBALogicWrapper cbaWrapper = new CBALogicWrapper(accountId, invoicesTags, context, entitySqlDaoWrapperFactory);
            cbaWrapper.runCBALogicWithNotificationEvents(Set.of(payment.getInvoiceId()));

            notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, chargeBack, accountId, paymentAttemptId, context.getUserToken(), context);

            return chargeBack;
        });
    }

    @Override
    public InvoicePaymentModelDao postChargebackReversal(final UUID paymentId, final UUID paymentAttemptId, final String chargebackTransactionExternalKey, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

            final InvoicePaymentModelDao invoicePayment = transactional.getPaymentForCookieId(chargebackTransactionExternalKey, context);
            if (invoicePayment == null) {
                throw new InvoiceApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
            }

            transactional.updateAttempt(invoicePayment.getId().toString(),
                                        invoicePayment.getPaymentId().toString(),
                                        invoicePayment.getPaymentDate().toDate(),
                                        invoicePayment.getAmount(),
                                        invoicePayment.getCurrency(),
                                        invoicePayment.getProcessedCurrency(),
                                        invoicePayment.getPaymentCookieId(),
                                        invoicePayment.getLinkedInvoicePaymentId() == null ? null : invoicePayment.getLinkedInvoicePaymentId().toString(),
                                        InvoicePaymentStatus.INIT.toString(),
                                        context);
            final InvoicePaymentModelDao chargebackReversed = transactional.getByRecordId(invoicePayment.getRecordId(), context);

            // Notify the bus since the balance of the invoice changed
            final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargebackReversed.getId().toString(), context);

            final CBALogicWrapper cbaWrapper = new CBALogicWrapper(accountId, invoicesTags, context, entitySqlDaoWrapperFactory);
            cbaWrapper.runCBALogicWithNotificationEvents(Set.of(chargebackReversed.getInvoiceId()));

            notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, chargebackReversed, accountId, paymentAttemptId, context.getUserToken(), context);

            return chargebackReversed;
        });
    }

    @Override
    public InvoiceItemModelDao doCBAComplexity(final InvoiceModelDao invoice, final InternalCallContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> cbaDao.computeCBAComplexity(invoice, null, entitySqlDaoWrapperFactory, context));
    }

    @Override
    public Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId, final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final InternalTenantContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);
        return transactionalSqlDao.execute(false, InvoiceApiException.class, sqlWrapperFactory -> invoiceDaoHelper.computeItemAdjustments(invoiceId, invoicesTags, sqlWrapperFactory, invoiceItemIdsWithNullAmounts, context));
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> invoiceDaoHelper.getRemainingAmountPaidFromTransaction(invoicePaymentId, entitySqlDaoWrapperFactory, context));
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final UUID accountId = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getAccountIdFromInvoicePaymentId(invoicePaymentId.toString(), context);
            if (accountId == null) {
                throw new InvoiceApiException(ErrorCode.CHARGE_BACK_COULD_NOT_FIND_ACCOUNT_ID, invoicePaymentId);
            } else {
                return accountId;
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, sqlWrapperFactory -> sqlWrapperFactory.become(InvoicePaymentSqlDao.class).getChargeBacksByAccountId(accountId.toString(), context));
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getChargebacksByPaymentId(paymentId.toString(), context));
    }

    @Override
    public InvoicePaymentModelDao getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentModelDao chargeback = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(chargebackId.toString(), context);
            if (chargeback == null) {
                throw new InvoiceApiException(ErrorCode.CHARGE_BACK_DOES_NOT_EXIST, chargebackId);
            } else {
                return chargeback;
            }
        });
    }

    @Override
    public InvoiceItemModelDao getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoiceItemById(externalChargeId, context);
    }

    /**
     * {@link #getExternalChargeById(UUID, InternalTenantContext)} and {@link #getCreditById(UUID, InternalTenantContext)}
     * contains the same logic, thus unify them here.
     */
    private InvoiceItemModelDao getInvoiceItemById(final UUID invoiceItemId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            final InvoiceItemModelDao invoiceItemModelDao = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
            if (invoiceItemModelDao == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId.toString());
            }
            return invoiceItemModelDao;
        });
    }

    @Override
    public void notifyOfPaymentInit(final InvoicePaymentModelDao invoicePayment, final UUID paymentAttemptId, final InternalCallContext context) {
        notifyOfPaymentCompletionInternal(invoicePayment, paymentAttemptId, false, context);
    }

    @Override
    public void notifyOfPaymentCompletion(final InvoicePaymentModelDao invoicePayment, final UUID paymentAttemptId, final InternalCallContext context) {
        notifyOfPaymentCompletionInternal(invoicePayment, paymentAttemptId, true, context);
    }

    private void notifyOfPaymentCompletionInternal(final InvoicePaymentModelDao invoicePayment, final UUID paymentAttemptId, final boolean completion, final InternalCallContext context) {
        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
            //
            // In case of notifyOfPaymentInit we always want to record the row with status = INIT
            // Otherwise, if the payment id is null, the payment wasn't attempted (e.g. no payment method so we don't record an attempt but send
            // an event nonetheless (e.g. for Overdue))
            //
            if (!completion || invoicePayment.getPaymentId() != null) {
                //
                // extract entries by invoiceId (which is always set, as opposed to paymentId) and then filter based on type and
                // paymentCookieId = transactionExternalKey
                //
                final List<InvoicePaymentModelDao> invoicePayments = transactional.getAllPaymentsForInvoiceIncludedInit(invoicePayment.getInvoiceId().toString(), context);
                final InvoicePaymentModelDao existingAttempt = invoicePayments.stream()
                                                                              .filter(input -> input.getType() == InvoicePaymentType.ATTEMPT &&
                                                                                               input.getPaymentCookieId().equals(invoicePayment.getPaymentCookieId()))
                                                                              .findFirst()
                                                                              .orElse(null);

                if (existingAttempt == null) {
                    createAndRefresh(transactional, invoicePayment, context);
                } else {
                    final UUID updatedPaymentId;
                    if (invoicePayment.getPaymentId() == null) {
                        // Likely invalid state (https://github.com/killbill/killbill/issues/1230) but go ahead nonetheless to run the payment state machine validations
                        updatedPaymentId = existingAttempt.getPaymentId();
                    } else if (existingAttempt.getPaymentId() == null) {
                        updatedPaymentId = invoicePayment.getPaymentId();
                    } else {
                        Preconditions.checkState(invoicePayment.getPaymentId().equals(existingAttempt.getPaymentId()),
                                                 "PaymentAttempt cannot change paymentId: attemptId=%s, newInvoicePaymentId=%s, existingPaymentId=%s", existingAttempt.getId(), invoicePayment.getPaymentId(), existingAttempt.getPaymentId());
                        updatedPaymentId = invoicePayment.getPaymentId();
                    }
                    transactional.updateAttempt(existingAttempt.getId().toString(),
                                                updatedPaymentId == null ? null : updatedPaymentId.toString(),
                                                invoicePayment.getPaymentDate().toDate(),
                                                invoicePayment.getAmount(),
                                                invoicePayment.getCurrency(),
                                                invoicePayment.getProcessedCurrency(),
                                                invoicePayment.getPaymentCookieId(),
                                                null,
                                                invoicePayment.getStatus().toString(),
                                                context);
                }
            }

            if (completion) {
                final UUID accountId = nonEntityDao.retrieveIdFromObjectInTransaction(context.getAccountRecordId(), ObjectType.ACCOUNT, objectIdCacheController, entitySqlDaoWrapperFactory.getHandle());
                notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, invoicePayment, accountId, paymentAttemptId, context.getUserToken(), context);
            }
            return null;
        });
    }

    @Override
    public InvoiceItemModelDao getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        return getInvoiceItemById(creditId, context);
    }

    private BigDecimal reclaimCreditFromTransaction(final BigDecimal amountToReclaim,
                                                    final Set<UUID> invoiceIds,
                                                    final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                    final InternalCallContext context) throws InvoiceApiException, EntityPersistenceException {

        final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        final List<InvoiceItemModelDao> cbaUsedItems = transactional.getConsumedCBAItems(context);

        BigDecimal leftToReclaim = amountToReclaim;
        for (final InvoiceItemModelDao cbaItem : cbaUsedItems) {

            final BigDecimal positiveCbaAmount = cbaItem.getAmount().negate();
            final BigDecimal adjustedAmount = leftToReclaim.compareTo(positiveCbaAmount) >= 0 ? positiveCbaAmount : leftToReclaim;
            final BigDecimal itemAmount = positiveCbaAmount.subtract(adjustedAmount);
            transactional.updateItemFields(cbaItem.getId().toString(), itemAmount.negate(), null,"Reclaim used credit", null, null, context);

            invoiceIds.add(cbaItem.getInvoiceId());
            leftToReclaim = leftToReclaim.subtract(adjustedAmount);
            if (leftToReclaim.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        return amountToReclaim.subtract(leftToReclaim);
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {

        final List<Tag> invoicesTags = getInvoicesTags(context);
        final Set<UUID> invoiceIds = new HashSet<>();

        transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            // Retrieve the invoice and make sure it belongs to the right account
            final InvoiceModelDao invoice = invoiceSqlDao.getById(invoiceId.toString(), context);
            if (invoice == null ||
                !invoice.getAccountId().equals(accountId) ||
                invoice.isMigrated() ||
                invoice.getStatus() != InvoiceStatus.COMMITTED) {
                throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
            }
            invoiceDaoHelper.populateChildren(invoice, invoicesTags, false, entitySqlDaoWrapperFactory, context);

            // Retrieve the invoice item and make sure it belongs to the right invoice
            final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            final InvoiceItemModelDao cbaItem = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
            if (cbaItem == null || !cbaItem.getInvoiceId().equals(invoice.getId())) {
                throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
            }

            if (cbaItem.getAmount().compareTo(BigDecimal.ZERO) < 0) { /* Credit consumption */

                invoiceItemSqlDao.updateItemFields(cbaItem.getId().toString(), BigDecimal.ZERO, null,"Delete used credit", null, null, context);
                invoiceIds.add(invoice.getId());
            } else if (cbaItem.getAmount().compareTo(BigDecimal.ZERO) > 0) {  /* Credit generation */
                final InvoiceItemModelDao creditItem = invoice.getInvoiceItems().stream()
                                                              .filter(targetItem -> targetItem.getType() == InvoiceItemType.CREDIT_ADJ &&
                                                                                    targetItem.getAmount().negate().compareTo(cbaItem.getAmount()) >= 0)
                                                              .findFirst()
                                                              .orElse(null);

                // In case this is a credit invoice (pure credit, or mixed), we allow to 'delete' credit generation
                if (creditItem != null) { /* Credit Invoice */
                    final BigDecimal accountCBA = cbaDao.getAccountCBAFromTransaction(entitySqlDaoWrapperFactory, context);
                    // If we don't have enough credit left on the account, we reclaim what is necessary
                    if (accountCBA.compareTo(cbaItem.getAmount()) < 0) {
                        final BigDecimal amountToReclaim = cbaItem.getAmount().subtract(accountCBA);
                        final BigDecimal reclaimed = reclaimCreditFromTransaction(amountToReclaim, invoiceIds, entitySqlDaoWrapperFactory, context);
                        Preconditions.checkState(reclaimed.compareTo(amountToReclaim) == 0,
                                                 String.format("Unexpected state, reclaimed used credit [%s/%s]", reclaimed, amountToReclaim));
                    }

                    invoiceItemSqlDao.updateItemFields(cbaItem.getId().toString(), BigDecimal.ZERO, null, "Delete gen credit", null, null, context);
                    final BigDecimal adjustedCreditAmount = creditItem.getAmount().add(cbaItem.getAmount());
                    invoiceItemSqlDao.updateItemFields(creditItem.getId().toString(), adjustedCreditAmount, null,null,null, "Delete gen credit", context);
                    invoiceIds.add(invoice.getId());
                } else /* System generated credit, e.g Repair invoice */ {
                    throw new InvoiceApiException(ErrorCode.INVOICE_CBA_DELETED, cbaItem.getId());
                }
            }
            // renamed to 'invId' because: Variable 'invoiceId' is already defined in the scope
            for (final UUID invId : invoiceIds) {
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invId, accountId, context.getUserToken(), context);
            }
            return null;
        });
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            cbaDao.doCBAComplexityFromTransaction(invoicesTags, entitySqlDaoWrapperFactory, context);
            return null;
        });
    }

    private void notifyOfFutureBillingEvents(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                             final UUID accountId,
                                             final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                             final InternalCallContext internalCallContext) {
        if (callbackDateTimePerSubscriptions == null) {
            return;
        }
        for (final LocalDate notificationDate : callbackDateTimePerSubscriptions.getNotificationsForTrigger().keySet()) {
            final DateTime notificationDateTime = internalCallContext.toUTCDateTime(notificationDate);
            final Set<UUID> subscriptionIds = callbackDateTimePerSubscriptions.getNotificationsForTrigger().get(notificationDate);
            nextBillingDatePoster.insertNextBillingNotificationFromTransaction(entitySqlDaoWrapperFactory, accountId, subscriptionIds, notificationDateTime, callbackDateTimePerSubscriptions.isRescheduled(), internalCallContext);
        }

        final long dryRunNotificationTime = invoiceConfig.getDryRunNotificationSchedule(internalCallContext).getMillis();
        if (dryRunNotificationTime > 0) {
            for (final LocalDate notificationDate : callbackDateTimePerSubscriptions.getNotificationsForDryRun().keySet()) {
                final DateTime notificationDateTime = internalCallContext.toUTCDateTime(notificationDate);
                if (notificationDateTime.compareTo(internalCallContext.getCreatedDate()) > 0) {
                    final Set<UUID> subscriptionIds = callbackDateTimePerSubscriptions.getNotificationsForDryRun().get(notificationDate);
                    nextBillingDatePoster.insertNextBillingDryRunNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                                                             accountId,
                                                                                             subscriptionIds,
                                                                                             notificationDateTime,
                                                                                             notificationDateTime.plusMillis((int) dryRunNotificationTime),
                                                                                             internalCallContext);
                }
            }
        }
    }

    private void notifyBusOfInvoiceAdjustment(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                              final UUID invoiceId, final UUID accountId,
                                              final UUID userToken, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultInvoiceAdjustmentEvent(invoiceId,
                                                                           accountId,
                                                                           context.getAccountRecordId(),
                                                                           context.getTenantRecordId(),
                                                                           userToken),
                                         entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.warn("Failed to post adjustment event for invoiceId='{}'", invoiceId, e);
        }
    }

    private void notifyBusOfInvoicePayment(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                           final InvoicePaymentModelDao invoicePaymentModelDao,
                                           final UUID accountId, final UUID paymentAttemptId, final UUID userToken,
                                           final InternalCallContext context) {
        final BusEvent busEvent;
        if (InvoicePaymentStatus.SUCCESS == invoicePaymentModelDao.getStatus()) {
            busEvent = new DefaultInvoicePaymentInfoEvent(accountId,
                                                          invoicePaymentModelDao.getPaymentId(),
                                                          paymentAttemptId,
                                                          invoicePaymentModelDao.getType(),
                                                          invoicePaymentModelDao.getInvoiceId(),
                                                          invoicePaymentModelDao.getPaymentDate(),
                                                          invoicePaymentModelDao.getAmount(),
                                                          invoicePaymentModelDao.getCurrency(),
                                                          invoicePaymentModelDao.getLinkedInvoicePaymentId(),
                                                          invoicePaymentModelDao.getPaymentCookieId(),
                                                          invoicePaymentModelDao.getProcessedCurrency(),
                                                          context.getAccountRecordId(),
                                                          context.getTenantRecordId(),
                                                          userToken);
        } else {
            busEvent = new DefaultInvoicePaymentErrorEvent(accountId,
                                                           invoicePaymentModelDao.getPaymentId(),
                                                           paymentAttemptId,
                                                           invoicePaymentModelDao.getType(),
                                                           invoicePaymentModelDao.getInvoiceId(),
                                                           invoicePaymentModelDao.getPaymentDate(),
                                                           invoicePaymentModelDao.getAmount(),
                                                           invoicePaymentModelDao.getCurrency(),
                                                           invoicePaymentModelDao.getLinkedInvoicePaymentId(),
                                                           invoicePaymentModelDao.getPaymentCookieId(),
                                                           invoicePaymentModelDao.getProcessedCurrency(),
                                                           context.getAccountRecordId(),
                                                           context.getTenantRecordId(),
                                                           userToken);
        }
        try {
            eventBus.postFromTransaction(busEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.warn("Failed to post invoice payment event for invoiceId='{}'", invoicePaymentModelDao.getInvoiceId(), e);
        }
    }

    private void createInvoiceItemFromTransaction(final InvoiceItemSqlDao invoiceItemSqlDao,
                                                  final InvoiceItemModelDao invoiceItemModelDao,
                                                  final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        validateInvoiceItemToBeAdjustedIfNeeded(invoiceItemSqlDao, invoiceItemModelDao, context);

        createAndRefresh(invoiceItemSqlDao, invoiceItemModelDao, context);
    }

    private void createInvoiceItemsFromTransaction(final InvoiceItemSqlDao invoiceItemSqlDao,
                                                   final List<InvoiceItemModelDao> invoiceItemModelDaos,
                                                   final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        for (final InvoiceItemModelDao invoiceItemModelDao : invoiceItemModelDaos) {
            validateInvoiceItemToBeAdjustedIfNeeded(invoiceItemSqlDao, invoiceItemModelDao, context);
        }

        bulkCreate(invoiceItemSqlDao, invoiceItemModelDaos, context);
    }

    private void validateInvoiceItemToBeAdjustedIfNeeded(final InvoiceItemSqlDao invoiceItemSqlDao, final InvoiceItemModelDao invoiceItemModelDao, final InternalCallContext context) throws InvoiceApiException {
        // There is no efficient way to retrieve an invoice item given an ID today (and invoice plugins can put item adjustments
        // on a different invoice than the original item), so it's easier to do the check in the DAO rather than in the API layer
        // See also https://github.com/killbill/killbill/issues/7
        if (InvoiceItemType.ITEM_ADJ.equals(invoiceItemModelDao.getType())) {
            validateInvoiceItemToBeAdjusted(invoiceItemSqlDao, invoiceItemModelDao, context);
        }
    }

    private void validateInvoiceItemToBeAdjusted(final InvoiceItemSqlDao invoiceItemSqlDao, final InvoiceItemModelDao invoiceItemModelDao, final InternalCallContext context) throws InvoiceApiException {
        Preconditions.checkNotNull(invoiceItemModelDao.getLinkedItemId(), "LinkedItemId cannot be null for ITEM_ADJ item: " + invoiceItemModelDao);
        // Note: this assumes the linked item has already been created in or prior to the transaction, which should almost always be the case
        // (unless some whacky plugin creates an out-of-order item adjustment on a subsequent external charge)
        final InvoiceItemModelDao invoiceItemToBeAdjusted = invoiceItemSqlDao.getById(invoiceItemModelDao.getLinkedItemId().toString(), context);
        if (!INVOICE_ITEM_TYPES_ADJUSTABLE.contains(invoiceItemToBeAdjusted.getType())) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_ADJUSTMENT_ITEM_INVALID, invoiceItemToBeAdjusted.getId());
        }
    }

    @Override
    public void changeInvoiceStatus(final UUID invoiceId, final InvoiceStatus newStatus, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

            // Retrieve the invoice and make sure it belongs to the right account
            final InvoiceModelDao invoice = transactional.getById(invoiceId.toString(), context);

            if (invoice == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
            }

            if (invoice.getStatus().equals(newStatus) || invoice.getStatus().equals(InvoiceStatus.VOID)) {
                throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_STATUS, newStatus, invoiceId, invoice.getStatus());
            }

            transactional.updateStatusAndTargetDate(invoiceId.toString(), newStatus.toString(), invoice.getTargetDate(), context);

            // Run through all invoices
            // Current invoice could be a credit item that needs to be rebalanced
            cbaDao.doCBAComplexityFromTransaction(invoicesTags, entitySqlDaoWrapperFactory, context);

            // Invoice creation event sent on COMMITTED
            if (InvoiceStatus.COMMITTED.equals(newStatus)) {
                notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, invoice, context);
            } else if (InvoiceStatus.VOID.equals(newStatus)) {
                // https://github.com/killbill/killbill/issues/1448
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoiceId, invoice.getAccountId(), context.getUserToken(), context);

                // Deactivate any usage trackingIds if necessary
                final InvoiceTrackingSqlDao trackingSqlDao = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
                final List<InvoiceTrackingModelDao> invoiceTrackingModelDaos = trackingSqlDao.getTrackingsForInvoices(List.of(invoiceId.toString()), context);
                if (!invoiceTrackingModelDaos.isEmpty()) {
                    final Collection<String> invoiceTrackingIdsToDeactivate = invoiceTrackingModelDaos.stream()
                            .map(input -> input.getId().toString())
                            .collect(Collectors.toUnmodifiableList());

                    trackingSqlDao.deactivateByIds(invoiceTrackingIdsToDeactivate, context);
                }
            }
            return null;
        });
    }

    private void notifyBusOfInvoiceCreation(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InvoiceModelDao invoice, final InternalCallContext context) {
        try {
            // This is called for a new COMMITTED invoice (which cannot be writtenOff as it does not exist yet, so rawBalance == balance)
            final BigDecimal rawBalance = InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice);
            final DefaultInvoiceCreationEvent event = new DefaultInvoiceCreationEvent(invoice.getId(),
                                                                                      invoice.getAccountId(),
                                                                                      rawBalance,
                                                                                      invoice.getCurrency(),
                                                                                      context.getAccountRecordId(),
                                                                                      context.getTenantRecordId(),
                                                                                      context.getUserToken());
            eventBus.postFromTransaction(event, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.error("Failed to post invoice creation event for account '{}'", invoice.getAccountId(), e);
        }
    }

    private void notifyOfParentInvoiceCreation(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InvoiceModelDao parentInvoice,
                                               final InternalCallContext context) {
        final LocalTime localTime = LocalTime.parse(invoiceConfig.getParentAutoCommitUtcTime(context));

        DateTime targetFutureNotificationDate = context.getCreatedDate().withTime(localTime);
        while (targetFutureNotificationDate.compareTo(context.getCreatedDate()) < 0) {
            targetFutureNotificationDate = targetFutureNotificationDate.plusDays(1);
        }

        parentInvoiceCommitmentPoster.insertParentInvoiceFromTransactionInternal(entitySqlDaoWrapperFactory, parentInvoice.getId(), targetFutureNotificationDate, context);
    }

    @Override
    public void createParentChildInvoiceRelation(final InvoiceParentChildModelDao invoiceRelation, final InternalCallContext context) throws InvoiceApiException {
        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            final InvoiceParentChildrenSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
            createAndRefresh(transactional, invoiceRelation, context);
            return null;
        });
    }

    @Override
    public List<InvoiceParentChildModelDao> getChildInvoicesByParentInvoiceId(final UUID parentInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceParentChildrenSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
            return transactional.getChildInvoicesByParentInvoiceId(parentInvoiceId.toString(), context);
        });
    }

    @Override
    public InvoiceModelDao getParentDraftInvoice(final UUID parentAccountId, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            final InvoiceModelDao invoice = invoiceSqlDao.getParentDraftInvoice(parentAccountId.toString(), context);
            if (invoice != null) {
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, false, entitySqlDaoWrapperFactory, context);
            }
            return invoice;
        });
    }

    @Override
    public void updateInvoiceItemAmount(final UUID invoiceItemId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {
        transactionalSqlDao.execute(false, InvoiceApiException.class, entitySqlDaoWrapperFactory -> {
            final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

            // Retrieve the invoice and make sure it belongs to the right account
            final InvoiceItemModelDao invoiceItem = transactional.getById(invoiceItemId.toString(), context);

            if (invoiceItem == null) {
                throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
            }

            transactional.updateItemFields(invoiceItemId.toString(), amount, null,null, null,null, context);
            return null;
        });
    }

    @Override
    public void transferChildCreditToParent(final Account childAccount, final InternalCallContext childAccountContext) throws InvoiceApiException {
        // Need to create an internalCallContext for parent account because it's needed to save the correct accountRecordId in Invoice tables.
        // Then it's used to load invoices by account.
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(childAccount.getParentAccountId(), childAccountContext);
        final InternalCallContext parentAccountContext = internalCallContextFactory.createInternalCallContext(internalTenantContext.getAccountRecordId(), childAccountContext);

        final List<Tag> parentInvoicesTags = getInvoicesTags(parentAccountContext);
        final List<Tag> childInvoicesTags = getInvoicesTags(childAccountContext);

        transactionalSqlDao.execute(false, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

            // create child and parent invoices

            final DateTime childCreatedDate = childAccountContext.getCreatedDate();
            final BigDecimal accountCBA = getAccountCBA(childAccount.getId(), childAccountContext);

            // create external charge to child account
            final LocalDate childInvoiceDate = childAccountContext.toLocalDate(childAccountContext.getCreatedDate());
            final Invoice invoiceForExternalCharge = new DefaultInvoice(childAccount.getId(),
                                                                        childInvoiceDate,
                                                                        childCreatedDate.toLocalDate(),
                                                                        childAccount.getCurrency(),
                                                                        InvoiceStatus.COMMITTED);
            final String chargeDescription = "Charge to move credit from child to parent account";
            final InvoiceItem externalChargeItem = new ExternalChargeInvoiceItem(UUIDs.randomUUID(),
                                                                                 childCreatedDate,
                                                                                 invoiceForExternalCharge.getId(),
                                                                                 childAccount.getId(),
                                                                                 null,
                                                                                 chargeDescription,
                                                                                 childCreatedDate.toLocalDate(),
                                                                                 childCreatedDate.toLocalDate(),
                                                                                 accountCBA,
                                                                                 childAccount.getCurrency(),
                                                                                 null);
            invoiceForExternalCharge.addInvoiceItem(externalChargeItem);

            // create credit to parent account
            final LocalDate parentInvoiceDate = parentAccountContext.toLocalDate(parentAccountContext.getCreatedDate());
            final Invoice invoiceForCredit = new DefaultInvoice(childAccount.getParentAccountId(),
                                                                parentInvoiceDate,
                                                                childCreatedDate.toLocalDate(),
                                                                childAccount.getCurrency(),
                                                                InvoiceStatus.COMMITTED);
            final String creditDescription = "Credit migrated from child account " + childAccount.getId();
            final InvoiceItem creditItem = new CreditAdjInvoiceItem(UUIDs.randomUUID(),
                                                                    childCreatedDate,
                                                                    invoiceForCredit.getId(),
                                                                    childAccount.getParentAccountId(),
                                                                    childCreatedDate.toLocalDate(),
                                                                    creditDescription,
                                                                    // Note! The amount is negated here!
                                                                    accountCBA.negate(),
                                                                    childAccount.getCurrency(),
                                                                    null);
            invoiceForCredit.addInvoiceItem(creditItem);

            // save invoices and invoice items
            final InvoiceModelDao childInvoice = new InvoiceModelDao(invoiceForExternalCharge);
            createAndRefresh(invoiceSqlDao, childInvoice, childAccountContext);
            final InvoiceItemModelDao childExternalChargeItem = new InvoiceItemModelDao(externalChargeItem);
            createInvoiceItemFromTransaction(transInvoiceItemSqlDao, childExternalChargeItem, childAccountContext);
            // Keep invoice up-to-date for CBA below
            childInvoice.addInvoiceItem(childExternalChargeItem);

            final InvoiceModelDao parentInvoice = new InvoiceModelDao(invoiceForCredit);
            createAndRefresh(invoiceSqlDao, parentInvoice, parentAccountContext);
            final InvoiceItemModelDao parentCreditItem = new InvoiceItemModelDao(creditItem);
            createInvoiceItemFromTransaction(transInvoiceItemSqlDao, parentCreditItem, parentAccountContext);
            // Keep invoice up-to-date for CBA below
            parentInvoice.addInvoiceItem(parentCreditItem);

            // Create Mapping relation
            final InvoiceParentChildrenSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
            final InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(parentInvoice.getId(), childInvoice.getId(), childInvoice.getAccountId());
            createAndRefresh(transactional, invoiceRelation, parentAccountContext);

            // Add child CBA complexity and notify bus on child invoice creation
            final CBALogicWrapper childCbaWrapper = new CBALogicWrapper(childAccount.getId(), childInvoicesTags, childAccountContext, entitySqlDaoWrapperFactory);
            childCbaWrapper.runCBALogicWithNotificationEvents(Collections.emptySet(), Set.of(childInvoice.getId()), List.of(childInvoice));
            notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, childInvoice, childAccountContext);

            // Add parent CBA complexity and notify bus on child invoice creation
            final CBALogicWrapper cbaWrapper = new CBALogicWrapper(childAccount.getParentAccountId(), parentInvoicesTags, parentAccountContext, entitySqlDaoWrapperFactory);
            cbaWrapper.runCBALogicWithNotificationEvents(Collections.emptySet(), Set.of(parentInvoice.getId()), List.of(parentInvoice));
            notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, parentInvoice, parentAccountContext);

            return null;
        });
    }

    @Override
    public List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(final UUID parentInvoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            return transactional.getInvoiceItemsByParentInvoice(parentInvoiceId.toString(), context);
        });
    }

    @Override
    public List<InvoiceTrackingModelDao> getTrackingsByDateRange(final LocalDate startDate, final LocalDate endDate, final InternalCallContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceTrackingSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceTrackingSqlDao.class);
            return transactional.getTrackingsByDateRange(startDate.toDate(), endDate.toDate(), context);
        });
    }

    @Override
    public List<AuditLogWithHistory> getInvoiceAuditLogsWithHistoryForId(final UUID invoiceId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
            return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.INVOICES, invoiceId, auditLevel, context);
        });
    }

    @Override
    public List<AuditLogWithHistory> getInvoiceItemAuditLogsWithHistoryForId(final UUID invoiceItemId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.INVOICE_ITEMS, invoiceItemId, auditLevel, context);
        });
    }

    @Override
    public List<AuditLogWithHistory> getInvoicePaymentAuditLogsWithHistoryForId(final UUID invoicePaymentId, final AuditLevel auditLevel, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, entitySqlDaoWrapperFactory -> {
            final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
            return auditDao.getAuditLogsWithHistoryForId(transactional, TableName.INVOICE_PAYMENTS, invoicePaymentId, auditLevel, context);
        });
    }

    private class CBALogicWrapper {

        private final UUID accountId;
        private final List<Tag> invoicesTags;
        private final InternalCallContext context;
        private final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory;

        public CBALogicWrapper(final UUID accountId, final List<Tag> invoicesTags, final InternalCallContext context, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) {
            this.accountId = accountId;
            this.invoicesTags = invoicesTags;
            this.context = context;
            this.entitySqlDaoWrapperFactory = entitySqlDaoWrapperFactory;
        }

        public void runCBALogicWithNotificationEvents(final Set<UUID> allInvoiceIds) throws EntityPersistenceException, InvoiceApiException {
            runCBALogicWithNotificationEventsInternal(Collections.emptySet(), Collections.emptySet(), runCBALogicWithInvoiceIds(allInvoiceIds));
        }

        public void runCBALogicWithNotificationEvents(final Set<UUID> initSet, final Set<UUID> excludedSet, final Set<UUID> allInvoiceIds) throws EntityPersistenceException, InvoiceApiException {
            runCBALogicWithNotificationEventsInternal(initSet, excludedSet, runCBALogicWithInvoiceIds(allInvoiceIds));
        }

        public void runCBALogicWithNotificationEvents(final List<InvoiceModelDao> invoices) throws EntityPersistenceException, InvoiceApiException {
            runCBALogicWithNotificationEvents(Collections.emptySet(), Collections.emptySet(), invoices);
        }

        public void runCBALogicWithNotificationEvents(final Set<UUID> initSet, final Set<UUID> excludedSet, final List<InvoiceModelDao> invoices) throws EntityPersistenceException, InvoiceApiException {
            runCBALogicWithNotificationEventsInternal(initSet, excludedSet, runCBALogicWithInvoices(invoices));
        }

        private void runCBALogicWithNotificationEventsInternal(final Set<UUID> initSet, final Set<UUID> excludedSet, final Set<UUID> resCbaInvoiceIds) {
            final Set<UUID> candidateModifiedInvoiceIds = new HashSet<>(initSet);
            candidateModifiedInvoiceIds.addAll(resCbaInvoiceIds);
            final Set<UUID> modifiedInvoiceIds = Sets.difference(candidateModifiedInvoiceIds, excludedSet);
            for (UUID id : modifiedInvoiceIds) {
                notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, id, accountId, context.getUserToken(), context);
            }
        }

        private Set<UUID> runCBALogicWithInvoices(final List<InvoiceModelDao> invoices) throws EntityPersistenceException, InvoiceApiException {
            return cbaDao.doCBAComplexityFromTransaction(invoices, invoicesTags, entitySqlDaoWrapperFactory, context);
        }

        private Set<UUID> runCBALogicWithInvoiceIds(final Set<UUID> allInvoiceIds) throws EntityPersistenceException, InvoiceApiException {
            return cbaDao.doCBAComplexityFromTransaction(allInvoiceIds, invoicesTags, entitySqlDaoWrapperFactory, context);
        }
    }

    // PERF: fetch tags once. See also https://github.com/killbill/killbill/issues/720.
    @VisibleForTesting
    List<Tag> getInvoicesTags(final InternalTenantContext context) {
        return tagInternalApi.getTagsForAccountType(ObjectType.INVOICE, false, context);
    }

    private static boolean checkAgainstExistingInvoiceItemState(final InvoiceItemModelDao existingInvoiceItem, final InvoiceItemModelDao inputInvoiceItem) {
        boolean itemShouldBeUpdated = false;
        if (inputInvoiceItem.getAmount() != null) {
            itemShouldBeUpdated = existingInvoiceItem.getAmount() == null /* unlikely */ || inputInvoiceItem.getAmount().compareTo(existingInvoiceItem.getAmount()) != 0;
        } else if (inputInvoiceItem.getRate() != null) {
            itemShouldBeUpdated = existingInvoiceItem.getRate() == null || inputInvoiceItem.getRate().compareTo(existingInvoiceItem.getRate()) != 0;
        } else if (inputInvoiceItem.getQuantity() != null) {
            itemShouldBeUpdated = existingInvoiceItem.getQuantity() == null || inputInvoiceItem.getQuantity().compareTo(existingInvoiceItem.getQuantity()) != 0;
        } else if (inputInvoiceItem.getDescription() != null) {
            itemShouldBeUpdated = existingInvoiceItem.getDescription() == null || inputInvoiceItem.getDescription().compareTo(existingInvoiceItem.getDescription()) != 0;
        } else if (inputInvoiceItem.getItemDetails() != null) {
            itemShouldBeUpdated = existingInvoiceItem.getItemDetails() == null || inputInvoiceItem.getItemDetails().compareTo(existingInvoiceItem.getItemDetails()) != 0;
        }
        return itemShouldBeUpdated;
    }

    private static boolean isSearchKeyCurrency(final String searchKey) {
        for (final Currency cur : Currency.values()) {
            if (cur.toString().equalsIgnoreCase(searchKey)) {
                return true;
            }
        }
        return false;
    }
}
