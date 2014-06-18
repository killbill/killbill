/*
 * Copyright 2010-2011 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.util.email;

import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test(groups = "slow")
public class EmailSenderTest extends UtilTestSuiteNoDB {

    private EmailConfig config;

    @BeforeClass
    public void beforeClass() throws Exception {
        super.beforeClass();
        config = new ConfigurationObjectFactory(skifeConfigSource).build(EmailConfig.class);
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
