/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.invoice.notification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.template.HtmlInvoice;
import org.killbill.billing.invoice.template.HtmlInvoiceGenerator;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.email.DefaultEmailSender;
import org.killbill.billing.util.email.EmailApiException;
import org.killbill.billing.util.email.EmailConfig;
import org.killbill.billing.util.email.EmailSender;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;

import com.google.common.base.Strings;
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
        if (Strings.emptyToNull(account.getEmail()) == null) {
            throw new InvoiceApiException(new IllegalArgumentException("Email for account " + account.getId() + " not specified"), ErrorCode.EMAIL_SENDING_FAILED);
        }

        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(account.getId(), context);
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

        final HtmlInvoice htmlInvoice;
        try {
            htmlInvoice = generator.generateInvoice(account, invoice, manualPay, internalTenantContext);
        } catch (final IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        // take localized subject, or the configured one if the localized one is not available
        String subject = htmlInvoice.getSubject();
        if (subject == null) {
            subject = config.getInvoiceEmailSubject();
        }

        final EmailSender sender = new DefaultEmailSender(config);
        try {
            sender.sendHTMLEmail(to, cc, subject, htmlInvoice.getBody());
        } catch (final EmailApiException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        } catch (final IOException e) {
            throw new InvoiceApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }
    }
}
