package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionJsonNoEvents extends EntitlementJsonNoEvents {

    private final String chargedThroughDate;

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
                                    @JsonProperty("chargedThroughDate") @Nullable final String chargedThroughDate) {
        super(accountId, bundleId, entitlementId, externalKey, startDate, productName, productCategory, billingPeriod, priceList, cancelledDate, auditLogs);
        this.chargedThroughDate = chargedThroughDate;
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
             s.getChargedThroughDate() != null ? s.getChargedThroughDate().toString() : null);
    }

    public String getChargedThroughDate() {
        return chargedThroughDate;
    }
}
