/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class InvoiceBillingEventModelDao extends EntityModelDaoBase implements EntityModelDao<Entity> {

    private UUID invoiceId;
    private byte[] billingEvents;

    /* For the DAO mapper */
    public InvoiceBillingEventModelDao() {
    }

    public InvoiceBillingEventModelDao(final UUID invoiceId, final byte[] billingEvents, final DateTime createdDate) {
        super(UUIDs.randomUUID(), createdDate, createdDate);
        this.invoiceId = invoiceId;
        this.billingEvents = billingEvents;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(final UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public byte[] getBillingEvents() {
        return billingEvents;
    }

    public void setBillingEvents(final byte[] billingEvents) {
        this.billingEvents = billingEvents;
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICE_BILLING_EVENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
