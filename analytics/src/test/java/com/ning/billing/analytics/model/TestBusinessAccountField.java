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

package com.ning.billing.analytics.model;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;

public class TestBusinessAccountField extends AnalyticsTestSuite {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final BusinessAccountFieldModelDao accountField = new BusinessAccountFieldModelDao(accountId,
                                                                                           accountKey,
                                                                                           name,
                                                                                           value);
        Assert.assertSame(accountField, accountField);
        Assert.assertEquals(accountField, accountField);
        Assert.assertTrue(accountField.equals(accountField));
        Assert.assertEquals(accountField.getAccountId(), accountId);
        Assert.assertEquals(accountField.getAccountKey(), accountKey);
        Assert.assertEquals(accountField.getName(), name);
        Assert.assertEquals(accountField.getValue(), value);

        final BusinessAccountFieldModelDao otherAccountField = new BusinessAccountFieldModelDao(UUID.randomUUID(),
                                                                                                UUID.randomUUID().toString(),
                                                                                                UUID.randomUUID().toString(),
                                                                                                UUID.randomUUID().toString());
        Assert.assertFalse(accountField.equals(otherAccountField));
    }
}
