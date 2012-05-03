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

import com.ning.billing.config.KillbillConfig;
import org.skife.config.Config;
import org.skife.config.Default;

import java.util.Locale;

public interface EmailConfig extends KillbillConfig {
    @Config("mail.smtp.host")
    @Default("smtp.gmail.com")
    public String getSmtpServerName();

    @Config("mail.smtp.port")
    @Default("465")
    public int getSmtpPort();

    @Config("mail.smtp.user")
    @Default("killbill.ning@gmail.com")
    public String getSmtpUserName();

    @Config("mail.smtp.password")
    @Default("killbill@ning!")
    public String getSmtpPassword();
}
