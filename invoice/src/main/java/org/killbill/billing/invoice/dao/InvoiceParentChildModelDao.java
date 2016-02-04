/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.invoice.api.InvoiceParentChild;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class InvoiceParentChildModelDao extends EntityModelDaoBase implements EntityModelDao<InvoiceParentChild> {

    private UUID parentInvoiceId;
    private UUID childInvoiceId;
    private UUID childAccountId;

    public InvoiceParentChildModelDao() { /* For the DAO mapper */ };

    public InvoiceParentChildModelDao(final UUID parentInvoiceId, final UUID childInvoiceId, final UUID childAccountId) {
        this(UUID.randomUUID(), DateTime.now(), parentInvoiceId, childInvoiceId, childAccountId);
    }

    public InvoiceParentChildModelDao(final UUID id, @Nullable final DateTime createdDate, final UUID parentInvoiceId, final UUID childInvoiceId, final UUID childAccountId) {
        super(id, createdDate, createdDate);
        this.parentInvoiceId = parentInvoiceId;
        this.childInvoiceId = childInvoiceId;
        this.childAccountId = childAccountId;
    }

    public InvoiceParentChildModelDao(final InvoiceParentChild invoiceParentChild) {
        this(invoiceParentChild.getParentInvoiceId(), invoiceParentChild.getChildInvoiceId(), invoiceParentChild.getChildAccountId());
    }

    public UUID getParentInvoiceId() {
        return parentInvoiceId;
    }

    public void setParentInvoiceId(final UUID parentInvoiceId) {
        this.parentInvoiceId = parentInvoiceId;
    }

    public UUID getChildInvoiceId() {
        return childInvoiceId;
    }

    public void setChildInvoiceId(final UUID childInvoiceId) {
        this.childInvoiceId = childInvoiceId;
    }

    public UUID getChildAccountId() {
        return childAccountId;
    }

    public void setChildAccountId(final UUID childAccountId) {
        this.childAccountId = childAccountId;
    }

    @Override
    public String toString() {
        return "InvoiceParentChildModelDao{" +
               "parentInvoiceId=" + parentInvoiceId +
               ", childInvoiceId=" + childInvoiceId +
               ", childAccountId=" + childAccountId +
               '}';
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

        final InvoiceParentChildModelDao that = (InvoiceParentChildModelDao) o;

        if (parentInvoiceId != null ? !parentInvoiceId.equals(that.parentInvoiceId) : that.parentInvoiceId != null) {
            return false;
        }
        if (childInvoiceId != null ? !childInvoiceId.equals(that.childInvoiceId) : that.childInvoiceId != null) {
            return false;
        }
        return childAccountId != null ? childAccountId.equals(that.childAccountId) : that.childAccountId == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (parentInvoiceId != null ? parentInvoiceId.hashCode() : 0);
        result = 31 * result + (childInvoiceId != null ? childInvoiceId.hashCode() : 0);
        result = 31 * result + (childAccountId != null ? childAccountId.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.INVOICE_PARENT_CHILDREN;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
