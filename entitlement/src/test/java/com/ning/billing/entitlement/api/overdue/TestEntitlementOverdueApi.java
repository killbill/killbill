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

package com.ning.billing.entitlement.api.overdue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.MockPlan;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;

public class TestEntitlementOverdueApi {
    
    @Test(groups={"fast"}, enabled=true)
    public void testGetBasePlan() {
        Plan basePlan = MockPlan.createBicycleNoTrialEvergreen1USD();
        Plan addonPlan = MockPlan.createHornMonthlyNoTrial1USD();
        
        List<Subscription> subs = new ArrayList<Subscription>();
        Subscription s1 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        Subscription s2 = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl)s1).addResult("getCurrentPlan", addonPlan);
        ((ZombieControl)s2).addResult("getCurrentPlan", basePlan);
        
        subs.add(s1);
        subs.add(s2);
        
        EntitlementDao dao = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementDao.class);
        ((ZombieControl)dao).addResult("getSubscriptions", subs);
        
        EntitlementOverdueApi api = new DefaultEntitlementOverdueApi(dao);
        
        Assert.assertEquals(api.getBaseSubscription(new UUID(0L,0L)).getCurrentPlan(),s2.getCurrentPlan());
        // Note can't compare proxy objects directly because 'equals' is not implemented...
    }

}
