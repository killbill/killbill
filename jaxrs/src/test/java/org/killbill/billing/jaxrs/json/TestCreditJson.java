/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestCreditJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final UUID creditId = UUID.randomUUID();
        final BigDecimal creditAmount = BigDecimal.TEN;
        final Currency currency = Currency.AED;
        final UUID invoiceId = UUID.randomUUID();
        final String invoiceNumber = UUID.randomUUID().toString();
        final LocalDate effectiveDate = clock.getUTCToday();
        final UUID accountId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final CreditJson creditJson = new CreditJson(creditId, creditAmount, currency, invoiceId, invoiceNumber, effectiveDate,
                                                     accountId, null, null, auditLogs);
        Assert.assertEquals(creditJson.getCreditId(), creditId);
        Assert.assertEquals(creditJson.getEffectiveDate(), effectiveDate);
        Assert.assertEquals(creditJson.getCreditAmount(), creditAmount);
        Assert.assertEquals(creditJson.getInvoiceId(), invoiceId);
        Assert.assertEquals(creditJson.getInvoiceNumber(), invoiceNumber);
        Assert.assertEquals(creditJson.getAccountId(), accountId);

        final String asJson = mapper.writeValueAsString(creditJson);
        final CreditJson fromJson = mapper.readValue(asJson, CreditJson.class);
        Assert.assertEquals(fromJson, creditJson);
    }
}
