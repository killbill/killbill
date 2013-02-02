/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.jaxrs;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.AccountEmailJson;
import com.ning.billing.jaxrs.json.AccountJson;

public class TestAccountEmail extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testAddAndRemoveAccountEmail() throws Exception {
        final AccountJson input = createAccount(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        final String accountId = input.getAccountId();

        final String email1 = UUID.randomUUID().toString();
        final String email2 = UUID.randomUUID().toString();
        final AccountEmailJson accountEmailJson1 = new AccountEmailJson(accountId, email1);
        final AccountEmailJson accountEmailJson2 = new AccountEmailJson(accountId, email2);

        // Verify the initial state
        final List<AccountEmailJson> firstEmails = getEmailsForAccount(accountId);
        Assert.assertEquals(firstEmails.size(), 0);

        // Add an email
        addEmailToAccount(accountId, accountEmailJson1);

        // Verify we can retrieve it
        final List<AccountEmailJson> secondEmails = getEmailsForAccount(accountId);
        Assert.assertEquals(secondEmails.size(), 1);
        Assert.assertEquals(secondEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(secondEmails.get(0).getEmail(), email1);

        // Add another email
        addEmailToAccount(accountId, accountEmailJson2);

        // Verify we can retrieve both
        final List<AccountEmailJson> thirdEmails = getEmailsForAccount(accountId);
        Assert.assertEquals(thirdEmails.size(), 2);
        Assert.assertEquals(thirdEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(thirdEmails.get(1).getAccountId(), accountId);
        Assert.assertTrue(thirdEmails.get(0).getEmail().equals(email1) || thirdEmails.get(0).getEmail().equals(email2));
        Assert.assertTrue(thirdEmails.get(1).getEmail().equals(email1) || thirdEmails.get(1).getEmail().equals(email2));

        // Delete the first email
        removeEmailFromAccount(accountId, email1);

        // Verify it has been deleted
        final List<AccountEmailJson> fourthEmails = getEmailsForAccount(accountId);
        Assert.assertEquals(fourthEmails.size(), 1);
        Assert.assertEquals(fourthEmails.get(0).getAccountId(), accountId);
        Assert.assertEquals(fourthEmails.get(0).getEmail(), email2);

        // Try to add the same email -- that works because we removed the unique constraints for soft deletion.
        // addEmailToAccount(accountId, accountEmailJson2);
        Assert.assertEquals(getEmailsForAccount(accountId), fourthEmails);
    }
}
