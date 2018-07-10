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

package org.killbill.billing.account.api.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.account.AccountTestSuiteNoDB;
import org.killbill.billing.account.api.DefaultChangedField;
import org.killbill.billing.account.api.user.DefaultAccountCreationEvent.DefaultAccountData;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.events.ChangedField;
import org.killbill.billing.util.jackson.ObjectMapper;

public class TestEventJson extends AccountTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast", description="Test Account event deserialization")
    public void testDefaultAccountChangeEvent() throws Exception {
        final List<ChangedField> changes = new ArrayList<ChangedField>();
        changes.add(new DefaultChangedField("fieldXX", "valueX", "valueXXX", clock.getUTCNow()));
        changes.add(new DefaultChangedField("fieldYY", "valueY", "valueYYY", clock.getUTCNow()));
        final AccountChangeInternalEvent e = new DefaultAccountChangeEvent(changes, UUID.randomUUID(), 1L, 2L, null);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName("org.killbill.billing.account.api.user.DefaultAccountChangeEvent");
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups = "fast", description="Test Account event serialization")
    public void testAccountCreationEvent() throws Exception {
        final DefaultAccountData data = new DefaultAccountData("dsfdsf", "bobo", 3, "bobo@yahoo.com", 12, "USD", null, false, UUID.randomUUID(),
                                                               new DateTime().toString(), "UTC", "US", "21 avenue", "", "Gling", "San Franciso", "CA", "94110", "USA", "4126789887", "notes", false);
        final DefaultAccountCreationEvent e = new DefaultAccountCreationEvent(data, UUID.randomUUID(), 1L, 2L, null);
        final String json = mapper.writeValueAsString(e);

        final DefaultAccountCreationEvent obj = mapper.readValue(json, DefaultAccountCreationEvent.class);
        Assert.assertTrue(obj.equals(e));
    }
}
