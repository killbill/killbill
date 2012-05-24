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
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockPaymentModule;
import com.ning.billing.mock.glue.TestDbiModule;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.OverdueTestBase;
import com.ning.billing.overdue.applicator.ApplicatorMockJunctionModule.ApplicatorBlockingApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.util.config.XMLLoader;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.NotificationQueueModule;

public class TestOverdueStateApplicator extends OverdueTestBase {
    @Inject
    OverdueStateApplicator<SubscriptionBundle> applicator;
        
    @Test( groups={"slow"} , enabled = true)
     public void testApplicator() throws Exception {
         InputStream is = new ByteArrayInputStream(configXml.getBytes());
         config = XMLLoader.getObjectFromStreamNoValidation(is,  OverdueConfig.class);
         overdueWrapperFactory.setOverdueConfig(config);
         
         SubscriptionBundle bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
         ((ZombieControl)bundle).addResult("getId", UUID.randomUUID());
         
         OverdueState<SubscriptionBundle> state;
         
         state = config.getBundleStateSet().findState("OD1");
         applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
         checkStateApplied(state);
         
        
         state = config.getBundleStateSet().findState("OD2");
         applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
         checkStateApplied(state);
        
         state = config.getBundleStateSet().findState("OD3");
         applicator.apply(bundle, BlockingApi.CLEAR_STATE_NAME, state);
         checkStateApplied(state);
        
    }


    
    
}
