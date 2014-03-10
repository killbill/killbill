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

package org.killbill.billing.overdue.applicator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.entitlement.api.Blockable;
import org.killbill.billing.overdue.OverdueState;
import org.killbill.billing.overdue.applicator.formatters.OverdueEmailFormatterFactory;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.util.email.templates.TemplateEngine;

import com.google.inject.Inject;

public class OverdueEmailGenerator {

    private final TemplateEngine templateEngine;
    private final OverdueEmailFormatterFactory overdueEmailFormatterFactory;

    @Inject
    public OverdueEmailGenerator(final TemplateEngine templateEngine, final OverdueEmailFormatterFactory overdueEmailFormatterFactory) {
        this.templateEngine = templateEngine;
        this.overdueEmailFormatterFactory = overdueEmailFormatterFactory;
    }

    public String generateEmail(final Account account, final BillingState billingState,
                                                      final Account overdueable, final OverdueState nextOverdueState) throws IOException {
        final Map<String, Object> data = new HashMap<String, Object>();

        // TODO raw objects for now. We eventually should respect the account locale and support translations
        data.put("account", account);
        data.put("billingState", overdueEmailFormatterFactory.createBillingStateFormatter(billingState));
        data.put("overdueable", overdueable);
        data.put("nextOverdueState", nextOverdueState);

        // TODO single template for all languages for now
        return templateEngine.executeTemplate(nextOverdueState.getEnterStateEmailNotification().getTemplateName(), data);
    }
}
