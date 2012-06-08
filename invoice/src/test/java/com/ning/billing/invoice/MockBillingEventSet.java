/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import com.ning.billing.entitlement.api.billing.BillingEvent;
import com.ning.billing.junction.api.BillingEventSet;

public class MockBillingEventSet extends TreeSet<BillingEvent> implements BillingEventSet {

    private static final long serialVersionUID = 1L;

    private boolean isAccountInvoiceOff;
    private List<UUID> subscriptionIdsWithAutoInvoiceOff = new ArrayList<UUID>();

    public void addSubscriptionWithAutoInvoiceOff(UUID subscriptionId) {
        subscriptionIdsWithAutoInvoiceOff.add(subscriptionId);
    }

    @Override
    public boolean isLast(BillingEvent event) {
        return event == last();
     }

    @Override
    public boolean isAccountAutoInvoiceOff() {
        return isAccountInvoiceOff;
    }

    @Override
    public List<UUID> getSubscriptionIdsWithAutoInvoiceOff() {
        return subscriptionIdsWithAutoInvoiceOff;
    }

    public void setAccountInvoiceOff(boolean isAccountInvoiceOff) {
        this.isAccountInvoiceOff = isAccountInvoiceOff;
    }

    public void setSubscriptionIdsWithAutoInvoiceOff(List<UUID> subscriptionIdsWithAutoInvoiceOff) {
        this.subscriptionIdsWithAutoInvoiceOff = subscriptionIdsWithAutoInvoiceOff;
    }

    public void clearSubscriptionsWithAutoInvoiceOff() {
        subscriptionIdsWithAutoInvoiceOff.clear();
    }
}
