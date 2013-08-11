package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.Subscription;
import com.ning.billing.util.audit.AuditLog;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionJsonNoEvents extends EntitlementJsonNoEvents {


    private final LocalDate chargedThroughDate;

    private final LocalDate billingStartDate;
    private final LocalDate billingEndDate;
    private final Integer bcd;
    private final String billingState;
    //private final Map<String, String> currentStatesForServices;


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
                                    @JsonProperty("billingEndDate") @Nullable final LocalDate billingEndDate,
                                    @JsonProperty("bcd") @Nullable final Integer bcd,
                                    @JsonProperty("billingState") @Nullable final String billingState) {
        super(accountId, bundleId, entitlementId, externalKey, startDate, productName, productCategory, billingPeriod, priceList, cancelledDate, auditLogs);
        this.chargedThroughDate = chargedThroughDate;
        this.billingStartDate = billingStartDate;
        this.billingEndDate = billingEndDate;
        this.bcd = bcd;
        this.billingState = billingState;
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
             s.getBillingEndDate(),
             s.getBCD(),
             s.getBillingState() != null ? s.getBillingState().name() : null);
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

    public Integer getBcd() {
        return bcd;
    }

    public String getBillingState() {
        return billingState;
    }
}
