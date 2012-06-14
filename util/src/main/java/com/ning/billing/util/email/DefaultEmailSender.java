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

package com.ning.billing.util.email;

import java.util.List;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class DefaultEmailSender implements EmailSender {
    private final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private final EmailConfig config;

    @Inject
    public DefaultEmailSender(final EmailConfig emailConfig) {
        this.config = emailConfig;
    }

    @Override
    public void sendSecureEmail(final List<String> to, final List<String> cc, final String subject, final String htmlBody) throws EmailApiException {
        final HtmlEmail email;
        try {
            email = new HtmlEmail();

            email.setSmtpPort(config.getSmtpPort());
            email.setAuthentication(config.getSmtpUserName(), config.getSmtpPassword());
            email.setHostName(config.getSmtpServerName());
            email.setFrom(config.getSmtpUserName());
            email.setSubject(subject);
            email.setHtmlMsg(htmlBody);

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

            email.setSSL(true);
            email.send();
        } catch (EmailException ee) {
            log.warn("Failed to send e-mail", ee);
        }
    }
}
