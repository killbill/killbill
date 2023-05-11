/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.broadcast;

import java.util.Collection;
import java.util.HashSet;

import org.killbill.billing.events.BroadcastInternalEvent;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.bus.api.BusEvent;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class TestDefaultBroadcastInternalEvent extends UtilTestSuiteNoDB {


    @Test(groups = "fast")
    public void testBasic() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JodaModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final String eventJson = "\"{\"pluginName\":\"foo\",\"pluginVersion\":\"1.2.3\",\"properties\":[{\"key\":\"something\",\"value\":\"nothing\"}]}\"";

        final BroadcastInternalEvent broadcastEvent = new DefaultBroadcastInternalEvent("service", "PLUGIN_INSTALL", eventJson);

        final String broadcastEventStr = objectMapper.writeValueAsString(broadcastEvent);

        final BroadcastInternalEvent res = objectMapper.readValue(broadcastEventStr, DefaultBroadcastInternalEvent.class);

        Assert.assertEquals(res.getServiceName(), "service");
        Assert.assertEquals(res.getType(), "PLUGIN_INSTALL");
        Assert.assertEquals(res.getJsonEvent(), eventJson);
    }

    // This test created especially to make sure that it still behave the same after adding equals() and hashCode()
    // to DefaultBroadcastInternalEvent.
    @Test(groups = "fast")
    public void testEquality() {
        // see DefaultBroadcastInternalEvent() default constructor
        final BusEventBase eventBaseAttrsInBroadcastInternal = new BusEventBase(null, 0L, null);

        final BroadcastInternalEvent broadcast1 = new DefaultBroadcastInternalEvent("service", "PLUGIN_INSTALL", "eventJson");
        final BroadcastInternalEvent broadcast2 = new DefaultBroadcastInternalEvent();

        Assert.assertEquals(eventBaseAttrsInBroadcastInternal, broadcast1);
        Assert.assertEquals(broadcast1, eventBaseAttrsInBroadcastInternal); // For symmetricity of equals.

        Assert.assertEquals(broadcast1, broadcast2);

        final Collection<BroadcastInternalEvent> broadcastInternal = new HashSet<>();
        broadcastInternal.add(broadcast1);
        broadcastInternal.add(broadcast2);
        Assert.assertEquals(broadcastInternal.size(), 1);

        final Collection<BusEvent> events = new HashSet<>();
        events.add(eventBaseAttrsInBroadcastInternal);
        events.add(broadcast1);
        events.add(broadcast2);
        Assert.assertEquals(events.size(), 1);
    }
}
