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

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.template.HtmlInvoiceGenerator;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.email.DefaultEmailSender;
import com.ning.billing.util.email.EmailApiException;
import com.ning.billing.util.email.EmailConfig;
import com.ning.billing.util.email.EmailSender;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

import com.google.inject.Inject;

public class EmailInvoiceNotifier implements InvoiceNotifier {

    private final AccountInternalApi accountApi;
    private final TagInternalApi tagUserApi;
    private final HtmlInvoiceGenerator generator;
    private final EmailConfig config;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public EmailInvoiceNotifier(final AccountInternalApi accountApi,
                                final TagInternalApi tagUserApi,
                                final HtmlInvoiceGenerator generator,
                                final EmailConfig config,
                                final InternalCallContextFactory internalCallContextFactory) {
        this.accountApi = accountApi;
        this.tagUserApi = tagUserApi;
        this.generator = generator;
        this.config = config;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void notify(final Account account, final Invoice invoice, final TenantContext context) throws InvoiceApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final List<String> to = new ArrayList<String>();
        to.add(account.getEmail());

        final List<AccountEmail> accountEmailList = accountApi.getEmails(account.getId(), internalTenantContext);
        final List<String> cc = new ArrayList<String>();
        for (final AccountEmail email : accountEmailList) {
            cc.add(email.getEmail());
        }

        // Check if this account has the MANUAL_PAY system tag
        boolean manualPay = false;
        final List<Tag> accountTags = tagUserApi.getTags(account.getId(), ObjectType.ACCOUNT, internalTenantContext);
        for (final Tag tag : accountTags) {
            if (ControlTagType.MANUAL_PAY.getId().equals(tag.getTagDefinitionId())) {
                manualPay = true;
                break;
            }
        }

        final String htmlBody;
        try {
            htmlBody = generator.generateInvoice(account, invoice, manualPay);
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        final String subject = config.getInvoiceEmailSubject();

        final EmailSender sender = new DefaultEmailSender(config);
        try {
            sender.sendHTMLEmail(to, cc, subject, htmlBody);
        } catch (EmailApiException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        } catch (IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }
    }
}
