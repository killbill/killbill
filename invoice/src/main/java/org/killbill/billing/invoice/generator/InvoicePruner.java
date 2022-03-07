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

package org.killbill.billing.invoice.generator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

//
// This is invoked prior we build the tree of existing items to make sure we simplify the view,
// and in particular in cases of complex repair (see #1205), we don't end up inserting items
// that overlap and break our interval-based tree structure.
//
// TODO This class should not deal with adjustments except for the fact that we currently keep the compatibility with previous behavior
// in terms of Preconditions. I think we should either remove them or introduce a 'strict' mode
//
public class InvoicePruner {

    private final Map<UUID, AdjustedRecurringItem> map;

    public InvoicePruner(final AccountInvoices accountInvoices) {

        final boolean isInvoiceOptimizationOn = accountInvoices.getCutoffDate() != null;

        final Iterable<Invoice> existingInvoices = accountInvoices.getInvoices();
        this.map = new HashMap<>();
        if (existingInvoices != null) {
            for (Invoice i : existingInvoices) {
                for (InvoiceItem ii : i.getInvoiceItems()) {

                    UUID targetId = null;
                    switch (ii.getInvoiceItemType()) {
                        case RECURRING:
                            targetId = ii.getId();
                            break;
                        case REPAIR_ADJ:
                        case ITEM_ADJ:
                            targetId = ii.getLinkedItemId();
                            break;
                        default:
                            break;
                    }

                    if (targetId != null) {
                        AdjustedRecurringItem entry = map.get(targetId);
                        if (entry == null) {
                            entry = new AdjustedRecurringItem(isInvoiceOptimizationOn);
                            map.put(targetId, entry);
                        }
                        entry.addInvoiceItem(ii);
                    }
                }
            }
        }
    }

    // In case of full repair return all items linked to the original RECURRING item
    public Set<UUID> getFullyRepairedItemsClosure() throws InvoiceApiException {
        try {
            final Set<UUID> result = new HashSet();
            for (AdjustedRecurringItem cur : map.values()) {
                result.addAll(cur.getFullyRepairedLinkedItems());
            }
            return result;
        } catch (final IllegalStateException e) {
            throw new InvoiceApiException(e, ErrorCode.UNEXPECTED_ERROR, String.format("ILLEGAL INVOICING STATE"));
        }
    }

    private static class AdjustedRecurringItem {

        private final boolean isInvoiceOptimizationOn;

        private InvoiceItem target;
        private List<InvoiceItem> repaired;
        private List<InvoiceItem> adjusted;

        private Set<UUID> result;
        private boolean isBuilt;

        public AdjustedRecurringItem(final boolean isInvoiceOptimizationOn) {
            this.isInvoiceOptimizationOn = isInvoiceOptimizationOn;
            result = new HashSet<>();
            isBuilt = false;
        }

        public void addInvoiceItem(final InvoiceItem item) {
            if (item.getInvoiceItemType() == InvoiceItemType.RECURRING) {
                setTargetItem(item);
            } else if (item.getInvoiceItemType() == InvoiceItemType.REPAIR_ADJ) {
                addRepairInvoiceItem(item);
            } else if (item.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ) {
                addAdjInvoiceItem(item);
            } else {
                Preconditions.checkState(false, "Unexpected item type %s", item.getInvoiceItemType());
            }
        }

        public Set<UUID> getFullyRepairedLinkedItems() {
            build();
            return result;
        }



        private void setTargetItem(final InvoiceItem item) {
            Preconditions.checkState(target == null, "Unexpected second recurring item with ID %s", item.getId());
            this.target = item;
        }

        private void addRepairInvoiceItem(final InvoiceItem item) {
            if (repaired == null) {
                repaired = new ArrayList<>();
            }
            repaired.add(item);
        }

        private void addAdjInvoiceItem(final InvoiceItem item) {
            if (adjusted == null) {
                adjusted = new ArrayList<>();
            }
            adjusted.add(item);
        }

