/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;

public class MockBillingEventSet extends TreeSet<BillingEvent> implements BillingEventSet {

    private static final long serialVersionUID = 1L;

    private boolean isAccountInvoiceOff;
    private boolean isAccountAutoInvoiceDraft;
    private boolean isAccountAutoInvoiceReuseDraft;

    private List<UUID> subscriptionIdsWithAutoInvoiceOff;

    public MockBillingEventSet() {
        super();
        this.isAccountInvoiceOff = false;
        this.isAccountAutoInvoiceDraft = false;
        this.isAccountAutoInvoiceReuseDraft = false;
        this.subscriptionIdsWithAutoInvoiceOff = new ArrayList<UUID>();
    }

    public void addSubscriptionWithAutoInvoiceOff(final UUID subscriptionId) {
        subscriptionIdsWithAutoInvoiceOff.add(subscriptionId);
    }

    @Override
    public boolean isAccountAutoInvoiceOff() {
        return isAccountInvoiceOff;
    }

    @Override
    public boolean isAccountAutoInvoiceDraft() {
        return isAccountAutoInvoiceDraft;
    }

    @Override
    public boolean isAccountAutoInvoiceReuseDraft() {
        return isAccountAutoInvoiceReuseDraft;
    }

    @Override
    public List<UUID> getSubscriptionIdsWithAutoInvoiceOff() {
        return subscriptionIdsWithAutoInvoiceOff;
    }

    @Override
    public Map<String, Usage> getUsages() {
        return Collections.emptyMap();
    }

    public void setAccountInvoiceOff(final boolean isAccountInvoiceOff) {
        this.isAccountInvoiceOff = isAccountInvoiceOff;
    }

    public void setAccountAutoInvoiceDraft(final boolean isAccountAutoInvoiceDraft) {
        this.isAccountAutoInvoiceDraft = isAccountAutoInvoiceDraft;

    }

    public void setSubscriptionIdsWithAutoInvoiceOff(final List<UUID> subscriptionIdsWithAutoInvoiceOff) {
        this.subscriptionIdsWithAutoInvoiceOff = subscriptionIdsWithAutoInvoiceOff;
    }

    public void clearSubscriptionsWithAutoInvoiceOff() {
        subscriptionIdsWithAutoInvoiceOff.clear();
    }
}
