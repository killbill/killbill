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

package org.killbill.billing.junction.plumbing.billing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;

public class DefaultBillingEventSet extends TreeSet<BillingEvent> implements SortedSet<BillingEvent>, BillingEventSet {

    private static final long serialVersionUID = 1L;

    private final boolean accountAutoInvoiceOff;
    private final boolean accountAutoInvoiceDraft;
    private final boolean accountAutoInvoiceReuseDraft;
    private final List<UUID> subscriptionIdsWithAutoInvoiceOff;

    public DefaultBillingEventSet(final boolean accountAutoInvoiceOff, final boolean accountAutoInvoiceDraft, final boolean accountAutoInvoiceReuseDraft) {
        this.accountAutoInvoiceOff = accountAutoInvoiceOff;
        this.accountAutoInvoiceDraft = accountAutoInvoiceDraft;
        this.accountAutoInvoiceReuseDraft = accountAutoInvoiceReuseDraft;
        this.subscriptionIdsWithAutoInvoiceOff = new ArrayList<UUID>();
    }

    @Override
    public boolean isAccountAutoInvoiceOff() {
        return accountAutoInvoiceOff;
    }

    @Override
    public boolean isAccountAutoInvoiceDraft() {
        return accountAutoInvoiceDraft;
    }

    @Override
    public boolean isAccountAutoInvoiceReuseDraft() {
        return accountAutoInvoiceReuseDraft;
    }

    @Override
    public List<UUID> getSubscriptionIdsWithAutoInvoiceOff() {
        return subscriptionIdsWithAutoInvoiceOff;
    }

    @Override
    public Map<String, Usage> getUsages() {
        final Map<String, Usage> result = new HashMap<>();
        this.stream()
            .map(input -> {
                try {
                    return input.getUsages();
                } catch (final CatalogApiException e) {
                    throw new IllegalStateException(String.format("Failed to retrieve usage section for billing event %s", input), e);
                }
            }).flatMap(Collection::stream)
            .forEach(usage -> result.put(usage.getName(), usage));
        return result;
    }

    @Override
    public String toString() {
        return "DefaultBillingEventSet [accountAutoInvoiceOff=" + accountAutoInvoiceOff
               + ", subscriptionIdsWithAutoInvoiceOff=" + subscriptionIdsWithAutoInvoiceOff + ", Events="
               + super.toString() + "]";
    }
}
