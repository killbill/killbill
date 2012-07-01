/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ning.billing.account.api.AccountEmail;

public class TestAccountEmailJson {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();

        final AccountEmailJson accountEmailJson = new AccountEmailJson(accountId, email);
        Assert.assertEquals(accountEmailJson.getAccountId(), accountId);
        Assert.assertEquals(accountEmailJson.getEmail(), email);

        final String asJson = mapper.writeValueAsString(accountEmailJson);
        Assert.assertEquals(asJson, "{\"accountId\":\"" + accountId + "\"," +
                "\"email\":\"" + email + "\"}");

        final AccountEmailJson fromJson = mapper.readValue(asJson, AccountEmailJson.class);
        Assert.assertEquals(fromJson, accountEmailJson);
    }

    @Test(groups = "fast")
    public void testToAccountEmail() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String email = UUID.randomUUID().toString();

        final AccountEmailJson accountEmailJson = new AccountEmailJson(accountId, email);
        Assert.assertEquals(accountEmailJson.getAccountId(), accountId);
        Assert.assertEquals(accountEmailJson.getEmail(), email);

        final AccountEmail accountEmail = accountEmailJson.toAccountEmail();
        Assert.assertEquals(accountEmail.getAccountId().toString(), accountId);
        Assert.assertEquals(accountEmail.getEmail(), email);
    }
}