        private void build() {

            if (isBuilt) {
                return;
            }

            if (target == null || target.getAmount() == null) {
                if (repaired != null) {

                    for (final InvoiceItem i : repaired) {
                        // When invoice optimization is ON, we may miss state from previous invoices leading to having 'dangling' repair item
                        if (isInvoiceOptimizationOn) {
                            result.add(i.getId());
                        } else {
                            // Keep previous precondition behavior
                            Preconditions.checkState(false, "Missing cancelledItem for cancelItem=%s", i);
                        }
                    }
                }
                return;
            }

            if (target.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                // Ignore $0 items
                return;
            }

            final BigDecimal repairedAmount = sumAmounts(repaired).negate();

            if (repaired != null) {
                for (final InvoiceItem i : repaired) {
                    // Keep previous precondition behavior
                    Preconditions.checkState(i.getStartDate().compareTo(target.getStartDate()) >= 0 && i.getEndDate().compareTo(target.getEndDate()) <= 0,
                                             "Invalid cancelledItem=%s for cancelItem=%s", i.getId(), target.getId());
                }
            }


            final BigDecimal totalAdjusted = sumAmounts(adjusted).negate();
            final BigDecimal remainingFromAdjusted = target.getAmount().subtract(totalAdjusted);
            final int compAdjOnly = remainingFromAdjusted.compareTo(BigDecimal.ZERO);
            if (compAdjOnly == -1) {
                // We adjusted too much :-(
                // In normal cases, the code should prevent this : https://github.com/killbill/killbill/blob/killbill-0.21.6/invoice/src/main/java/org/killbill/billing/invoice/dao/InvoiceDaoHelper.java#L115
                // However, this is possible to bypass this logic when ITEM_ADJ are added from within a invoice plugin, so we ignore it -- this does not seem to create too much side effects.
                // @see TestIntegrationInvoiceWithRepairLogic#testAdjustmentsToolarge
                //
            } else {
                final BigDecimal remainingFromRepair = target.getAmount().subtract(repairedAmount);
                final int compRepair = remainingFromRepair.compareTo(BigDecimal.ZERO);


                // We repaired the whole thing
                if (compRepair == 0) {
                    // Keep previous precondition behavior and check there is no extra adjustment on top of it.
                    // @see TestIntegrationInvoiceWithRepairLogic#testWithFullRepairAndExistingPartialAdjustment
                    Preconditions.checkState(adjusted == null || adjusted.isEmpty(), "Too many repairs for invoiceItemId='%s', fully repaired and adjusted='%s'",
                                             target.getId(),
                                             totalAdjusted);
                    result.add(target.getId());
                    result.addAll(getIds(repaired));
                } else {
                    // Keep previous precondition behavior and check the sum of total repair + total adjustments is not more than original amount
                    final int compWithAdjustments = remainingFromRepair.subtract(totalAdjusted).compareTo(BigDecimal.ZERO);
                    if (compWithAdjustments == -1) {
                        // @see TestIntegrationInvoiceWithRepairLogic#testWithPartialRepairAndExistingPartialTooLargeAdjustment
                        Preconditions.checkState(false, "Too many repairs for invoiceItemId='%s', partially repaired='%s', partially adjusted='%s', total='%s'",
                                                 target.getId(),
                                                 repairedAmount,
                                                 totalAdjusted,
                                                 target.getAmount());
                    }
                }
            }

            this.isBuilt = true;
        }

        private Set<UUID> getIds(final Iterable<? extends InvoiceItem> items) {
            if (items == null) {
                return ImmutableSet.of();
            }

            return ImmutableSet.copyOf(Iterables.transform(items, new Function<InvoiceItem, UUID>() {
                @Override
                public UUID apply(final InvoiceItem input) {
                    return input.getId();
                }
            }));
        }

        private BigDecimal sumAmounts(final Iterable<? extends InvoiceItem> items) {
            BigDecimal result = BigDecimal.ZERO;
            if (items != null) {
                for (final InvoiceItem cur : items) {
                    final BigDecimal amount = cur.getAmount() != null ? cur.getAmount() : BigDecimal.ZERO;
                    result = result.add(amount);
                }
            }
            return result;
        }
    }

}
