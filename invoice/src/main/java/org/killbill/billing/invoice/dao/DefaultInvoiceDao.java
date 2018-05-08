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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
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
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.user.DefaultInvoiceAdjustmentEvent;
import org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.notification.NextBillingDatePoster;
import org.killbill.billing.invoice.notification.ParentInvoiceCommitmentPoster;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.Tag;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultInvoiceDao extends EntityDaoBase<InvoiceModelDao, Invoice, InvoiceApiException> implements InvoiceDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceDao.class);

    private static final Ordering<InvoiceModelDao> INVOICE_MODEL_DAO_ORDERING = Ordering.natural()
                                                                                        .onResultOf(new Function<InvoiceModelDao, Comparable>() {
                                                                                            @Override
                                                                                            public Comparable apply(final InvoiceModelDao invoice) {
                                                                                                return invoice.getTargetDate() == null ? invoice.getCreatedDate().toLocalDate() : invoice.getTargetDate();
                                                                                            }
                                                                                        });

    private static final Collection<InvoiceItemType> INVOICE_ITEM_TYPES_ADJUSTABLE = ImmutableList.<InvoiceItemType>of(InvoiceItemType.EXTERNAL_CHARGE,
                                                                                                                       InvoiceItemType.FIXED,
                                                                                                                       InvoiceItemType.RECURRING,
                                                                                                                       InvoiceItemType.TAX,
                                                                                                                       InvoiceItemType.USAGE,
                                                                                                                       InvoiceItemType.PARENT_SUMMARY);

    private final NextBillingDatePoster nextBillingDatePoster;
    private final PersistentBus eventBus;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceDaoHelper invoiceDaoHelper;
    private final CBADao cbaDao;
    private final InvoiceConfig invoiceConfig;
    private final Clock clock;
    private final CacheController<String, UUID> objectIdCacheController;
    private final NonEntityDao nonEntityDao;
    private final ParentInvoiceCommitmentPoster parentInvoiceCommitmentPoster;
    private final TagInternalApi tagInternalApi;

    @Inject
    public DefaultInvoiceDao(final TagInternalApi tagInternalApi,
                             final IDBI dbi,
                             @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi,
                             final NextBillingDatePoster nextBillingDatePoster,
                             final PersistentBus eventBus,
                             final Clock clock,
                             final CacheControllerDispatcher cacheControllerDispatcher,
                             final NonEntityDao nonEntityDao,
                             final InvoiceConfig invoiceConfig,
                             final InvoiceDaoHelper invoiceDaoHelper,
                             final CBADao cbaDao,
                             final ParentInvoiceCommitmentPoster parentInvoiceCommitmentPoster,
                             final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), InvoiceSqlDao.class);
        this.tagInternalApi = tagInternalApi;
        this.nextBillingDatePoster = nextBillingDatePoster;
        this.eventBus = eventBus;
        this.invoiceConfig = invoiceConfig;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceDaoHelper = invoiceDaoHelper;
        this.cbaDao = cbaDao;
        this.clock = clock;
        this.objectIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        this.nonEntityDao = nonEntityDao;
        this.parentInvoiceCommitmentPoster = parentInvoiceCommitmentPoster;
    }

    @Override
    protected InvoiceApiException generateAlreadyExistsException(final InvoiceModelDao entity, final InternalCallContext context) {
        return new InvoiceApiException(ErrorCode.INVOICE_ACCOUNT_ID_INVALID, entity.getId());
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = ImmutableList.<InvoiceModelDao>copyOf(INVOICE_MODEL_DAO_ORDERING.sortedCopy(Iterables.<InvoiceModelDao>filter(invoiceSqlDao.getByAccountRecordId(context),
                                                                                                                                                                     new Predicate<InvoiceModelDao>() {
                                                                                                                                                                         @Override
                                                                                                                                                                         public boolean apply(final InvoiceModelDao invoice) {
                                                                                                                                                                             return !invoice.isMigrated() &&
                                                                                                                                                                                    (includeVoidedInvoices ? true : !InvoiceStatus.VOID.equals(invoice.getStatus()));
                                                                                                                                                                         }
                                                                                                                                                                     })));
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getAllInvoicesByAccount(final Boolean includeVoidedInvoices, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(includeVoidedInvoices, invoicesTags, entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final LocalDate fromDate, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
                final List<InvoiceModelDao> invoices = getAllNonMigratedInvoicesByAccountAfterDate(includeVoidedInvoices, invoiceDao, fromDate, context);
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
        });
    }

    private List<InvoiceModelDao> getAllNonMigratedInvoicesByAccountAfterDate(final Boolean includeVoidedInvoices, final InvoiceSqlDao invoiceSqlDao, final LocalDate fromDate, final InternalTenantContext context) {
        return ImmutableList.<InvoiceModelDao>copyOf(INVOICE_MODEL_DAO_ORDERING.sortedCopy(Iterables.<InvoiceModelDao>filter(invoiceSqlDao.getByAccountRecordId(context),
                                                                                                                             new Predicate<InvoiceModelDao>() {
                                                                                                                                 @Override
                                                                                                                                 public boolean apply(final InvoiceModelDao invoice) {
                                                                                                                                     return !invoice.isMigrated() && invoice.getTargetDate().compareTo(fromDate) >= 0 &&
                                                                                                                                            (includeVoidedInvoices ? true : !InvoiceStatus.VOID.equals(invoice.getStatus()));
                                                                                                                                 }
                                                                                                                             })));
    }

    @Override
    public InvoiceModelDao getById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceModelDao>() {
            @Override
            public InvoiceModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao invoice = invoiceSqlDao.getById(invoiceId.toString(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
                return invoice;
            }
        });
    }

    @Override
    public InvoiceModelDao getByNumber(final Integer number, final InternalTenantContext context) throws InvoiceApiException {
        if (number == null) {
            throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_NUMBER, "(null)");
        }

        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceModelDao>() {
            @Override
            public InvoiceModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final InvoiceModelDao invoice = invoiceDao.getByRecordId(number.longValue(), context);
                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NUMBER_NOT_FOUND, number.longValue());
                }

                // The context may not contain the account record id at this point - we couldn't do it in the API above
                // as we couldn't get access to the invoice object until now.
                final InternalTenantContext contextWithAccountRecordId = internalCallContextFactory.createInternalTenantContext(invoice.getAccountId(), context);
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, contextWithAccountRecordId);

                return invoice;
            }
        });
    }

    @Override
    public void setFutureAccountNotificationsForEmptyInvoice(final UUID accountId, final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                                             final InternalCallContext context) {

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                notifyOfFutureBillingEvents(entitySqlDaoWrapperFactory, accountId, callbackDateTimePerSubscriptions, context);
                return null;
            }
        });

    }

    @Override
    public void createInvoice(final InvoiceModelDao invoice,
                              final FutureAccountNotifications callbackDateTimePerSubscriptions,
                              final InternalCallContext context) {
        createInvoices(ImmutableList.<InvoiceModelDao>of(invoice), callbackDateTimePerSubscriptions, context);
    }

    @Override
    public List<InvoiceItemModelDao> createInvoices(final List<InvoiceModelDao> invoices,
                                                    final InternalCallContext context) {
        return createInvoices(invoices, new FutureAccountNotifications(), context);
    }

    private List<InvoiceItemModelDao> createInvoices(final Iterable<InvoiceModelDao> invoices,
                                                     final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                                     final InternalCallContext context) {
        // Track invoices that are being created
        final Collection<UUID> createdInvoiceIds = new HashSet<UUID>();
        // Track invoices that already exist but are being committed -- AUTO_INVOICING_REUSE_DRAFT mode
        final Collection<UUID> committedReusedInvoiceId = new HashSet<UUID>();
        // Track all invoices that are referenced through all invoiceItems
        final Collection<UUID> allInvoiceIds = new HashSet<UUID>();
        // Track invoices that are committed but were not created or reused -- to sent the InvoiceAdjustment bus event
        final Collection<UUID> adjustedCommittedInvoiceIds = new HashSet<UUID>();

        final Collection<UUID> invoiceIdsReferencedFromItems = new HashSet<UUID>();
        for (final InvoiceModelDao invoiceModelDao : invoices) {
            for (final InvoiceItemModelDao invoiceItemModelDao : invoiceModelDao.getInvoiceItems()) {
                invoiceIdsReferencedFromItems.add(invoiceItemModelDao.getInvoiceId());
            }
        }

        if (Iterables.isEmpty(invoices)) {
            return ImmutableList.<InvoiceItemModelDao>of();
        }
        final UUID accountId = invoices.iterator().next().getAccountId();

        final List<Tag> invoicesTags = getInvoicesTags(context);

        final Map<UUID, InvoiceModelDao> invoiceByInvoiceId = new HashMap<UUID, InvoiceModelDao>();
        return transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<List<InvoiceItemModelDao>>() {
            @Override
            public List<InvoiceItemModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
                final InvoiceItemSqlDao transInvoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

                final List<InvoiceItemModelDao> createdInvoiceItems = new LinkedList<InvoiceItemModelDao>();
                for (final InvoiceModelDao invoiceModelDao : invoices) {
                    invoiceByInvoiceId.put(invoiceModelDao.getId(), invoiceModelDao);
                    final boolean isNotShellInvoice = invoiceIdsReferencedFromItems.remove(invoiceModelDao.getId());

                    final InvoiceModelDao invoiceOnDisk = invoiceSqlDao.getById(invoiceModelDao.getId().toString(), context);
                    if (isNotShellInvoice) {
                        // Create the invoice if this is not a shell invoice and it does not already exist
                        if (invoiceOnDisk == null) {
                            createAndRefresh(invoiceSqlDao, invoiceModelDao, context);
                            createdInvoiceIds.add(invoiceModelDao.getId());
                        } else if (invoiceOnDisk.getStatus() == InvoiceStatus.DRAFT && invoiceModelDao.getStatus() == InvoiceStatus.COMMITTED) {
                            invoiceSqlDao.updateStatus(invoiceModelDao.getId().toString(), InvoiceStatus.COMMITTED.toString(), context);
                            committedReusedInvoiceId.add(invoiceModelDao.getId());
                        }
                    }

                    // Create the invoice items if needed (note: they may not necessarily belong to that invoice)
                    for (final InvoiceItemModelDao invoiceItemModelDao : invoiceModelDao.getInvoiceItems()) {
                        final InvoiceItemModelDao existingInvoiceItem = transInvoiceItemSqlDao.getById(invoiceItemModelDao.getId().toString(), context);
                        // Because of AUTO_INVOICING_REUSE_DRAFT we expect an invoice were items might already exist.
                        // Also for ALLOWED_INVOICE_ITEM_TYPES, we expect plugins to potentially modify the amount
                        if (existingInvoiceItem == null) {
                            createdInvoiceItems.add(createInvoiceItemFromTransaction(transInvoiceItemSqlDao, invoiceItemModelDao, context));
                            allInvoiceIds.add(invoiceItemModelDao.getInvoiceId());
                        } else if (InvoicePluginDispatcher.ALLOWED_INVOICE_ITEM_TYPES.contains(invoiceItemModelDao.getType()) &&
                                   (invoiceItemModelDao.getAmount().compareTo(existingInvoiceItem.getAmount()) != 0)) {
                            checkAgainstExistingInvoiceItemState(existingInvoiceItem, invoiceItemModelDao);

                            transInvoiceItemSqlDao.updateAmount(invoiceItemModelDao.getId().toString(), invoiceItemModelDao.getAmount(), context);
                        }
                    }

                    final boolean wasInvoiceCreatedOrCommitted = createdInvoiceIds.contains(invoiceModelDao.getId()) ||
                                                                 committedReusedInvoiceId.contains(invoiceModelDao.getId());
                    if (InvoiceStatus.COMMITTED.equals(invoiceModelDao.getStatus())) {

                        if (wasInvoiceCreatedOrCommitted) {
                            notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, invoiceModelDao, context);
                        } else {
                            adjustedCommittedInvoiceIds.add(invoiceModelDao.getId());
                        }
                    } else if (wasInvoiceCreatedOrCommitted && invoiceModelDao.isParentInvoice()) {
                        // Commit queue
                        notifyOfParentInvoiceCreation(entitySqlDaoWrapperFactory, invoiceModelDao, context);
                    }

                    // We always add the future notifications when the callbackDateTimePerSubscriptions is not empty (incl. DRAFT invoices containing RECURRING items created using AUTO_INVOICING_DRAFT feature)
                    notifyOfFutureBillingEvents(entitySqlDaoWrapperFactory, invoiceModelDao.getAccountId(), callbackDateTimePerSubscriptions, context);
                }

                for (final UUID adjustedInvoiceId : allInvoiceIds) {
                    final boolean newInvoice = createdInvoiceIds.contains(adjustedInvoiceId);
                    if (newInvoice) {
                        // New invoice, so no associated payment yet: no need to refresh the invoice state
                        cbaDao.doCBAComplexityFromTransaction(invoiceByInvoiceId.get(adjustedInvoiceId), invoicesTags, entitySqlDaoWrapperFactory, context);
                    } else {
                        // Existing invoice (e.g. we're processing an adjustment): refresh the invoice state to get the correct balance
                        // Should we maybe enforce callers (e.g. InvoiceApiHelper) to properly populate these invoices?
                        cbaDao.doCBAComplexityFromTransaction(adjustedInvoiceId, invoicesTags, entitySqlDaoWrapperFactory, context);
                    }

                    if (adjustedCommittedInvoiceIds.contains(adjustedInvoiceId)) {
                        // Notify the bus since the balance of the invoice changed (only if the invoice is COMMITTED)
                        notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, adjustedInvoiceId, accountId, context.getUserToken(), context);
                    }
                }
                return createdInvoiceItems;
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesBySubscription(subscriptionId.toString(), context);
                invoiceDaoHelper.populateChildren(invoices, invoicesTags, entitySqlDaoWrapperFactory, context);

                return invoices;
            }
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
        return paginationHelper.getPagination(InvoiceSqlDao.class,
                                              new PaginationIteratorBuilder<InvoiceModelDao, Invoice, InvoiceSqlDao>() {
                                                  @Override
                                                  public Long getCount(final InvoiceSqlDao invoiceSqlDao, final InternalTenantContext context) {
                                                      return invoiceNumber != null ? 1L : invoiceSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<InvoiceModelDao> build(final InvoiceSqlDao invoiceSqlDao, final Long offset, final Long limit, final DefaultPaginationSqlDaoHelper.Ordering ordering, final InternalTenantContext context) {
                                                      try {
                                                          return invoiceNumber != null ?
                                                                 ImmutableList.<InvoiceModelDao>of(getByNumber(invoiceNumber, context)).iterator() :
                                                                 invoiceSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, ordering.toString(), context);
                                                      } catch (final InvoiceApiException ignored) {
                                                          return ImmutableSet.<InvoiceModelDao>of().iterator();
                                                      }
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);

    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                BigDecimal cba = BigDecimal.ZERO;

                BigDecimal accountBalance = BigDecimal.ZERO;
                final List<InvoiceModelDao> invoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(false, invoicesTags, entitySqlDaoWrapperFactory, context);
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
                    accountBalance = cur.isWrittenOff() || hasZeroParentBalance ? BigDecimal.ZERO : accountBalance.add(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(cur));
                    cba = cba.add(InvoiceModelDaoHelper.getCBAAmount(cur));
                }
                return accountBalance.subtract(cba);
            }
        });
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return cbaDao.getAccountCBAFromTransaction(entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public List<InvoiceModelDao> getUnpaidInvoicesByAccountId(final UUID accountId, @Nullable final LocalDate upToDate, final InternalTenantContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceModelDao>>() {
            @Override
            public List<InvoiceModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return invoiceDaoHelper.getUnpaidInvoicesByAccountFromTransaction(accountId, invoicesTags, entitySqlDaoWrapperFactory, upToDate, context);
            }
        });
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class).getInvoiceIdByPaymentId(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getInvoicePayments(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByAccount(final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getByAccountRecordId(context);
            }
        });
    }

    @Override
    public InvoicePaymentModelDao createRefund(final UUID paymentId, final BigDecimal requestedRefundAmount, final boolean isInvoiceAdjusted,
                                               final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final String transactionExternalKey,
                                               final InternalCallContext context) throws InvoiceApiException {

        if (isInvoiceAdjusted && invoiceItemIdsWithNullAmounts.size() == 0) {
            throw new InvoiceApiException(ErrorCode.INVOICE_ITEMS_ADJUSTMENT_MISSING);
        }

        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

                final InvoiceSqlDao transInvoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                final List<InvoicePaymentModelDao> paymentsForId = transactional.getByPaymentId(paymentId.toString(), context);
                final InvoicePaymentModelDao payment = Iterables.tryFind(paymentsForId, new Predicate<InvoicePaymentModelDao>() {
                    @Override
                    public boolean apply(final InvoicePaymentModelDao input) {
                        return input.getType() == InvoicePaymentType.ATTEMPT && input.getSuccess();
                    }
                }).orNull();
                if (payment == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND, paymentId);
                }

                // Retrieve the amounts to adjust, if needed
                final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = invoiceDaoHelper.computeItemAdjustments(payment.getInvoiceId().toString(),
                                                                                                                invoicesTags,
                                                                                                                entitySqlDaoWrapperFactory,
                                                                                                                invoiceItemIdsWithNullAmounts,
                                                                                                                context);

                // Compute the actual amount to refund
                final BigDecimal requestedPositiveAmount = invoiceDaoHelper.computePositiveRefundAmount(payment, requestedRefundAmount, invoiceItemIdsWithAmounts);

                // Before we go further, check if that refund already got inserted -- the payment system keeps a state machine
                // and so this call may be called several time for the same  paymentCookieId (which is really the refundId)
                final InvoicePaymentModelDao existingRefund = transactional.getPaymentForCookieId(transactionExternalKey, context);
                if (existingRefund != null) {
                    return existingRefund;
                }

                final InvoicePaymentModelDao refund = new InvoicePaymentModelDao(UUIDs.randomUUID(), context.getCreatedDate(), InvoicePaymentType.REFUND,
                                                                                 payment.getInvoiceId(), paymentId,
                                                                                 context.getCreatedDate(), requestedPositiveAmount.negate(),
                                                                                 payment.getCurrency(), payment.getProcessedCurrency(), transactionExternalKey, payment.getId(), true);
                createAndRefresh(transactional, refund, context);

                // Retrieve invoice after the Refund
                final InvoiceModelDao invoice = transInvoiceDao.getById(payment.getInvoiceId().toString(), context);
                Preconditions.checkState(invoice != null, "Invoice shouldn't be null for payment " + payment.getId());
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);

                final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

                // At this point, we created the refund which made the invoice balance positive and applied any existing
                // available CBA to that invoice.
                // We now need to adjust the invoice and/or invoice items if needed and specified.
                if (isInvoiceAdjusted) {
                    // Invoice item adjustment
                    for (final UUID invoiceItemId : invoiceItemIdsWithAmounts.keySet()) {
                        final BigDecimal adjAmount = invoiceItemIdsWithAmounts.get(invoiceItemId);
                        final InvoiceItemModelDao item = invoiceDaoHelper.createAdjustmentItem(entitySqlDaoWrapperFactory, invoice.getId(), invoiceItemId, adjAmount,
                                                                                               invoice.getCurrency(), context.getCreatedDate().toLocalDate(),
                                                                                               context);

                        createInvoiceItemFromTransaction(transInvoiceItemDao, item, context);
                        invoice.addInvoiceItem(item);
                    }
                }

                // The invoice object has been kept up-to-date
                cbaDao.doCBAComplexityFromTransaction(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);

                if (isInvoiceAdjusted) {
                    notifyBusOfInvoiceAdjustment(entitySqlDaoWrapperFactory, invoice.getId(), invoice.getAccountId(), context.getUserToken(), context);
                }
                notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, refund, invoice.getAccountId(), context.getUserToken(), context);

                return refund;
            }
        });
    }

    @Override
    public InvoicePaymentModelDao postChargeback(final UUID paymentId, final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

                final List<InvoicePaymentModelDao> invoicePayments = transactional.getByPaymentId(paymentId.toString(), context);
                final InvoicePaymentModelDao invoicePayment = Iterables.tryFind(invoicePayments, new Predicate<InvoicePaymentModelDao>() {
                    @Override
                    public boolean apply(final InvoicePaymentModelDao input) {
                        return input.getType() == InvoicePaymentType.ATTEMPT;
                    }
                }).orNull();
                if (invoicePayment == null) {
                    throw new InvoiceApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
                }
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
                                                                                     chargebackTransactionExternalKey, payment.getId(), true);
                createAndRefresh(transactional, chargeBack, context);

                // Notify the bus since the balance of the invoice changed
                final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargeBack.getId().toString(), context);

                cbaDao.doCBAComplexityFromTransaction(payment.getInvoiceId(), invoicesTags, entitySqlDaoWrapperFactory, context);

                notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, chargeBack, accountId, context.getUserToken(), context);

                return chargeBack;
            }
        });
    }

    @Override
    public InvoicePaymentModelDao postChargebackReversal(final UUID paymentId, final String chargebackTransactionExternalKey, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);

                final InvoicePaymentModelDao invoicePayment = transactional.getPaymentForCookieId(chargebackTransactionExternalKey, context);
                if (invoicePayment == null) {
                    throw new InvoiceApiException(ErrorCode.PAYMENT_NO_SUCH_PAYMENT, paymentId);
                }

                transactional.updateAttempt(invoicePayment.getRecordId(),
                                            invoicePayment.getPaymentId().toString(),
                                            invoicePayment.getPaymentDate().toDate(),
                                            invoicePayment.getAmount(),
                                            invoicePayment.getCurrency(),
                                            invoicePayment.getProcessedCurrency(),
                                            invoicePayment.getPaymentCookieId(),
                                            invoicePayment.getLinkedInvoicePaymentId() == null ? null : invoicePayment.getLinkedInvoicePaymentId().toString(),
                                            false,
                                            context);
                final InvoicePaymentModelDao chargebackReversed = transactional.getByRecordId(invoicePayment.getRecordId(), context);

                // Notify the bus since the balance of the invoice changed
                final UUID accountId = transactional.getAccountIdFromInvoicePaymentId(chargebackReversed.getId().toString(), context);

                cbaDao.doCBAComplexityFromTransaction(chargebackReversed.getInvoiceId(), invoicesTags, entitySqlDaoWrapperFactory, context);

                notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, chargebackReversed, accountId, context.getUserToken(), context);

                return chargebackReversed;
            }
        });
    }

    @Override
    public InvoiceItemModelDao doCBAComplexity(final InvoiceModelDao invoice, final InternalCallContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemModelDao cbaNewItem = cbaDao.computeCBAComplexity(invoice, null, entitySqlDaoWrapperFactory, context);
                return cbaNewItem;
            }
        });
    }

    @Override
    public Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId, final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final InternalTenantContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<Map<UUID, BigDecimal>>() {
            @Override
            public Map<UUID, BigDecimal> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return invoiceDaoHelper.computeItemAdjustments(invoiceId, invoicesTags, entitySqlDaoWrapperFactory, invoiceItemIdsWithNullAmounts, context);
            }
        });
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<BigDecimal>() {
            @Override
            public BigDecimal inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return invoiceDaoHelper.getRemainingAmountPaidFromTransaction(invoicePaymentId, entitySqlDaoWrapperFactory, context);
            }
        });
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final UUID accountId = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getAccountIdFromInvoicePaymentId(invoicePaymentId.toString(), context);
                if (accountId == null) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_COULD_NOT_FIND_ACCOUNT_ID, invoicePaymentId);
                } else {
                    return accountId;
                }
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getChargeBacksByAccountId(accountId.toString(), context);
            }
        });
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoicePaymentModelDao>>() {
            @Override
            public List<InvoicePaymentModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getChargebacksByPaymentId(paymentId.toString(), context);
            }
        });
    }

    @Override
    public InvoicePaymentModelDao getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoicePaymentModelDao>() {
            @Override
            public InvoicePaymentModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentModelDao chargeback = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class).getById(chargebackId.toString(), context);
                if (chargeback == null) {
                    throw new InvoiceApiException(ErrorCode.CHARGE_BACK_DOES_NOT_EXIST, chargebackId);
                } else {
                    return chargeback;
                }
            }
        });
    }

    @Override
    public InvoiceItemModelDao getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                final InvoiceItemModelDao invoiceItemModelDao = invoiceItemSqlDao.getById(externalChargeId.toString(), context);
                if (invoiceItemModelDao == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, externalChargeId.toString());
                }
                return invoiceItemModelDao;
            }
        });
    }

    @Override
    public void notifyOfPaymentInit(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        notifyOfPaymentCompletionInternal(invoicePayment, false, context);
    }

    @Override
    public void notifyOfPaymentCompletion(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        notifyOfPaymentCompletionInternal(invoicePayment, true, context);
    }

    private void notifyOfPaymentCompletionInternal(final InvoicePaymentModelDao invoicePayment, final boolean completion, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoicePaymentSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoicePaymentSqlDao.class);
                //
                // In case of notifyOfPaymentInit we always want to record the row with success = false
                // Otherwise, if the payment id is null, the payment wasn't attempted (e.g. no payment method so we don't record an attempt but send
                // an event nonetheless (e.g. for Overdue)
                //
                if (!completion || invoicePayment.getPaymentId() != null) {
                    //
                    // extract entries by invoiceId (which is always set, as opposed to paymentId) and then filter based on type and
                    // paymentCookieId = transactionExternalKey
                    //
                    final List<InvoicePaymentModelDao> invoicePayments = transactional.getAllPaymentsForInvoiceIncludedInit(invoicePayment.getInvoiceId().toString(), context);
                    final InvoicePaymentModelDao existingAttempt = Iterables.tryFind(invoicePayments, new Predicate<InvoicePaymentModelDao>() {
                        @Override
                        public boolean apply(final InvoicePaymentModelDao input) {
                            return input.getType() == InvoicePaymentType.ATTEMPT &&
                                   input.getPaymentCookieId().equals(invoicePayment.getPaymentCookieId());
                        }
                    }).orNull();

                    if (existingAttempt == null) {
                        createAndRefresh(transactional, invoicePayment, context);
                    } else {
                        transactional.updateAttempt(existingAttempt.getRecordId(),
                                                    invoicePayment.getPaymentId().toString(),
                                                    invoicePayment.getPaymentDate().toDate(),
                                                    invoicePayment.getAmount(),
                                                    invoicePayment.getCurrency(),
                                                    invoicePayment.getProcessedCurrency(),
                                                    invoicePayment.getPaymentCookieId(),
                                                    null,
                                                    invoicePayment.getSuccess(),
                                                    context);
                    }
                }

                if (completion) {
                    final UUID accountId = nonEntityDao.retrieveIdFromObjectInTransaction(context.getAccountRecordId(), ObjectType.ACCOUNT, objectIdCacheController, entitySqlDaoWrapperFactory.getHandle());
                    notifyBusOfInvoicePayment(entitySqlDaoWrapperFactory, invoicePayment, accountId, context.getUserToken(), context);
                }
                return null;
            }
        });
    }

    @Override
    public InvoiceItemModelDao getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceItemModelDao>() {
            @Override
            public InvoiceItemModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                final InvoiceItemModelDao invoiceItemModelDao = invoiceItemSqlDao.getById(creditId.toString(), context);
                if (invoiceItemModelDao == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, creditId.toString());
                }
                return invoiceItemModelDao;
            }
        });
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                // Retrieve the invoice and make sure it belongs to the right account
                final InvoiceModelDao invoice = transactional.getById(invoiceId.toString(), context);
                if (invoice == null ||
                    !invoice.getAccountId().equals(accountId) ||
                    invoice.isMigrated() ||
                    invoice.getStatus() == InvoiceStatus.DRAFT) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }

                // Retrieve the invoice item and make sure it belongs to the right invoice
                final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                final InvoiceItemModelDao cbaItem = invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
                if (cbaItem == null || !cbaItem.getInvoiceId().equals(invoice.getId())) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
                }

                // First, adjust the same invoice with the CBA amount to "delete"
                final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ, invoice.getId(), invoice.getAccountId(),
                                                                               null, null, null, null, null, null, null, context.getCreatedDate().toLocalDate(),
                                                                               null, cbaItem.getAmount().negate(), null, cbaItem.getCurrency(), cbaItem.getId());
                createInvoiceItemFromTransaction(invoiceItemSqlDao, cbaAdjItem, context);

                // Verify the final invoice balance is not negative
                invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
                if (InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).compareTo(BigDecimal.ZERO) < 0) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_WOULD_BE_NEGATIVE);
                }

                // If there is more account credit than CBA we adjusted, we're done.
                // Otherwise, we need to find further invoices on which this credit was consumed
                final BigDecimal accountCBA = cbaDao.getAccountCBAFromTransaction(entitySqlDaoWrapperFactory, context);
                if (accountCBA.compareTo(BigDecimal.ZERO) < 0) {
                    if (accountCBA.compareTo(cbaItem.getAmount().negate()) < 0) {
                        throw new IllegalStateException("The account balance can't be lower than the amount adjusted");
                    }
                    final List<InvoiceModelDao> invoicesFollowing = getAllNonMigratedInvoicesByAccountAfterDate(false, transactional, invoice.getInvoiceDate(), context);
                    invoiceDaoHelper.populateChildren(invoicesFollowing, invoicesTags, entitySqlDaoWrapperFactory, context);

                    // The remaining amount to adjust (i.e. the amount of credits used on following invoices)
                    // is the current account CBA balance (minus the sign)
                    BigDecimal positiveRemainderToAdjust = accountCBA.negate();
                    for (final InvoiceModelDao invoiceFollowing : invoicesFollowing) {
                        if (invoiceFollowing.getId().equals(invoice.getId())) {
                            continue;
                        }

                        // Add a single adjustment per invoice
                        BigDecimal positiveCBAAdjItemAmount = BigDecimal.ZERO;

                        for (final InvoiceItemModelDao cbaUsed : invoiceFollowing.getInvoiceItems()) {
                            // Ignore non CBA items or credits (CBA >= 0)
                            if (!InvoiceItemType.CBA_ADJ.equals(cbaUsed.getType()) ||
                                cbaUsed.getAmount().compareTo(BigDecimal.ZERO) >= 0) {
                                continue;
                            }

                            final BigDecimal positiveCBAUsedAmount = cbaUsed.getAmount().negate();
                            final BigDecimal positiveNextCBAAdjItemAmount;
                            if (positiveCBAUsedAmount.compareTo(positiveRemainderToAdjust) < 0) {
                                positiveNextCBAAdjItemAmount = positiveCBAUsedAmount;
                                positiveRemainderToAdjust = positiveRemainderToAdjust.subtract(positiveNextCBAAdjItemAmount);
                            } else {
                                positiveNextCBAAdjItemAmount = positiveRemainderToAdjust;
                                positiveRemainderToAdjust = BigDecimal.ZERO;
                            }
                            positiveCBAAdjItemAmount = positiveCBAAdjItemAmount.add(positiveNextCBAAdjItemAmount);

                            if (positiveRemainderToAdjust.compareTo(BigDecimal.ZERO) == 0) {
                                break;
                            }
                        }

                        // Add the adjustment on that invoice
                        final InvoiceItemModelDao nextCBAAdjItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.CBA_ADJ, invoiceFollowing.getId(),
                                                                                           invoice.getAccountId(), null, null, null, null, null, null, null,
                                                                                           context.getCreatedDate().toLocalDate(), null,
                                                                                           positiveCBAAdjItemAmount, null, cbaItem.getCurrency(), cbaItem.getId());
                        createInvoiceItemFromTransaction(invoiceItemSqlDao, nextCBAAdjItem, context);
                        if (positiveRemainderToAdjust.compareTo(BigDecimal.ZERO) == 0) {
                            break;
                        }
                    }
                }

                return null;
            }
        });
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                cbaDao.doCBAComplexityFromTransaction(invoicesTags, entitySqlDaoWrapperFactory, context);
                return null;
            }
        });
    }

    private void notifyOfFutureBillingEvents(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                             final UUID accountId,
                                             final FutureAccountNotifications callbackDateTimePerSubscriptions,
                                             final InternalCallContext internalCallContext) {
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
                    nextBillingDatePoster.insertNextBillingDryRunNotificationFromTransaction(entitySqlDaoWrapperFactory, accountId, subscriptionIds, notificationDateTime, notificationDateTime.plusMillis((int) dryRunNotificationTime), internalCallContext);
                }
            }
        }
    }

    private void notifyBusOfInvoiceAdjustment(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final UUID invoiceId, final UUID accountId,
                                              final UUID userToken, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultInvoiceAdjustmentEvent(invoiceId, accountId, context.getAccountRecordId(), context.getTenantRecordId(), userToken),
                                         entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.warn("Failed to post adjustment event for invoiceId='{}'", invoiceId, e);
        }
    }

    private void notifyBusOfInvoicePayment(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InvoicePaymentModelDao invoicePaymentModelDao,
                                           final UUID accountId, final UUID userToken, final InternalCallContext context) {
        final BusEvent busEvent;
        if (invoicePaymentModelDao.getSuccess() == Boolean.TRUE) {
            busEvent = new DefaultInvoicePaymentInfoEvent(accountId,
                                                          invoicePaymentModelDao.getPaymentId(),
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

    private InvoiceItemModelDao createInvoiceItemFromTransaction(final InvoiceItemSqlDao invoiceItemSqlDao, final InvoiceItemModelDao invoiceItemModelDao, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        // There is no efficient way to retrieve an invoice item given an ID today (and invoice plugins can put item adjustments
        // on a different invoice than the original item), so it's easier to do the check in the DAO rather than in the API layer
        // See also https://github.com/killbill/killbill/issues/7
        if (InvoiceItemType.ITEM_ADJ.equals(invoiceItemModelDao.getType())) {
            validateInvoiceItemToBeAdjusted(invoiceItemSqlDao, invoiceItemModelDao, context);
        }

        return createAndRefresh(invoiceItemSqlDao, invoiceItemModelDao, context);
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
    public void changeInvoiceStatus(final UUID invoiceId, final InvoiceStatus newStatus,
                                    final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);

                // Retrieve the invoice and make sure it belongs to the right account
                final InvoiceModelDao invoice = transactional.getById(invoiceId.toString(), context);

                if (invoice == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_NOT_FOUND, invoiceId);
                }

                if (invoice.getStatus().equals(newStatus) || invoice.getStatus().equals(InvoiceStatus.VOID)) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_INVALID_STATUS, newStatus, invoiceId, invoice.getStatus());
                }

                transactional.updateStatus(invoiceId.toString(), newStatus.toString(), context);

                cbaDao.doCBAComplexityFromTransaction(invoicesTags, entitySqlDaoWrapperFactory, context);

                if (InvoiceStatus.COMMITTED.equals(newStatus)) {
                    // notify invoice creation event
                    notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, invoice, context);
                }

                return null;
            }
        });
    }

    private void notifyBusOfInvoiceCreation(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InvoiceModelDao invoice, final InternalCallContext context) {
        try {
            // This is called for a new COMMITTED invoice (which cannot be writtenOff as it does not exist yet, so rawBalance == balance)
            final BigDecimal rawBalance = InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice);
            final DefaultInvoiceCreationEvent event = new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                                                      rawBalance, invoice.getCurrency(),
                                                                                      context.getAccountRecordId(), context.getTenantRecordId(),
                                                                                      context.getUserToken());
            eventBus.postFromTransaction(event, entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.error(String.format("Failed to post invoice creation event %s for account %s", invoice.getAccountId()), e);
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
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceParentChildrenSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
                createAndRefresh(transactional, invoiceRelation, context);
                return null;
            }
        });
    }

    @Override
    public List<InvoiceParentChildModelDao> getChildInvoicesByParentInvoiceId(final UUID parentInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceParentChildModelDao>>() {
            @Override
            public List<InvoiceParentChildModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceParentChildrenSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceParentChildrenSqlDao.class);
                return transactional.getChildInvoicesByParentInvoiceId(parentInvoiceId.toString(), context);
            }
        });
    }

    @Override
    public InvoiceModelDao getParentDraftInvoice(final UUID parentAccountId, final InternalCallContext context) throws InvoiceApiException {
        final List<Tag> invoicesTags = getInvoicesTags(context);

        return transactionalSqlDao.execute(true, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<InvoiceModelDao>() {
            @Override
            public InvoiceModelDao inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceSqlDao invoiceSqlDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
                InvoiceModelDao invoice = invoiceSqlDao.getParentDraftInvoice(parentAccountId.toString(), context);
                if (invoice != null) {
                    invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
                }
                return invoice;
            }
        });
    }

    @Override
    public void updateInvoiceItemAmount(final UUID invoiceItemId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {
        transactionalSqlDao.execute(false, InvoiceApiException.class, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);

                // Retrieve the invoice and make sure it belongs to the right account
                final InvoiceItemModelDao invoiceItem = transactional.getById(invoiceItemId.toString(), context);

                if (invoiceItem == null) {
                    throw new InvoiceApiException(ErrorCode.INVOICE_ITEM_NOT_FOUND, invoiceItemId);
                }

                transactional.updateAmount(invoiceItemId.toString(), amount, context);
                return null;
            }
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

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
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

                // add CBA complexity and notify bus on child invoice creation
                cbaDao.doCBAComplexityFromTransaction(childInvoice, childInvoicesTags, entitySqlDaoWrapperFactory, childAccountContext);
                notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, childInvoice, childAccountContext);

                cbaDao.doCBAComplexityFromTransaction(parentInvoice, parentInvoicesTags, entitySqlDaoWrapperFactory, parentAccountContext);
                notifyBusOfInvoiceCreation(entitySqlDaoWrapperFactory, parentInvoice, parentAccountContext);

                return null;
            }
        });
    }

    @Override
    public List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(final UUID parentInvoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<InvoiceItemModelDao>>() {
            @Override
            public List<InvoiceItemModelDao> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final InvoiceItemSqlDao transactional = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
                return transactional.getInvoiceItemsByParentInvoice(parentInvoiceId.toString(), context);
            }
        });
    }

    // PERF: fetch tags once. See also https://github.com/killbill/killbill/issues/720.
    private List<Tag> getInvoicesTags(final InternalTenantContext context) {
        return tagInternalApi.getTagsForAccountType(ObjectType.INVOICE, false, context);
    }

    private static void checkAgainstExistingInvoiceItemState(final InvoiceItemModelDao existingInvoiceItem, final InvoiceItemModelDao inputInvoiceItem) {
        Preconditions.checkState(existingInvoiceItem.getAccountId().equals(inputInvoiceItem.getAccountId()), String.format("Unexpected account ID '%s' for invoice item '%s'",
                                                                                                                           inputInvoiceItem.getAccountId(), existingInvoiceItem.getId()));
        if (existingInvoiceItem.getChildAccountId() != null) {
            Preconditions.checkState(existingInvoiceItem.getChildAccountId().equals(inputInvoiceItem.getChildAccountId()), String.format("Unexpected child account ID '%s' for invoice item '%s'",
                                                                                                                                         inputInvoiceItem.getChildAccountId(), existingInvoiceItem.getId()));
        }
        Preconditions.checkState(existingInvoiceItem.getInvoiceId().equals(inputInvoiceItem.getInvoiceId()), String.format("Unexpected invoice ID '%s' for invoice item '%s'",
                                                                                                                           inputInvoiceItem.getInvoiceId(), existingInvoiceItem.getId()));
        if (existingInvoiceItem.getBundleId() != null) {
            Preconditions.checkState(existingInvoiceItem.getBundleId().equals(inputInvoiceItem.getBundleId()), String.format("Unexpected bundle ID '%s' for invoice item '%s'",
                                                                                                                             inputInvoiceItem.getBundleId(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getSubscriptionId() != null) {
            Preconditions.checkState(existingInvoiceItem.getSubscriptionId().equals(inputInvoiceItem.getSubscriptionId()), String.format("Unexpected subscription ID '%s' for invoice item '%s'",
                                                                                                                                         inputInvoiceItem.getSubscriptionId(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getPlanName() != null) {
            Preconditions.checkState(existingInvoiceItem.getPlanName().equals(inputInvoiceItem.getPlanName()), String.format("Unexpected plan name '%s' for invoice item '%s'",
                                                                                                                             inputInvoiceItem.getPlanName(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getPhaseName() != null) {
            Preconditions.checkState(existingInvoiceItem.getPhaseName().equals(inputInvoiceItem.getPhaseName()), String.format("Unexpected phase name '%s' for invoice item '%s'",
                                                                                                                               inputInvoiceItem.getPhaseName(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getUsageName() != null) {
            Preconditions.checkState(existingInvoiceItem.getUsageName().equals(inputInvoiceItem.getUsageName()), String.format("Unexpected usage name '%s' for invoice item '%s'",
                                                                                                                               inputInvoiceItem.getUsageName(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getStartDate() != null) {
            Preconditions.checkState(existingInvoiceItem.getStartDate().equals(inputInvoiceItem.getStartDate()), String.format("Unexpected startDate '%s' for invoice item '%s'",
                                                                                                                               inputInvoiceItem.getStartDate(), existingInvoiceItem.getId()));
        }
        if (existingInvoiceItem.getEndDate() != null) {
            Preconditions.checkState(existingInvoiceItem.getEndDate().equals(inputInvoiceItem.getEndDate()), String.format("Unexpected endDate '%s' for invoice item '%s'",
                                                                                                                           inputInvoiceItem.getEndDate(), existingInvoiceItem.getId()));
        }

        Preconditions.checkState(existingInvoiceItem.getCurrency() == inputInvoiceItem.getCurrency(), String.format("Unexpected currency '%s' for invoice item '%s'",
                                                                                                                    inputInvoiceItem.getCurrency(), existingInvoiceItem.getId()));
        Preconditions.checkState(existingInvoiceItem.getType() == inputInvoiceItem.getType(), String.format("Unexpected item type '%s' for invoice item '%s'",
                                                                                                            inputInvoiceItem.getType(), existingInvoiceItem.getId()));
    }

}
