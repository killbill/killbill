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

package org.killbill.billing.entitlement.logging;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.slf4j.Logger;

public abstract class EntitlementLoggingHelper {

    private static final int MAX_LENGTH = 1024;

    private static void logWithLimit(final Logger log, final String line) {
        final String logLine;
        if (line.length() >  MAX_LENGTH) {
            final StringBuilder tmp = new StringBuilder(line.substring(0, MAX_LENGTH));
            tmp.append("...");
            logLine = tmp.toString();
        } else {
            logLine = line;
        }
        log.info(logLine);
    }

    public static void logCreateEntitlementsWithAOs(final Logger log, final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementSpecifiersWithAddOns) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Create Entitlements with AddOns: ");

            if (baseEntitlementSpecifiersWithAddOns != null && baseEntitlementSpecifiersWithAddOns.iterator().hasNext()) {
                for (final BaseEntitlementWithAddOnsSpecifier cur : baseEntitlementSpecifiersWithAddOns) {
                    logCreateEntitlementWithAOs(logLine,
                                                cur.getBundleExternalKey(),
                                                cur.getEntitlementSpecifier(),
                                                cur.getEntitlementEffectiveDate(),
                                                cur.getBillingEffectiveDate());
                }
            }
            logWithLimit(log, logLine.toString());
        }
    }

    private static void logCreateEntitlementWithAOs(final StringBuilder logLine,
                                                    final String externalKey,
                                                    final Iterable<EntitlementSpecifier> entitlementSpecifiers,
                                                    final LocalDate entitlementDate,
                                                    final LocalDate billingDate) {
        if (externalKey != null) {
            logLine.append("bundleKey='")
                   .append(externalKey)
                   .append("'");
        }
        if (entitlementDate != null) {
            if (externalKey != null) {
                logLine.append(", ");
            }
            logLine.append("entDate='")
                   .append(entitlementDate)
                   .append("'");
        }
        if (billingDate != null) {
            if (externalKey != null || entitlementDate != null) {
                logLine.append(", ");
            }
            logLine.append("billDate='")
                   .append(billingDate)
                   .append("'");
        }
        if (entitlementSpecifiers != null && entitlementSpecifiers.iterator().hasNext()) {
            if (externalKey != null || entitlementDate != null || billingDate != null) {
                logLine.append(", ");
            }
            logEntitlementSpecifier(logLine, entitlementSpecifiers);
        }
    }

    public static void logPauseResumeEntitlement(final Logger log,
                                                 final String op,
                                                 final UUID bundleId,
                                                 final LocalDate effectiveDate) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder(op)
                    .append(" Entitlement: ");

            if (bundleId != null) {
                logLine.append(", bundleId='")
                       .append(bundleId)
                       .append("'");
            }
            if (effectiveDate != null) {
                logLine.append(", date='")
                       .append(effectiveDate)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logTransferEntitlement(final Logger log,
                                              final UUID sourceAccountId,
                                              final UUID destAccountId,
                                              final String externalKey,
                                              final LocalDate effectiveDate,
                                              final BillingActionPolicy billingPolicy) {

        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Transfer Entitlement: ");
            if (sourceAccountId != null) {
                logLine.append(", src='")
                       .append(sourceAccountId)
                       .append("'");
            }
            if (destAccountId != null) {
                logLine.append(", dst='")
                       .append(destAccountId)
                       .append("'");
            }
            if (externalKey != null) {
                logLine.append(", key='")
                       .append(externalKey)
                       .append("'");
            }
            if (effectiveDate != null) {
                logLine.append(", date='")
                       .append(effectiveDate)
                       .append("'");
            }
            if (effectiveDate != null) {
                logLine.append(", policy='")
                       .append(billingPolicy)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logCancelEntitlement(final Logger log, final Entitlement entitlement, final LocalDate entitlementEffectiveDate, final Boolean overrideBillingEffectiveDate, final EntitlementActionPolicy entitlementPolicy, final BillingActionPolicy billingPolicy) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Cancel Entitlement: ")
                    .append(" id = '")
                    .append(entitlement.getId())
                    .append("'");
            if (entitlementEffectiveDate != null) {
                logLine.append(", entDate='")
                       .append(entitlementEffectiveDate)
                       .append("'");
            }
            if (overrideBillingEffectiveDate != null) {
                logLine.append(", overrideBillDate='")
                       .append(overrideBillingEffectiveDate)
                       .append("'");
            }
            if (entitlementPolicy != null) {
                logLine.append(", entPolicy='")
                       .append(entitlementPolicy)
                       .append("'");
            }
            if (billingPolicy != null) {
                logLine.append(", billPolicy='")
                       .append(billingPolicy)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logUncancelEntitlement(final Logger log, final Entitlement entitlement) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Uncancel Entitlement: ")
                    .append(" id = '")
                    .append(entitlement.getId())
                    .append("'");
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logUndoChangePlan(final Logger log, final Entitlement entitlement) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Undo Entitlement Change Plan: ")
                    .append(" id = '")
                    .append(entitlement.getId())
                    .append("'");
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logChangePlan(final Logger log, final Entitlement entitlement, final EntitlementSpecifier entitlementSpecifier,
                                     final LocalDate entitlementEffectiveDate, final BillingActionPolicy actionPolicy) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Change Entitlement Plan: ")
                    .append(" id = '")
                    .append(entitlement.getId())
                    .append("'");
            if (entitlementEffectiveDate != null) {
                logLine.append(", entDate='")
                       .append(entitlementEffectiveDate)
                       .append("'");
            }
            if (entitlementSpecifier.getPlanPhaseSpecifier() != null) {

                if (entitlementSpecifier.getPlanPhaseSpecifier().getPlanName() != null) {
                    logLine.append(", plan='")
                           .append(entitlementSpecifier.getPlanPhaseSpecifier().getPlanName())
                           .append("'");
                }
                if (entitlementSpecifier.getPlanPhaseSpecifier().getProductName() != null) {
                    logLine.append(", product='")
                           .append(entitlementSpecifier.getPlanPhaseSpecifier().getProductName())
                           .append("'");
                }
                if (entitlementSpecifier.getPlanPhaseSpecifier().getBillingPeriod() != null) {
                    logLine.append(", billingPeriod='")
                           .append(entitlementSpecifier.getPlanPhaseSpecifier().getBillingPeriod())
                           .append("'");
                }
                if (entitlementSpecifier.getPlanPhaseSpecifier().getPriceListName() != null) {
                    logLine.append(", priceList='")
                           .append(entitlementSpecifier.getPlanPhaseSpecifier().getPriceListName())
                           .append("'");
                }
                logPlanPhasePriceOverrides(logLine, entitlementSpecifier.getOverrides());
                if (actionPolicy != null) {
                    logLine.append(", actionPolicy='")
                           .append(actionPolicy)
                           .append("'");
                }
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logUpdateBCD(final Logger log, final Entitlement entitlement, final int newBCD, final LocalDate effectiveFromDate) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Update Entitlement BCD: ")
                    .append(" id = '")
                    .append(entitlement.getId())
                    .append("'");

            logLine.append(", newBCD='")
                   .append(newBCD)
                   .append("'");
            if (effectiveFromDate != null) {
                logLine.append(", date='")
                       .append(effectiveFromDate)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logUpdateExternalKey(final Logger log, final UUID bundleId, final String newExternalKey) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Update Entitlement Key: ");
            if (bundleId != null) {
                logLine.append(", bundleId='")
                       .append(bundleId)
                       .append("'");
            }
            if (newExternalKey != null) {
                logLine.append(", key='")
                       .append(newExternalKey)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    public static void logAddBlockingState(final Logger log, final BlockingState inputBlockingState, final LocalDate inputEffectiveDate) {
        if (log.isInfoEnabled()) {
            final StringBuilder logLine = new StringBuilder("Add BlockingState Entitlement: ");
            logBlockingState(logLine, inputBlockingState);
            if (inputEffectiveDate != null) {
                logLine.append(", date='")
                       .append(inputEffectiveDate)
                       .append("'");
            }
            logWithLimit(log, logLine.toString());
        }
    }

    private static void logBlockingState(final StringBuilder logLine, final BlockingState blk) {
        if (blk != null) {
            logLine.append("blk='");
            logLine.append(blk.getBlockedId() != null ? blk.getBlockedId() : "null");
            logLine.append(":");
            logLine.append(blk.getType() != null ? blk.getType() : "null");
            logLine.append(":");
            logLine.append(blk.getService() != null ? blk.getService() : "null");
            logLine.append(":");
            logLine.append(blk.getStateName() != null ? blk.getStateName() : "null");
            logLine.append("'");
        }
    }

    private static void logEntitlementSpecifier(final StringBuilder logLine, final Iterable<EntitlementSpecifier> entitlementSpecifiers) {
        logLine.append("'[");
        boolean first = true;
        for (final EntitlementSpecifier cur : entitlementSpecifiers) {
            if (!first) {
                logLine.append(",");
            }
            logPlanPhaseSpecifier(logLine, cur.getPlanPhaseSpecifier(), false, false);
            logPlanPhasePriceOverrides(logLine, cur.getOverrides());
            first = false;
        }
        logLine.append("]'");
    }

    private static void logPlanPhaseSpecifier(final StringBuilder logLine, final PlanPhaseSpecifier spec, final boolean addComma, final boolean addParenthesisQuote) {
        if (spec != null) {
            if (addComma) {
                logLine.append(", ");
            }
            if (spec.getPlanName() != null) {
                logLine.append("planName=");
                if (addParenthesisQuote) {
                    logLine.append("'(");
                }
                logLine.append(spec.getPlanName());
            } else {
                logLine.append("spec=");
                if (addParenthesisQuote) {
                    logLine.append("'(");
                }
                logLine.append(spec.getProductName() != null ? spec.getProductName() : "null");
                logLine.append(":");
                logLine.append(spec.getBillingPeriod() != null ? spec.getBillingPeriod() : "null");
                logLine.append(":");
                logLine.append(spec.getPriceListName() != null ? spec.getPriceListName() : "null");
            }
            logLine.append(":");
            logLine.append(spec.getPhaseType() != null ? spec.getPhaseType() : "null");
            if (addParenthesisQuote) {
                logLine.append(")'");
            }
        }
    }

    private static void logPlanPhasePriceOverrides(final StringBuilder logLine, final List<PlanPhasePriceOverride> overrides) {
        if (overrides != null && !overrides.isEmpty()) {
            logLine.append(", overrides='[");
            boolean first = true;
            for (final PlanPhasePriceOverride cur : overrides) {
                if (!first) {
                    logLine.append(",");
                }
                logPlanPhasePriceOverride(logLine, cur);
                first = false;
            }
            logLine.append("]'");

        }
    }

    private static void logPlanPhasePriceOverride(final StringBuilder logLine, final PlanPhasePriceOverride override) {
        if (override != null) {
            logLine.append("(");
            logPlanPhaseSpecifier(logLine, override.getPlanPhaseSpecifier(), false, false);
            logLine.append(":");
            logLine.append(override.getPhaseName() != null ? override.getPhaseName() : "null");
            logLine.append(":");
            logLine.append(override.getCurrency() != null ? override.getCurrency() : "null");
            logLine.append(":");
            logLine.append(override.getFixedPrice() != null ? override.getFixedPrice() : "null");
            logLine.append(":");
            logLine.append(override.getRecurringPrice() != null ? override.getRecurringPrice() : "null");
            logLine.append(")");
        }
    }
}
