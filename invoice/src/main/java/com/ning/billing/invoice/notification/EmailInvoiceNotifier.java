/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.notification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.template.HtmlInvoiceGenerator;
import com.ning.billing.util.email.DefaultEmailSender;
import com.ning.billing.util.email.EmailApiException;
import com.ning.billing.util.email.EmailConfig;
import com.ning.billing.util.email.EmailSender;

public class EmailInvoiceNotifier implements InvoiceNotifier {
    private final AccountUserApi accountUserApi;
    private final HtmlInvoiceGenerator generator;
    private final EmailConfig config;

    @Inject
    public EmailInvoiceNotifier(final AccountUserApi accountUserApi, final HtmlInvoiceGenerator generator, final EmailConfig config) {
        this.accountUserApi = accountUserApi;
        this.generator = generator;
        this.config = config;
    }

    @Override
    public void notify(final Account account, final Invoice invoice) throws InvoiceApiException {
        final List<String> to = new ArrayList<String>();
        to.add(account.getEmail());

        final List<AccountEmail> accountEmailList = accountUserApi.getEmails(account.getId());
        final List<String> cc = new ArrayList<String>();
        for (final AccountEmail email : accountEmailList) {
            cc.add(email.getEmail());
        }

        final String htmlBody;
        try {
            htmlBody = generator.generateInvoice(account, invoice);
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        // TODO: get subject
        final String subject = "";

        final EmailSender sender = new DefaultEmailSender(config);
        try {
            sender.sendSecureEmail(to, cc, subject, htmlBody);
        } catch (EmailApiException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }
    }
}
