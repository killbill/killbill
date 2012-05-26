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

package com.ning.billing.junction.blocking;

import java.util.SortedSet;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.junction.block.DefaultBlockingChecker;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.mock.BrainDeadProxyFactory.ZombieControl;
import com.ning.billing.util.clock.Clock;

public class TestBlockingChecker {
   
    private BlockingState bundleState;
    private BlockingState subscriptionState;
    private BlockingState accountState;
    
    private BlockingStateDao dao = new BlockingStateDao() {

        @Override
        public BlockingState getBlockingStateFor(Blockable blockable) {
            if(blockable.getId() == account.getId()) {
                return accountState;
            } else  if(blockable.getId() == subscription.getId()) {
                return subscriptionState;
            } else {
                return bundleState;
            }
        }

        @Override
        public BlockingState getBlockingStateFor(UUID blockableId) {
            if(blockableId == account.getId()) {
                return accountState;
            } else  if(blockableId == subscription.getId()) {
                return subscriptionState;
            } else {
                return bundleState;
            }
        }

        @Override
        public SortedSet<BlockingState> getBlockingHistoryFor(Blockable overdueable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SortedSet<BlockingState> getBlockingHistoryFor(UUID overdueableId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Blockable> void setBlockingState(BlockingState state, Clock clock) {
            throw new UnsupportedOperationException();
        }
        
    };
    private BlockingChecker checker;
    private Subscription subscription;
    private Account account;
    private SubscriptionBundle bundle;
    
    @BeforeClass(groups={"fast"})
    public void setup() {
        account = BrainDeadProxyFactory.createBrainDeadProxyFor(Account.class);
        ((ZombieControl) account).addResult("getId", UUID.randomUUID());
        
        bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
        ((ZombieControl) bundle).addResult("getAccountId", account.getId());
        ((ZombieControl) bundle).addResult("getId", UUID.randomUUID());
        ((ZombieControl) bundle).addResult("getKey", "key");

        subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
        ((ZombieControl) subscription).addResult("getId", UUID.randomUUID());
        ((ZombieControl) subscription).addResult("getBundleId", bundle.getId());

        
        Injector i = Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                bind(BlockingChecker.class).to(DefaultBlockingChecker.class).asEagerSingleton();
                
                bind(BlockingStateDao.class).toInstance(dao);             
                
                EntitlementUserApi entitlementUserApi = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementUserApi.class);
                bind(EntitlementUserApi.class).toInstance(entitlementUserApi);
                ((ZombieControl) entitlementUserApi).addResult("getBundleFromId",bundle);
                
            }
            
        });
        checker = i.getInstance(BlockingChecker.class);
    }


    private void setStateBundle(boolean bC, boolean bE, boolean bB) {
        bundleState = new DefaultBlockingState(UUID.randomUUID(), "state", Blockable.Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE,bB);
        ((ZombieControl) bundle).addResult("getBlockingState", bundleState);
    }

    private void setStateAccount(boolean bC, boolean bE, boolean bB) {
        accountState = new DefaultBlockingState(UUID.randomUUID(), "state", Blockable.Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE,bB);
    }

    private void setStateSubscription(boolean bC, boolean bE, boolean bB) {
        subscriptionState = new DefaultBlockingState(UUID.randomUUID(), "state", Blockable.Type.SUBSCRIPTION_BUNDLE, "test-service", bC, bE,bB);
        ((ZombieControl) subscription).addResult("getBlockingState", subscriptionState);
    }

    @Test(groups={"fast"}, enabled = true)
    public void testSubscriptionChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedEntitlement(subscription);
        checker.checkBlockedBilling(subscription);
        
        //BLOCKED SUBSCRIPTION
        setStateSubscription(true, false, false);
        checker.checkBlockedEntitlement(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedChange(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateSubscription(false, true, false);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedEntitlement(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateSubscription(false, false, true);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedEntitlement(subscription);
        try {
           checker.checkBlockedBilling(subscription);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }

        //BLOCKED BUNDLE
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        checker.checkBlockedEntitlement(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedChange(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateBundle(false, true, false);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedEntitlement(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateBundle(false, false, true);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedEntitlement(subscription);
        try {
           checker.checkBlockedBilling(subscription);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        
        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        checker.checkBlockedEntitlement(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedChange(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, true, false);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedBilling(subscription);
        try {
            checker.checkBlockedEntitlement(subscription);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, false, true);
        checker.checkBlockedChange(subscription);
        checker.checkBlockedEntitlement(subscription);
        try {
           checker.checkBlockedBilling(subscription);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        
    }
    
    @Test(groups={"fast"}, enabled = true)
    public void testBundleChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        checker.checkBlockedChange(bundle);
        checker.checkBlockedEntitlement(bundle);
        checker.checkBlockedBilling(bundle);

        //BLOCKED BUNDLE
        setStateSubscription(false, false, false);
        setStateBundle(true, false, false);
        checker.checkBlockedEntitlement(bundle);
        checker.checkBlockedBilling(bundle);
        try {
            checker.checkBlockedChange(bundle);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateBundle(false, true, false);
        checker.checkBlockedChange(bundle);
        checker.checkBlockedBilling(bundle);
        try {
            checker.checkBlockedEntitlement(bundle);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateBundle(false, false, true);
        checker.checkBlockedChange(bundle);
        checker.checkBlockedEntitlement(bundle);
        try {
           checker.checkBlockedBilling(bundle);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        
        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        checker.checkBlockedEntitlement(bundle);
        checker.checkBlockedBilling(bundle);
        try {
            checker.checkBlockedChange(bundle);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, true, false);
        checker.checkBlockedChange(bundle);
        checker.checkBlockedBilling(bundle);
        try {
            checker.checkBlockedEntitlement(bundle);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, false, true);
        checker.checkBlockedChange(bundle);
        checker.checkBlockedEntitlement(bundle);
        try {
           checker.checkBlockedBilling(bundle);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
  
    }
    
    @Test(groups={"fast"}, enabled = true)
    public void testAccountChecker() throws Exception {
        setStateAccount(false, false, false);
        setStateBundle(false, false, false);
        setStateSubscription(false, false, false);
        checker.checkBlockedChange(account);
        checker.checkBlockedEntitlement(account);
        checker.checkBlockedBilling(account);

        //BLOCKED ACCOUNT
        setStateSubscription(false, false, false);
        setStateBundle(false, false, false);
        setStateAccount(true, false, false);
        checker.checkBlockedEntitlement(account);
        checker.checkBlockedBilling(account);
        try {
            checker.checkBlockedChange(account);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, true, false);
        checker.checkBlockedChange(account);
        checker.checkBlockedBilling(account);
        try {
            checker.checkBlockedEntitlement(account);
            Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        
        setStateAccount(false, false, true);
        checker.checkBlockedChange(account);
        checker.checkBlockedEntitlement(account);
        try {
           checker.checkBlockedBilling(account);
           Assert.fail("The call should have been blocked!");
        } catch (BlockingApiException e) {
            //Expected behavior
        }
        

        
    }
    

     
}
