package org.killbill.billing.util.email;/*
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

import java.util.ArrayList;
import java.util.List;

import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.killbill.billing.util.UtilTestSuiteNoDB;

@Test(groups = "slow")
public class EmailSenderTest extends UtilTestSuiteNoDB {

    private EmailConfig config;

    @BeforeClass
    public void beforeClass() throws Exception {
        super.beforeClass();
        config = new ConfigurationObjectFactory(System.getProperties()).build(EmailConfig.class);
    }

    @Test(enabled = false)
    public void testSendEmail() throws Exception {
        final String html = "<html><body><h1>Test E-mail</h1></body></html>";
        final List<String> recipients = new ArrayList<String>();
        recipients.add("killbill.ning@gmail.com");

        final EmailSender sender = new DefaultEmailSender(config);
        sender.sendHTMLEmail(recipients, null, "Test message", html);
    }
}
