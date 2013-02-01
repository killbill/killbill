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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestCreditCollectionJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();

        final BigDecimal creditAmount = BigDecimal.TEN;
        final String invoiceId = UUID.randomUUID().toString();
        final String invoiceNumber = UUID.randomUUID().toString();
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final String reason = UUID.randomUUID().toString();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final CreditJson creditJson = new CreditJson(creditAmount, invoiceId, invoiceNumber, requestedDate,
                                                     effectiveDate, reason, accountId, auditLogs);

        final CreditCollectionJson creditCollectionJson = new CreditCollectionJson(accountId, ImmutableList.<CreditJson>of(creditJson));
        Assert.assertEquals(creditCollectionJson.getAccountId(), accountId);
        Assert.assertEquals(creditCollectionJson.getCredits().size(), 1);
        Assert.assertEquals(creditCollectionJson.getCredits().get(0), creditJson);
        Assert.assertEquals(creditCollectionJson.getCredits().get(0).getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(creditCollectionJson);
        final CreditCollectionJson fromJson = mapper.readValue(asJson, CreditCollectionJson.class);
        Assert.assertEquals(fromJson, creditCollectionJson);
    }
}
