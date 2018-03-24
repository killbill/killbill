/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AccountEmail;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAccountEmail extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can create and delete account emails")
    public void testAddAndRemoveAccountEmail() throws Exception {
        final Account input = createAccount();
        final UUID accountId = input.getAccountId();

        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();
        final AccountEmail accountEmailJson1 = new AccountEmail(accountId, email1, null);
        final AccountEmail accountEmailJson2 = new AccountEmail(accountId, email2, null);

        // Verify the initial state
        final List<AccountEmail> firstEmails = accountApi.getEmails(accountId, requestOptions);
        Assert.assertEquals(firstEmails.size(), 0);

        // Add an email
        accountApi.addEmail(accountEmailJson1, accountId, requestOptions);

        // Verify we can retrieve it
        final List<AccountEmail> secondEmails = accountApi.getEmails(accountId, requestOptions);
        Assert.assertEquals(secondEmails.size(), 1);
        Assert.assertEquals(secondEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(secondEmails.get(0).getEmail(), email1);

        // Add another email
        accountApi.addEmail(accountEmailJson2, accountId, requestOptions);

        // Verify we can retrieve both
        final List<AccountEmail> thirdEmails = accountApi.getEmails(accountId, requestOptions);
        Assert.assertEquals(thirdEmails.size(), 2);
        Assert.assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(thirdEmails.get(1).getAccountId(), accountId);
        Assert.assertTrue(thirdEmails.get(0).getEmail().equals(email1) || thirdEmails.get(0).getEmail().equals(email2));
        Assert.assertTrue(thirdEmails.get(1).getEmail().equals(email1) || thirdEmails.get(1).getEmail().equals(email2));

        // Delete the first email
        accountApi.removeEmail(accountId, accountEmailJson1.getEmail(), requestOptions);

        // Verify it has been deleted
        final List<AccountEmail> fourthEmails = accountApi.getEmails(accountId, requestOptions);
        Assert.assertEquals(fourthEmails.size(), 1);
        Assert.assertEquals(fourthEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(fourthEmails.get(0).getEmail(), email2);

        // Try to add the same email
        accountApi.addEmail(accountEmailJson2, accountId, requestOptions);
        Assert.assertEquals(accountApi.getEmails(accountId, requestOptions), fourthEmails);
    }
}
