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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmailInvoiceNotifier implements InvoiceNotifier {
    private final AccountUserApi accountUserApi;
    private final HtmlInvoiceGenerator generator;
    private final EmailConfig config;

    @Inject
    public EmailInvoiceNotifier(AccountUserApi accountUserApi, HtmlInvoiceGenerator generator, EmailConfig config) {
        this.accountUserApi = accountUserApi;
        this.generator = generator;
        this.config = config;
    }

    @Override
    public void notify(Account account, Invoice invoice) throws InvoiceApiException {
        List<String> to = new ArrayList<String>();
        to.add(account.getEmail());

        List<AccountEmail> accountEmailList = accountUserApi.getEmails(account.getId());
        List<String> cc = new ArrayList<String>();
        for (AccountEmail email : accountEmailList) {
            cc.add(email.getEmail());
        }

        String htmlBody = null;
        try {
            htmlBody = generator.generateInvoice(account, invoice, "HtmlInvoiceTemplate");
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        // TODO: get subject
        String subject = "";

        EmailSender sender = new DefaultEmailSender(config);
        try {
            sender.sendSecureEmail(to, cc, subject, htmlBody);
        } catch (EmailApiException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }
    }
}
