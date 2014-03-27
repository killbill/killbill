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

package org.killbill.billing.junction.plumbing.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;

public class DefaultBillingEventSet extends TreeSet<BillingEvent> implements SortedSet<BillingEvent>, BillingEventSet {

    private static final long serialVersionUID = 1L;

    private boolean accountAutoInvoiceOff = false;
    private List<UUID> subscriptionIdsWithAutoInvoiceOff = new ArrayList<UUID>();
    private BillingMode recurrringBillingMode;

    /* (non-Javadoc)
    * @see org.killbill.billing.junction.plumbing.billing.BillingEventSet#isAccountAutoInvoiceOff()
    */
    @Override
    public boolean isAccountAutoInvoiceOff() {
        return accountAutoInvoiceOff;
    }

    @Override
    public BillingMode getRecurringBillingMode() {
        return null;
    }

    /* (non-Javadoc)
    * @see org.killbill.billing.junction.plumbing.billing.BillingEventSet#getSubscriptionIdsWithAutoInvoiceOff()
    */
    @Override
    public List<UUID> getSubscriptionIdsWithAutoInvoiceOff() {
        return subscriptionIdsWithAutoInvoiceOff;
    }

    public void setAccountAutoInvoiceIsOff(final boolean accountAutoInvoiceIsOff) {
        this.accountAutoInvoiceOff = accountAutoInvoiceIsOff;
    }

    public BillingMode getRecurrringBillingMode() {
        return recurrringBillingMode;
    }

    public void setRecurrringBillingMode(final BillingMode recurrringBillingMode) {
        this.recurrringBillingMode = recurrringBillingMode;
    }

    @Override
    public String toString() {
        return "DefaultBillingEventSet [accountAutoInvoiceOff=" + accountAutoInvoiceOff
               + ", subscriptionIdsWithAutoInvoiceOff=" + subscriptionIdsWithAutoInvoiceOff + ", Events="
               + super.toString() + "]";
    }

}
