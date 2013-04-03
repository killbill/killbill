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

package com.ning.billing.osgi.bundles.analytics.model;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.util.audit.AuditLog;

public class BusinessInvoicePaymentRefundModelDao extends BusinessInvoicePaymentBaseModelDao {

    public BusinessInvoicePaymentRefundModelDao(final Account account,
                                                final Invoice invoice,
                                                final InvoicePayment invoicePayment,
                                                final Payment payment,
                                                final PaymentMethod paymentMethod,
                                                final AuditLog creationAuditLog) {
        super(account,
              invoice,
              invoicePayment,
              payment,
              paymentMethod,
              creationAuditLog);
    }

    @Override
    public String getTableName() {
        return INVOICE_PAYMENT_REFUNDS_TABLE_NAME;
    }
}
