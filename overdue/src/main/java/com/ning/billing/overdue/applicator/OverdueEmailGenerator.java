/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.overdue.applicator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.ning.billing.account.api.Account;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.util.email.templates.TemplateEngine;

import com.google.inject.Inject;

public class OverdueEmailGenerator {

    private final TemplateEngine templateEngine;

    @Inject
    public OverdueEmailGenerator(final TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public <T extends Blockable> String generateEmail(final Account account, final BillingState<T> billingState,
                                                      final T overdueable, final OverdueState<T> nextOverdueState) throws IOException {
        final Map<String, Object> data = new HashMap<String, Object>();

        // TODO raw objects for now. We eventually should respect the account locale and support translations
        data.put("account", account);
        data.put("billingState", billingState);
        data.put("overdueable", overdueable);
        data.put("nextOverdueState", nextOverdueState);

        // TODO single template for all languages for now
        return templateEngine.executeTemplate(nextOverdueState.getEnterStateEmailNotification().getTemplateName(), data);
    }
}
