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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestCreditJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final BigDecimal creditAmount = BigDecimal.TEN;
        final String invoiceId = UUID.randomUUID().toString();
        final String invoiceNumber = UUID.randomUUID().toString();
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final String reason = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final CreditJson creditJson = new CreditJson(creditAmount, invoiceId, invoiceNumber, requestedDate, effectiveDate,
                                                     reason, accountId, auditLogs);
        Assert.assertEquals(creditJson.getRequestedDate(), requestedDate);
        Assert.assertEquals(creditJson.getEffectiveDate(), effectiveDate);
        Assert.assertEquals(creditJson.getCreditAmount(), creditAmount);
        Assert.assertEquals(creditJson.getInvoiceId(), invoiceId);
        Assert.assertEquals(creditJson.getInvoiceNumber(), invoiceNumber);
        Assert.assertEquals(creditJson.getReason(), reason);
        Assert.assertEquals(creditJson.getAccountId(), accountId);

        final String asJson = mapper.writeValueAsString(creditJson);
        final CreditJson fromJson = mapper.readValue(asJson, CreditJson.class);
        Assert.assertEquals(fromJson, creditJson);
    }
}
