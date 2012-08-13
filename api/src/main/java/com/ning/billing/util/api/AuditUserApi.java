/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForBundles;
import com.ning.billing.util.audit.AuditLogsForInvoicePayments;
import com.ning.billing.util.audit.AuditLogsForInvoices;
import com.ning.billing.util.audit.AuditLogsForPayments;
import com.ning.billing.util.audit.AuditLogsForRefunds;
import com.ning.billing.util.dao.ObjectType;

public interface AuditUserApi {

    /**
     * Fetch all audit logs for bundles.
     *
     * @param bundles    the bundles to lookup
     * @param auditLevel audit level (verbosity)
     * @return all audit logs for these refunds
     */
    public AuditLogsForBundles getAuditLogsForBundles(final List<SubscriptionBundle> bundles, final AuditLevel auditLevel);

    /**
     * Fetch all audit logs for invoice payments.
     *
     * @param invoicePayments the invoice payments to lookup
     * @param auditLevel      audit level (verbosity)
     * @return all audit logs for these invoice payments
     */
    public AuditLogsForInvoicePayments getAuditLogsForInvoicePayments(final List<InvoicePayment> invoicePayments, final AuditLevel auditLevel);

    /**
     * Fetch all audit logs for refunds.
     *
     * @param refunds    the refunds to lookup
     * @param auditLevel audit level (verbosity)
     * @return all audit logs for these refunds
     */
    public AuditLogsForRefunds getAuditLogsForRefunds(final List<Refund> refunds, final AuditLevel auditLevel);

    /**
     * Fetch all audit logs for payments.
     *
     * @param payments   the payments to lookup
     * @param auditLevel audit level (verbosity)
     * @return all audit logs for these payments
     */
    public AuditLogsForPayments getAuditLogsForPayments(final List<Payment> payments, final AuditLevel auditLevel);

    /**
     * Fetch all audit logs for invoices and associated invoice items.
     *
     * @param invoices   the invoices to lookup
     * @param auditLevel audit level (verbosity)
     * @return all audit logs for these invoices
     */
    public AuditLogsForInvoices getAuditLogsForInvoices(final List<Invoice> invoices, final AuditLevel auditLevel);

    /**
     * Get all the audit entries for a given object.
     *
     * @param objectId   the object id
     * @param objectType the type of object
     * @param auditLevel audit level (verbosity)
     * @return all audit entries for that object
     */
    public List<AuditLog> getAuditLogs(final UUID objectId, final ObjectType objectType, final AuditLevel auditLevel);
}
