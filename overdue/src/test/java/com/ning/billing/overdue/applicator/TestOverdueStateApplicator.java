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


import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueTestBase;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.config.XMLLoader;

public class TestOverdueStateApplicator extends OverdueTestBase {
    @Inject
    OverdueStateApplicator<SubscriptionBundle> applicator;

    @Inject
    OverdueBusListenerTester listener;
    
    @Inject 
    Bus bus;
    

    @Test(groups = {"slow"}, enabled = true)
    public void testApplicator() throws Exception {
        bus.register(listener);
        bus.start();
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        final SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl) bundle).addResult("getId", UUID.randomUUID());

        OverdueState<SubscriptionBundle> state;

        state = config.getBundleStateSet().findState("OD1");
        applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
        checkStateApplied(state);
//        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
//            @Override
//            public Boolean call() throws Exception {
//                return listener.getEventsReceived().size() == 1;
//            }
//        });
        


        state = config.getBundleStateSet().findState("OD2");
        applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
        checkStateApplied(state);

        state = config.getBundleStateSet().findState("OD3");
        applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
        checkStateApplied(state);

    }


}
