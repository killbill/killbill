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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.osgi.service.log.LogService;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogUserApi;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForAccount;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillAPI;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillLogService;

public abstract class BusinessAnalyticsBase {

    protected final OSGIKillbillLogService logService;
    protected final OSGIKillbillAPI osgiKillbillAPI;

    public BusinessAnalyticsBase(final OSGIKillbillLogService logService, final OSGIKillbillAPI osgiKillbillAPI) {
        this.logService = logService;
        this.osgiKillbillAPI = osgiKillbillAPI;
    }

    //
    // ACCOUNT
    //

    protected Account getAccount(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final AccountUserApi accountUserApi = getAccountUserApi();

        try {
            return accountUserApi.getAccountById(accountId, context);
        } catch (AccountApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving account for id " + accountId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected AuditLog getAccountCreationAuditLog(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final AuditLogsForAccount auditLogsForAccount = getAuditUserApi().getAuditLogsForAccount(accountId, AuditLevel.MINIMAL, context);
        for (final AuditLog auditLog : auditLogsForAccount.getAccountAuditLogs()) {
            if (auditLog.getChangeType().equals(ChangeType.INSERT)) {
                return auditLog;
            }
        }

        throw new AnalyticsRefreshException("Unable to find Account creation audit log for id " + accountId);
    }

    //
    // SUBSCRIPTION
    //

    protected SubscriptionBundle getSubscriptionBundle(final UUID bundleId, final TenantContext context) throws AnalyticsRefreshException {
        final EntitlementUserApi entitlementUserApi = getEntitlementUserApi();

        try {
            return entitlementUserApi.getBundleFromId(bundleId, context);
        } catch (EntitlementUserApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving bundle for id " + bundleId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected Collection<Subscription> getSubscriptionsForBundle(final UUID bundleId, final TenantContext context) throws AnalyticsRefreshException {
        final EntitlementUserApi entitlementUserApi = getEntitlementUserApi();
        return entitlementUserApi.getSubscriptionsForBundle(bundleId, context);
    }

    protected List<SubscriptionBundle> getSubscriptionBundlesForAccount(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final EntitlementUserApi entitlementUserApi = getEntitlementUserApi();
        return entitlementUserApi.getBundlesForAccount(accountId, context);
    }

    protected Subscription getSubscription(final UUID subscriptionId, final TenantContext context) throws AnalyticsRefreshException {
        final EntitlementUserApi entitlementUserApi = getEntitlementUserApi();

        try {
            return entitlementUserApi.getSubscriptionFromId(subscriptionId, context);
        } catch (EntitlementUserApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving subscription for id " + subscriptionId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    //
    // INVOICE
    //

    protected Invoice getInvoice(final UUID invoiceId, final TenantContext context) throws AnalyticsRefreshException {
        final InvoiceUserApi invoiceUserApi = getInvoiceUserApi();
        try {
            return invoiceUserApi.getInvoice(invoiceId, context);
        } catch (InvoiceApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving subscription for id " + invoiceId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected AuditLog getInvoiceCreationAuditLog(final UUID invoiceId, final TenantContext context) throws AnalyticsRefreshException {
        final List<AuditLog> auditLogsForInvoice = getAuditUserApi().getAuditLogs(invoiceId, ObjectType.INVOICE, AuditLevel.MINIMAL, context);
        for (final AuditLog auditLog : auditLogsForInvoice) {
            if (auditLog.getChangeType().equals(ChangeType.INSERT)) {
                return auditLog;
            }
        }

        throw new AnalyticsRefreshException("Unable to find Invoice creation audit log for id " + invoiceId);
    }

    protected AuditLog getInvoiceItemCreationAuditLog(final UUID invoiceItemId, final TenantContext context) throws AnalyticsRefreshException {
        final List<AuditLog> auditLogsForInvoiceItem = getAuditUserApi().getAuditLogs(invoiceItemId, ObjectType.INVOICE_ITEM, AuditLevel.MINIMAL, context);
        for (final AuditLog auditLog : auditLogsForInvoiceItem) {
            if (auditLog.getChangeType().equals(ChangeType.INSERT)) {
                return auditLog;
            }
        }

        throw new AnalyticsRefreshException("Unable to find Invoice item creation audit log for id " + invoiceItemId);
    }

    protected Collection<Invoice> getInvoicesByAccountId(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final InvoiceUserApi invoiceUserApi = getInvoiceUserApi();
        return invoiceUserApi.getInvoicesByAccount(accountId, context);
    }

    protected BigDecimal getAccountBalance(final UUID accountId, final CallContext context) throws AnalyticsRefreshException {
        final InvoiceUserApi invoiceUserApi = getInvoiceUserApi();
        return invoiceUserApi.getAccountBalance(accountId, context);
    }

    protected Plan getPlanFromInvoiceItem(final InvoiceItem invoiceItem, final TenantContext context) throws AnalyticsRefreshException {
        try {
            final Catalog catalog = getCatalog(context);
            return catalog.findPlan(invoiceItem.getPlanName(), invoiceItem.getStartDate().toDateTimeAtStartOfDay());
        } catch (CatalogApiException e) {
            logService.log(LogService.LOG_WARNING, "Unable to retrieve plan for invoice item " + invoiceItem.getId(), e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected PlanPhase getPlanPhaseFromInvoiceItem(final InvoiceItem invoiceItem, final TenantContext context) throws AnalyticsRefreshException {
        final Subscription subscription = getSubscription(invoiceItem.getSubscriptionId(), context);

        try {
            final Catalog catalog = getCatalog(context);
            return catalog.findPhase(invoiceItem.getPhaseName(), invoiceItem.getStartDate().toDateTimeAtStartOfDay(), subscription.getStartDate());
        } catch (CatalogApiException e) {
            logService.log(LogService.LOG_WARNING, "Unable to retrieve phase for invoice item " + invoiceItem.getId(), e);
            throw new AnalyticsRefreshException(e);
        }
    }

    //
    // CATALOG
    //

    protected Catalog getCatalog(final TenantContext context) throws AnalyticsRefreshException {
        final CatalogUserApi catalogUserApi = getCatalogUserApi();

        // TODO FIXME
        final String catalogName = "Please, please, fix me!!!";
        return catalogUserApi.getCatalog(catalogName, context);
    }

    //
    // INVOICE PAYMENT
    //

    protected Collection<InvoicePayment> getAccountInvoicePayments(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final InvoicePaymentApi invoicePaymentApi = getInvoicePaymentUserApi();
        final Collection<Payment> payments = getPaymentsByAccountId(accountId, context);

        final Collection<InvoicePayment> invoicePayments = new LinkedList<InvoicePayment>();
        for (final Payment payment : payments) {
            invoicePayments.addAll(invoicePaymentApi.getInvoicePayments(payment.getId(), context));
        }
        return invoicePayments;
    }

    protected AuditLog getInvoicePaymentCreationAuditLog(final UUID invoicePaymentId, final TenantContext context) throws AnalyticsRefreshException {
        final List<AuditLog> auditLogsForInvoicePayment = getAuditUserApi().getAuditLogs(invoicePaymentId, ObjectType.INVOICE_PAYMENT, AuditLevel.MINIMAL, context);
        for (final AuditLog auditLog : auditLogsForInvoicePayment) {
            if (auditLog.getChangeType().equals(ChangeType.INSERT)) {
                return auditLog;
            }
        }

        throw new AnalyticsRefreshException("Unable to find Invoice payment creation audit log for id " + invoicePaymentId);
    }

    //
    // PAYMENT
    //

    protected Collection<Payment> getPaymentsByAccountId(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final PaymentApi paymentApi = getPaymentUserApi();
        try {
            return paymentApi.getAccountPayments(accountId, context);
        } catch (PaymentApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving payments for account id " + accountId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected Payment getPayment(final UUID paymentId, final TenantContext context) throws AnalyticsRefreshException {
        final PaymentApi paymentApi = getPaymentUserApi();

        try {
            return paymentApi.getPayment(paymentId, context);
        } catch (PaymentApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving payment for id " + paymentId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected Collection<PaymentMethod> getAccountPaymentMethods(final UUID accountId, final TenantContext context) throws AnalyticsRefreshException {
        final Account account = getAccount(accountId, context);

        final PaymentApi paymentApi = getPaymentUserApi();
        try {
            return paymentApi.getPaymentMethods(account, true, context);
        } catch (PaymentApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving payment methods for account id " + accountId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    protected PaymentMethod getPaymentMethod(final UUID paymentMethodId, final TenantContext context) throws AnalyticsRefreshException {
        final PaymentApi paymentApi = getPaymentUserApi();

        try {
            return paymentApi.getPaymentMethodById(paymentMethodId, true, context);
        } catch (PaymentApiException e) {
            logService.log(LogService.LOG_WARNING, "Error retrieving payment method for id " + paymentMethodId, e);
            throw new AnalyticsRefreshException(e);
        }
    }

    //
    // APIs
    //

    private AccountUserApi getAccountUserApi() throws AnalyticsRefreshException {
        final AccountUserApi accountUserApi = osgiKillbillAPI.getAccountUserApi();
        if (accountUserApi == null) {
            throw new AnalyticsRefreshException("Error retrieving accountUserApi");
        }
        return accountUserApi;
    }

    private AuditUserApi getAuditUserApi() throws AnalyticsRefreshException {
        final AuditUserApi auditUserApi = osgiKillbillAPI.getAuditUserApi();
        if (auditUserApi == null) {
            throw new AnalyticsRefreshException("Error retrieving auditUserApi");
        }
        return auditUserApi;
    }

    private EntitlementUserApi getEntitlementUserApi() throws AnalyticsRefreshException {
        final EntitlementUserApi entitlementUserApi = osgiKillbillAPI.getEntitlementUserApi();
        if (entitlementUserApi == null) {
            throw new AnalyticsRefreshException("Error retrieving entitlementUserApi");
        }
        return entitlementUserApi;
    }

    private InvoiceUserApi getInvoiceUserApi() throws AnalyticsRefreshException {
        final InvoiceUserApi invoiceUserApi = osgiKillbillAPI.getInvoiceUserApi();
        if (invoiceUserApi == null) {
            throw new AnalyticsRefreshException("Error retrieving invoiceUserApi");
        }
        return invoiceUserApi;
    }

    private CatalogUserApi getCatalogUserApi() throws AnalyticsRefreshException {
        final CatalogUserApi catalogUserApi = osgiKillbillAPI.getCatalogUserApi();
        if (catalogUserApi == null) {
            throw new AnalyticsRefreshException("Error retrieving catalogUserApi");
        }
        return catalogUserApi;
    }

    private PaymentApi getPaymentUserApi() throws AnalyticsRefreshException {
        final PaymentApi paymentApi = osgiKillbillAPI.getPaymentApi();
        if (paymentApi == null) {
            throw new AnalyticsRefreshException("Error retrieving paymentApi");
        }
        return paymentApi;
    }

    private InvoicePaymentApi getInvoicePaymentUserApi() throws AnalyticsRefreshException {
        final InvoicePaymentApi invoicePaymentApi = osgiKillbillAPI.getInvoicePaymentApi();
        if (invoicePaymentApi == null) {
            throw new AnalyticsRefreshException("Error retrieving invoicePaymentApi");
        }
        return invoicePaymentApi;
    }
}
