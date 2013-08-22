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

package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionJsonNoEvents extends EntitlementJsonNoEvents {


    private final LocalDate chargedThroughDate;

    private final LocalDate billingStartDate;
    private final LocalDate billingEndDate;
    //private final Map<String, String> currentStatesForServices;

    @JsonCreator
    public SubscriptionJsonNoEvents(@JsonProperty("accountId") @Nullable final String accountId,
                                    @JsonProperty("bundleId") @Nullable final String bundleId,
                                    @JsonProperty("entitlementId") @Nullable final String entitlementId,
                                    @JsonProperty("externalKey") @Nullable final String externalKey,
                                    @JsonProperty("startDate") @Nullable final LocalDate startDate,
                                    @JsonProperty("productName") @Nullable final String productName,
                                    @JsonProperty("productCategory") @Nullable final String productCategory,
                                    @JsonProperty("billingPeriod") @Nullable final String billingPeriod,
                                    @JsonProperty("priceList") @Nullable final String priceList,
                                    @JsonProperty("cancelledDate") @Nullable final LocalDate cancelledDate,
                                    @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs,
                                    @JsonProperty("chargedThroughDate") @Nullable final LocalDate chargedThroughDate,
                                    @JsonProperty("billingStartDate") @Nullable final LocalDate billingStartDate,
                                    @JsonProperty("billingEndDate") @Nullable final LocalDate billingEndDate) {
        super(accountId, bundleId, entitlementId, externalKey, startDate, productName, productCategory, billingPeriod, priceList, cancelledDate, auditLogs);
        this.chargedThroughDate = chargedThroughDate;
        this.billingStartDate = billingStartDate;
        this.billingEndDate = billingEndDate;
    }

    public SubscriptionJsonNoEvents(final Subscription s,
                                    @Nullable final List<AuditLog> auditLogs) {
        this(s.getAccountId().toString(),
             s.getBundleId().toString(),
             s.getId().toString(),
             s.getExternalKey(),
             s.getEffectiveStartDate(),
             s.getProduct() != null ? s.getProduct().getName() : null,
             s.getProductCategory() != null ? s.getProductCategory().name() : null,
             s.getPlan() != null ? s.getPlan().getBillingPeriod().name() : null,
             s.getPriceList() != null ? s.getPriceList().getName() : null,
             s.getEffectiveEndDate(),
             toAuditLogJson(auditLogs),
             s.getChargedThroughDate() != null ? s.getChargedThroughDate() : null,
             s.getBillingStartDate(),
             s.getBillingEndDate());
    }

    public LocalDate getChargedThroughDate() {
        return chargedThroughDate;
    }

    public LocalDate getBillingStartDate() {
        return billingStartDate;
    }

    public LocalDate getBillingEndDate() {
        return billingEndDate;
    }

}
