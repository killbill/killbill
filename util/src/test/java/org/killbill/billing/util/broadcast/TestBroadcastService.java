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

import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.UtilTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.broadcast.dao.BroadcastModelDao;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

public class TestBroadcastService extends UtilTestSuiteWithEmbeddedDB {

    @Inject
    private BroadcastService broadcastService;

    @Override
    protected KillbillConfigSource getConfigSource() {
            return getConfigSource(null,
                               ImmutableMap.<String, String>of("org.killbill.billing.util.broadcast.rate", "500ms"));
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        ((DefaultBroadcastService) broadcastService).initialize();
        ((DefaultBroadcastService) broadcastService).start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        ((DefaultBroadcastService) broadcastService).stop();
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void testBasic() {
        final String eventJson = "\"{\"pluginName\":\"foo\",\"pluginVersion\":\"1.2.3\",\"properties\":[{\"key\":\"something\",\"value\":\"nothing\"}]}\"";

        eventsListener.pushExpectedEvent(NextEvent.BROADCAST_SERVICE);
        broadcastDao.create(new BroadcastModelDao("svc", "type", eventJson, clock.getUTCNow(), "tester"));
        assertListenerStatus();
    }

}
