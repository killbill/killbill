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

package com.ning.billing.invoice.generator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.RepairAdjInvoiceItem;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.config.InvoiceConfig;

import com.google.inject.Inject;

public class DefaultInvoiceGeneratorWithSwitchRepairLogic extends DefaultInvoiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceGeneratorWithSwitchRepairLogic.class);

    private REPAIR_INVOICE_LOGIC repairtLogic;

    public enum REPAIR_INVOICE_LOGIC {
        FULL_REPAIR,
        PARTIAL_REPAIR
    }


    @Inject
    public DefaultInvoiceGeneratorWithSwitchRepairLogic(final Clock clock, final InvoiceConfig config) {
        super(clock, config);
        // Default DefaultInvoicegenerator repair logic
        setDefaultRepairLogic(REPAIR_INVOICE_LOGIC.PARTIAL_REPAIR);
    }

    protected void removeProposedRepareeForPartialrepair(final InvoiceItem repairedItem, final List<InvoiceItem> proposedItems) {
        if (repairtLogic == REPAIR_INVOICE_LOGIC.PARTIAL_REPAIR) {
            super.removeProposedRepareeForPartialrepair(repairedItem, proposedItems);
        }
    }

    void addRepairItem(final InvoiceItem repairedItem, final RepairAdjInvoiceItem candidateRepairItem, final List<InvoiceItem> proposedItems) {
        if (repairtLogic == REPAIR_INVOICE_LOGIC.PARTIAL_REPAIR) {
            super.addRepairItem(repairedItem, candidateRepairItem, proposedItems);
        } else {
            proposedItems.add(candidateRepairItem);
            return;
        }
    }


    public void setDefaultRepairLogic(final REPAIR_INVOICE_LOGIC repairLogic) {
        log.info("Switching DefaultInvoiceGenerator repair logic to : " + repairLogic);
        this.repairtLogic = repairLogic;
    }
}
