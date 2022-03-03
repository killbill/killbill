/*
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.invoice.optimizer;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.Period;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.clock.Clock;
import org.skife.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvoiceOptimizerNoop extends InvoiceOptimizerBase {

    private static Logger logger = LoggerFactory.getLogger(InvoiceOptimizerNoop.class);

    @Inject
    public InvoiceOptimizerNoop(final InvoiceDao invoiceDao,
                               final Clock clock,
                               final InvoiceConfig invoiceConfig) {
        super(invoiceDao, clock, invoiceConfig);
        logger.info("Feature InvoiceOptimizer is OFF");
    }

    @Override
    public AccountInvoices getInvoices(final InternalCallContext callContext) {

        logDisabledFeatureIfNeeded(callContext);
        final List<Invoice> existingInvoices = new LinkedList<Invoice>();
        final List<InvoiceModelDao> invoicesByAccount = invoiceDao.getInvoicesByAccount(false, callContext);
        for (final InvoiceModelDao invoiceModelDao : invoicesByAccount) {
            existingInvoices.add(new DefaultInvoice(invoiceModelDao));
        }
        return new AccountInvoices(null, null, existingInvoices);
    }

    @Override
    public boolean rescheduleProcessAccount(final UUID accountId, final InternalCallContext context) {
        return false;
    }

    private void logDisabledFeatureIfNeeded(final InternalCallContext callContext) {

        final Period maxInvoiceLimit = invoiceConfig.getMaxInvoiceLimit(callContext);
        if (maxInvoiceLimit == null) {
            return;
        }

        try {
            final Method method = invoiceConfig.getClass().getMethod("getMaxInvoiceLimit");
            if (method.isAnnotationPresent(Config.class)) {
                final Config annotation = method.getAnnotation(Config.class);
                // config-magic already does sanity checking
                final String property = annotation.value()[0];
                logger.warn("Unsupported property {}, will be ignored...", property);
            }
        } catch (final NoSuchMethodException e) {
            logger.warn("Failed to retrieve name of unsupported property", e);
        }
    }

}
