/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

package org.killbill.billing.subscription.events.bcd;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestBCDEventData extends SubscriptionTestSuiteNoDB {

    private static final UUID DEFAULT_SUBS_ID = UUID.randomUUID();
    private static final DateTime NOW = DateTime.now();

    private BCDEventData newBCDEventData() {
        final BCDEventBuilder builder = new BCDEventBuilder()
                .setSubscriptionId(DEFAULT_SUBS_ID)
                .setEffectiveDate(NOW)
                .setBillCycleDayLocal(30);
        return new BCDEventData(builder);
    }

    @Test(groups = "fast")
    public void testEqualsAndHashcode() {
        final BCDEventData eventData1 = newBCDEventData();
        final BCDEventData eventData2 = newBCDEventData();

        // Equals, defined by org.killbill.billing.subscription.events.EventBase#equals()
        Assert.assertEquals(eventData1, eventData2);
        // Not equals because use java.lang.Object.hashCode()
        Assert.assertNotEquals(eventData1.hashCode(), eventData2.hashCode());

        final Collection<BCDEventData> eventDataSet = new HashSet<>();
        eventDataSet.add(eventData1);
        eventDataSet.add(eventData2);

        // HashSet, HashMap, and any hash based implementation use 2 steps:
        // 1. Use equals to compare elements/keys. If not equals, then add them.
        // 2. If 2 elements/keys are equals, it will use #hashCode() to compare them.
        // In this case, eventData1 and eventData2 are #equals(), but the #hashCode() values are different, thus
        // eventDataSet.size() == 2
        Assert.assertEquals(eventDataSet.size(), 2);
    }
}
