/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class InvoiceItemFactory {

    private static final Logger log = LoggerFactory.getLogger(InvoiceItemFactory.class);

    private InvoiceItemFactory() {}

    public static InvoiceItem fromModelDao(final InvoiceItemModelDao invoiceItemModelDao) {
        return fromModelDaoWithCatalog(invoiceItemModelDao, null);
    }

    public static InvoiceItem fromModelDaoWithCatalog(final InvoiceItemModelDao invoiceItemModelDao, @Nullable final Catalog catalog) {
        if (invoiceItemModelDao == null) {
            return null;
        }

        final UUID id = invoiceItemModelDao.getId();
        final DateTime createdDate = invoiceItemModelDao.getCreatedDate();
        final UUID invoiceId = invoiceItemModelDao.getInvoiceId();
        final UUID accountId = invoiceItemModelDao.getAccountId();
        final UUID childAccountId = invoiceItemModelDao.getChildAccountId();
        final UUID bundleId = invoiceItemModelDao.getBundleId();
        final UUID subscriptionId = invoiceItemModelDao.getSubscriptionId();
        final String productName = invoiceItemModelDao.getProductName();
        final String planName = invoiceItemModelDao.getPlanName();
        final String phaseName = invoiceItemModelDao.getPhaseName();
        final String usageName = invoiceItemModelDao.getUsageName();
        final String description = invoiceItemModelDao.getDescription();
        final LocalDate startDate = invoiceItemModelDao.getStartDate();
        final LocalDate endDate = invoiceItemModelDao.getEndDate();
        final BigDecimal amount = invoiceItemModelDao.getAmount();
        final BigDecimal rate = invoiceItemModelDao.getRate();
        final Currency currency = invoiceItemModelDao.getCurrency();
        final UUID linkedItemId = invoiceItemModelDao.getLinkedItemId();
        final Integer quantity = invoiceItemModelDao.getQuantity();
        final String itemDetails = invoiceItemModelDao.getItemDetails();

        final InvoiceItemType type = invoiceItemModelDao.getType();

        final String [] prettyNames = computePrettyName(type, createdDate, productName, planName, phaseName, usageName, catalog);
        String prettyProductName = prettyNames[0];
        String prettyPlanName = prettyNames[1];
        String prettyPlanPhaseName = prettyNames[2];
        String prettyUsageName = prettyNames[3];

        final InvoiceItem item;
        switch (type) {
            case EXTERNAL_CHARGE:
                item = new ExternalChargeInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, prettyProductName, prettyPlanName, prettyPlanPhaseName, description, startDate, endDate, amount, rate, currency, linkedItemId, itemDetails);
                break;
            case FIXED:
                item = new FixedPriceInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, prettyProductName, prettyPlanName, prettyPlanPhaseName, description, startDate, amount, currency);
                break;
            case RECURRING:
                item = new RecurringInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, prettyProductName, prettyPlanName, prettyPlanPhaseName, description, startDate, endDate, amount, rate, currency);
                break;
            case CBA_ADJ:
                item = new CreditBalanceAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, linkedItemId, description, amount, currency);
                break;
            case CREDIT_ADJ:
                item = new CreditAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, description, amount, currency, itemDetails);
                break;
            case REPAIR_ADJ:
                item = new RepairAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, endDate, description, amount, currency, linkedItemId);
                break;
            case ITEM_ADJ:
                item = new ItemAdjInvoiceItem(id, createdDate, invoiceId, accountId, startDate, description, amount, currency, linkedItemId, itemDetails);
                break;
            case USAGE:
                item = new UsageInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, prettyProductName, prettyPlanName, prettyPlanPhaseName, prettyUsageName, startDate, endDate, description, amount, rate, currency, quantity, itemDetails);
                break;
            case TAX:
                item = new TaxInvoiceItem(id, createdDate, invoiceId, accountId, bundleId, subscriptionId, productName, planName, phaseName, usageName, prettyProductName, prettyPlanName, prettyPlanPhaseName, prettyUsageName, startDate, description, amount, currency, linkedItemId);
                break;
            case PARENT_SUMMARY:
                item = new ParentInvoiceItem(id, createdDate, invoiceId, accountId, childAccountId, amount, currency, description);
                break;
            default:
                throw new RuntimeException("Unexpected type of event item " + type);
        }
        return item;
    }

    //
    // Returns an array of string for 'pretty' names [prettyPlanName, prettyPlanPhaseName, prettyUsageName]
    //
    private static String [] computePrettyName(final InvoiceItemType type, final DateTime createdDate, @Nullable final String productName, @Nullable final String planName, @Nullable final String phaseName, @Nullable final String usageName, @Nullable final Catalog catalog) {

        final String [] result = new String[4];

        final boolean computePrettyName = catalog != null &&
                                          (type == InvoiceItemType.FIXED || type == InvoiceItemType.RECURRING || type == InvoiceItemType.TAX || type == InvoiceItemType.USAGE);

        String prettyProductName = null;
        String prettyPlanName = null;
        String prettyPlanPhaseName = null;
        String prettyUsageName = null;

        // Trying to optimize safe default behavior by checking both input and each intermediary null result at each step -- and doing poor on the Cyclomatic complexity
        try {
            if (computePrettyName) {

                if (planName != null) {
                    final Plan plan = catalog.findPlan(planName, createdDate);
                    if (plan != null) {
                        prettyPlanName = plan.getPrettyName();

                        if (productName != null) {
                            Preconditions.checkState(plan.getProduct().getName().equals(productName));
                            prettyProductName = plan.getProduct().getPrettyName();
                        }

                        if (phaseName != null) {
                            final PlanPhase planPhase = plan.findPhase(phaseName);
                            if (planPhase != null) {
                                prettyPlanPhaseName = planPhase.getPrettyName();

                                if (usageName != null) {
                                    Usage usage = null;
                                    for (Usage cur : planPhase.getUsages()) {
                                        if (cur.getName().equals(usageName)) {
                                            usage = cur;
                                            break;
                                        }
                                    }
                                    if (usage != null) {
                                        prettyUsageName = usage.getPrettyName();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (final CatalogApiException e) {
            log.warn("Failed to compute invoice pretty names:", e.getMessage());
        } finally {
            result[0] = prettyProductName;
            result[1] = prettyPlanName;
            result[2] = prettyPlanPhaseName;
            result[3] = prettyUsageName;
        }
        return result;
    }
}
