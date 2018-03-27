/*
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

package org.killbill.billing.invoice.api;

import java.util.List;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.util.callcontext.CallContext;

public class DefaultInvoiceContext extends DefaultCallContext implements InvoiceContext {

    private final LocalDate targetDate;
    private final Invoice invoice;
    private final List<Invoice> existingInvoices;
    private final boolean isDryRun;
    private final boolean isRescheduled;

    public DefaultInvoiceContext(final LocalDate targetDate,
                                 final Invoice invoice,
                                 final List<Invoice> existingInvoices,
                                 final boolean isDryRun,
                                 final boolean isRescheduled,
                                 final CallContext context) {
        super(context.getAccountId(),
              context.getTenantId(),
              context.getUserName(),
              context.getCallOrigin(),
              context.getUserType(),
              context.getReasonCode(),
              context.getComments(),
              context.getUserToken(),
              context.getCreatedDate(),
              context.getUpdatedDate());
        this.targetDate = targetDate;
        this.invoice = invoice;
        this.existingInvoices = existingInvoices;
        this.isDryRun = isDryRun;
        this.isRescheduled = isRescheduled;
    }

    @Override
    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Override
    public Invoice getInvoice() {
        return invoice;
    }

    @Override
    public List<Invoice> getExistingInvoices() {
        return existingInvoices;
    }

    @Override
    public boolean isDryRun() {
        return isDryRun;
    }

    @Override
    public boolean isRescheduled() {
        return isRescheduled;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultInvoiceContext{");
        sb.append("targetDate=").append(targetDate);
        sb.append(", invoice=").append(invoice);
        sb.append(", isDryRun=").append(isDryRun);
        sb.append(", isRescheduled=").append(isRescheduled);
        sb.append(", accountId=").append(accountId);
        sb.append(", tenantId=").append(tenantId);
        sb.append(", userToken=").append(userToken);
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", callOrigin=").append(callOrigin);
        sb.append(", userType=").append(userType);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comments='").append(comments).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultInvoiceContext that = (DefaultInvoiceContext) o;

        if (isDryRun != that.isDryRun) {
            return false;
        }
        if (isRescheduled != that.isRescheduled) {
            return false;
        }
        if (targetDate != null ? targetDate.compareTo(that.targetDate) != 0 : that.targetDate != null) {
            return false;
        }
        if (invoice != null ? !invoice.equals(that.invoice) : that.invoice != null) {
            return false;
        }
        return existingInvoices != null ? existingInvoices.equals(that.existingInvoices) : that.existingInvoices == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (invoice != null ? invoice.hashCode() : 0);
        result = 31 * result + (existingInvoices != null ? existingInvoices.hashCode() : 0);
        result = 31 * result + (isDryRun ? 1 : 0);
        result = 31 * result + (isRescheduled ? 1 : 0);
        return result;
    }
}
