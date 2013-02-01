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
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestChargebackCollectionJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final DateTime requestedDate = new DateTime(DateTimeZone.UTC);
        final DateTime effectiveDate = new DateTime(DateTimeZone.UTC);
        final BigDecimal chargebackAmount = BigDecimal.TEN;
        final String paymentId = UUID.randomUUID().toString();
        final String reason = UUID.randomUUID().toString();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final ChargebackJson chargebackJson = new ChargebackJson(requestedDate, effectiveDate, chargebackAmount, paymentId,
                                                                 reason, auditLogs);

        final String accountId = UUID.randomUUID().toString();
        final ChargebackCollectionJson chargebackCollectionJson = new ChargebackCollectionJson(accountId, ImmutableList.<ChargebackJson>of(chargebackJson));
        Assert.assertEquals(chargebackCollectionJson.getAccountId(), accountId);
        Assert.assertEquals(chargebackCollectionJson.getChargebacks().size(), 1);
        Assert.assertEquals(chargebackCollectionJson.getChargebacks().get(0), chargebackJson);
        Assert.assertEquals(chargebackCollectionJson.getChargebacks().get(0).getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(chargebackCollectionJson);
        final ChargebackCollectionJson fromJson = mapper.readValue(asJson, ChargebackCollectionJson.class);
        Assert.assertEquals(fromJson, chargebackCollectionJson);
    }
}
