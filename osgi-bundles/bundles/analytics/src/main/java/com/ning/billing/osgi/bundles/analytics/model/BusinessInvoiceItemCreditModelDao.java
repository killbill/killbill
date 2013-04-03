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

import javax.annotation.Nullable;

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.util.audit.AuditLog;

public class BusinessInvoiceItemCreditModelDao extends BusinessInvoiceItemBaseModelDao {

    public BusinessInvoiceItemCreditModelDao(final Account account,
                                             final Invoice invoice,
                                             final InvoiceItem invoiceItem,
                                             @Nullable final SubscriptionBundle bundle,
                                             @Nullable final Plan plan,
                                             @Nullable final PlanPhase planPhase,
                                             final AuditLog creationAuditLogs) {
        super(account,
              invoice,
              invoiceItem,
              bundle,
              plan,
              planPhase,
              creationAuditLogs);
    }

    @Override
    public String getTableName() {
        return ACCOUNT_CREDITS_TABLE_NAME;
    }
}
