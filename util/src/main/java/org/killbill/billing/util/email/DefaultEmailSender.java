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

package org.killbill.billing.util.email;

import java.io.IOException;
import java.util.List;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.ErrorCode;

import com.google.inject.Inject;

public class DefaultEmailSender implements EmailSender {

    private final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private final EmailConfig config;

    @Inject
    public DefaultEmailSender(final EmailConfig emailConfig) {
        this.config = emailConfig;
    }

    @Override
    public void sendHTMLEmail(final List<String> to, final List<String> cc, final String subject, final String htmlBody) throws EmailApiException {
        final HtmlEmail email = new HtmlEmail();
        try {
            email.setHtmlMsg(htmlBody);
        } catch (EmailException e) {
            throw new EmailApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        sendEmail(to, cc, subject, email);
    }

    @Override
    public void sendPlainTextEmail(final List<String> to, final List<String> cc, final String subject, final String body) throws IOException, EmailApiException {
        final SimpleEmail email = new SimpleEmail();
        try {
            email.setMsg(body);
        } catch (EmailException e) {
            throw new EmailApiException(e, ErrorCode.EMAIL_SENDING_FAILED);
        }

        sendEmail(to, cc, subject, email);
    }

    private void sendEmail(final List<String> to, final List<String> cc, final String subject, final Email email) throws EmailApiException {
        try {
            email.setSmtpPort(config.getSmtpPort());
            if (config.useSmtpAuth()) {
                email.setAuthentication(config.getSmtpUserName(), config.getSmtpPassword());
            }
            email.setHostName(config.getSmtpServerName());
            email.setFrom(config.getDefaultFrom());

            email.setSubject(subject);

            if (to != null) {
                for (final String recipient : to) {
                    email.addTo(recipient);
                }
            }

            if (cc != null) {
                for (final String recipient : cc) {
                    email.addCc(recipient);
                }
            }

            email.setSSL(config.useSSL());

            log.info("Sending email to='{}', cc='{}', subject='{}'", to, cc, subject);
            email.send();
        } catch (EmailException ee) {
            throw new EmailApiException(ee, ErrorCode.EMAIL_SENDING_FAILED);
        }
    }
}
