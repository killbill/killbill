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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;
import org.killbill.bus.api.PersistentBus;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;

public class MockInvoiceDao extends MockEntityDaoBase<InvoiceModelDao, Invoice, InvoiceApiException> implements InvoiceDao {

    private final PersistentBus eventBus;
    private final Object monitor = new Object();
    private final Map<UUID, InvoiceModelDao> invoices = new LinkedHashMap<UUID, InvoiceModelDao>();
    private final Map<UUID, InvoiceItemModelDao> items = new LinkedHashMap<UUID, InvoiceItemModelDao>();
    private final Map<UUID, InvoicePaymentModelDao> payments = new LinkedHashMap<UUID, InvoicePaymentModelDao>();
    private final BiMap<UUID, Long> accountRecordIds = HashBiMap.create();

    @Inject
    public MockInvoiceDao(final PersistentBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void createInvoice(final InvoiceModelDao invoice,
                              final FutureAccountNotifications callbackDateTimePerSubscriptions, final InternalCallContext context) {
        synchronized (monitor) {
            storeInvoice(invoice, context);
        }
        try {
            eventBus.post(new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                          InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice), invoice.getCurrency(),
                                                          context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()));
        } catch (final PersistentBus.EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void setFutureAccountNotificationsForEmptyInvoice(final UUID accountId, final FutureAccountNotifications callbackDateTimePerSubscriptions, final InternalCallContext context) {

    }

    @Override
    public List<InvoiceItemModelDao> createInvoices(final List<InvoiceModelDao> invoiceModelDaos, final InternalCallContext context) {
        synchronized (monitor) {
            final List<InvoiceItemModelDao> createdItems = new LinkedList<InvoiceItemModelDao>();
            for (final InvoiceModelDao invoice : invoiceModelDaos) {
                createdItems.addAll(storeInvoice(invoice, context));
            }
            return createdItems;
        }
    }

    private Collection<InvoiceItemModelDao> storeInvoice(final InvoiceModelDao invoice, final InternalCallContext context) {
        final Collection<InvoiceItemModelDao> createdItems = new LinkedList<InvoiceItemModelDao>();

        invoices.put(invoice.getId(), invoice);
        for (final InvoiceItemModelDao invoiceItemModelDao : invoice.getInvoiceItems()) {
            final InvoiceItemModelDao oldItemOrNull = items.put(invoiceItemModelDao.getId(), invoiceItemModelDao);
            if (oldItemOrNull == null) {
                createdItems.add(invoiceItemModelDao);
            }
        }
        accountRecordIds.put(invoice.getAccountId(), context.getAccountRecordId());

        return createdItems;
    }

    @Override
    public InvoiceModelDao getById(final UUID id, final InternalTenantContext context) {
        synchronized (monitor) {
            return invoices.get(id);
        }
    }

    @Override
    public InvoiceModelDao getByNumber(final Integer number, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (invoice.getInvoiceNumber().equals(number)) {
                    return invoice;
                }
            }
        }

