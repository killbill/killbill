/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.overdue.applicator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueTestSuiteWithEmbeddedDB;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.util.config.catalog.XMLLoader;
import com.ning.billing.util.events.OverdueChangeInternalEvent;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

public class TestOverdueStateApplicator extends OverdueTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testApplicator() throws Exception {
        final InputStream is = new ByteArrayInputStream(testOverdueHelper.getConfigXml().getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(UUID.randomUUID());

        OverdueState<SubscriptionBundle> state;

        state = config.getBundleStateSet().findState("OD1");
        applicator.apply(null, null, bundle, DefaultBlockingState.CLEAR_STATE_NAME, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD1");

        state = config.getBundleStateSet().findState("OD2");
        applicator.apply(null, null, bundle, DefaultBlockingState.CLEAR_STATE_NAME, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD2");

        state = config.getBundleStateSet().findState("OD3");
        applicator.apply(null, null, bundle, DefaultBlockingState.CLEAR_STATE_NAME, state, internalCallContext);
        testOverdueHelper.checkStateApplied(state);
        checkBussEvent("OD3");
    }

    private void checkBussEvent(final String state) throws Exception {
        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final List<OverdueChangeInternalEvent> events = listener.getEventsReceived();
                return events.size() == 1;
            }
        });
        final List<OverdueChangeInternalEvent> events = listener.getEventsReceived();
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(state, events.get(0).getNextOverdueStateName());
        listener.clearEventsReceived();
    }
}
