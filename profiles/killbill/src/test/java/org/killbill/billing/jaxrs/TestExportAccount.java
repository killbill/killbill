/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import org.killbill.billing.client.model.gen.Account;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestExportAccount extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testExportAccount() throws Exception {
        final Account emptyAccount = new Account();
        final Account account = accountApi.createAccount(emptyAccount, requestOptions);
        final OutputStream outputStream = new ByteArrayOutputStream();
        final int statusCode = exportApi.exportDataForAccount(account.getAccountId(), outputStream, requestOptions);
        outputStream.flush();
        Assert.assertEquals(statusCode, 200);
    }
}