        return null;
    }

    @Override
    public InvoiceModelDao getByInvoiceItem(final UUID invoiceItemId, final InternalTenantContext context) throws InvoiceApiException {
        final InvoiceItemModelDao item = items.get(invoiceItemId);
        return (item != null) ? invoices.get(item.getInvoiceId()) : null;
    }

    @Override
    public Pagination<InvoiceModelDao> getAll(final InternalTenantContext context) {
        synchronized (monitor) {
            return new DefaultPagination<InvoiceModelDao>((long) invoices.values().size(), invoices.values().iterator());
        }
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.isMigrated()) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final Boolean includeVoidedInvoices, final LocalDate fromDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoicesForAccount = new ArrayList<InvoiceModelDao>();
        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : getAll(context)) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.getTargetDate().isBefore(fromDate) && !invoice.isMigrated() &&
                    (includeVoidedInvoices ? true : !InvoiceStatus.VOID.equals(invoice.getStatus()))) {
                    invoicesForAccount.add(invoice);
                }
            }
        }

        return invoicesForAccount;
    }

    @Override
    public List<InvoiceModelDao> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            for (final InvoiceModelDao invoice : invoices.values()) {
                for (final InvoiceItemModelDao item : invoice.getInvoiceItems()) {
                    if (subscriptionId.equals(item.getSubscriptionId()) && !invoice.isMigrated()) {
                        result.add(invoice);
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Pagination<InvoiceModelDao> searchInvoices(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        final List<InvoiceModelDao> results = new LinkedList<InvoiceModelDao>();
        int maxNbRecords = 0;
        for (final InvoiceModelDao invoice : getAll(context)) {
            maxNbRecords++;
            if (invoice.getId().toString().equals(searchKey) ||
                invoice.getAccountId().toString().equals(searchKey) ||
                invoice.getInvoiceNumber().toString().equals(searchKey) ||
                invoice.getCurrency().toString().equals(searchKey)) {
                results.add(invoice);
            }
        }

        return DefaultPagination.<InvoiceModelDao>build(offset, limit, maxNbRecords, results);
    }

    @Override
    public void test(final InternalTenantContext context) {
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final InvoicePaymentModelDao payment : payments.values()) {
                if (paymentId.equals(payment.getPaymentId())) {
                    return payment.getInvoiceId();
                }
            }
        }
        return null;
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        final List<InvoicePaymentModelDao> result = new LinkedList<InvoicePaymentModelDao>();
        synchronized (monitor) {
            for (final InvoicePaymentModelDao payment : payments.values()) {
                if (paymentId.equals(payment.getPaymentId())) {
                    result.add(payment);
                }
            }
        }
        return result;
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByInvoice(final UUID invoiceId, final InternalTenantContext context) {
        final List<InvoicePaymentModelDao> result = new LinkedList<InvoicePaymentModelDao>();
        synchronized (monitor) {
            for (final InvoicePaymentModelDao payment : payments.values()) {
                if (invoiceId.equals(payment.getInvoiceId())) {
                    result.add(payment);
                }
            }
        }
        return result;
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePaymentsByAccount(final InternalTenantContext context) {

        throw new UnsupportedOperationException();
/*
        InvoicePaymentModelDao does not export accountId ?

        final List<InvoicePaymentModelDao> invoicesForAccount = new ArrayList<InvoicePaymentModelDao>();
        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoicePaymentModelDao payment : payments.values()) {
            }
        }
        return null;
*/
    }

    @Override
    public InvoicePaymentModelDao getInvoicePaymentByCookieId(final String cookieId, final InternalTenantContext internalTenantContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void notifyOfPaymentCompletion(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        synchronized (monitor) {
            payments.put(invoicePayment.getId(), invoicePayment);
        }
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) {
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        BigDecimal balance = BigDecimal.ZERO;

        for (final InvoiceModelDao invoice : getAll(context)) {
            if (accountId.equals(invoice.getAccountId())) {
                balance = balance.add(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice));
            }
        }

        return balance;
    }

    @Override
    public List<InvoiceModelDao> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> unpaidInvoices = new ArrayList<InvoiceModelDao>();

        for (final InvoiceModelDao invoice : getAll(context)) {
            if (accountId.equals(invoice.getAccountId()) && (InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).compareTo(BigDecimal.ZERO) > 0) && !invoice.isMigrated()) {
                unpaidInvoices.add(invoice);
            }
        }

        return unpaidInvoices;
    }

    @Override
    public List<InvoiceModelDao> getAllInvoicesByAccount(final Boolean includeVoidedInvoices, final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId()) && (includeVoidedInvoices ? true : !InvoiceStatus.VOID.equals(invoice.getStatus()))) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public InvoicePaymentModelDao postChargeback(final UUID invoicePaymentId, final String chargebackTransactionExternalKey, final BigDecimal amount, final Currency currency, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePaymentModelDao postChargebackReversal(final UUID paymentId, final String chargebackTransactionExternalKey, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao doCBAComplexity(final InvoiceModelDao invoice, final InternalCallContext context) throws InvoiceApiException {
        // Do nothing unless we need it..
        return null;
    }

    @Override
    public Map<UUID, BigDecimal> computeItemAdjustments(final String invoiceId, final Map<UUID, BigDecimal> invoiceItemIdsWithNullAmounts, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePaymentModelDao getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public InvoicePaymentModelDao createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted,
                                               final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final String transactionExternalKey,
                                               final InternalCallContext context)
            throws InvoiceApiException {
        return null;
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void notifyOfPaymentInit(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        synchronized (monitor) {
            payments.put(invoicePayment.getId(), invoicePayment);
        }

    }

    @Override
    public void changeInvoiceStatus(final UUID invoiceId, final InvoiceStatus newState, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createParentChildInvoiceRelation(final InvoiceParentChildModelDao invoiceRelation, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceModelDao getParentDraftInvoice(final UUID parentAccountId, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoiceParentChildModelDao> getChildInvoicesByParentInvoiceId(final UUID parentInvoiceId, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    public void updateInvoiceItemAmount(final UUID invoiceItemId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transferChildCreditToParent(final Account childAccount, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoiceItemModelDao> getInvoiceItemsByParentInvoice(final UUID parentInvoiceId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }
}
