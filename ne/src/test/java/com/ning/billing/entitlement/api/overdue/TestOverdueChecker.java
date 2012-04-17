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


public class TestOverdueChecker {
//
//
//    private static final String DISABLED_AND_BLOCKED_BUNDLE = "disabled-blocked-bundle";
//    private static final String DISABLED_BUNDLE = "disabled-bundle";
//    private static final String BLOCKED_BUNDLE = "blocked-bundle";
//    private static final String CLEAR_BUNDLE = "clear-bundle";
//    
//        
//    private static final DefaultOverdueState<SubscriptionBundle> CLEAR_BUNDLE_STATE = new MockOverdueState<SubscriptionBundle>(CLEAR_BUNDLE, false, false); 
//    private static final DefaultOverdueState<SubscriptionBundle> BLOCKED_BUNDLE_STATE = new MockOverdueState<SubscriptionBundle>(BLOCKED_BUNDLE, true, false);
//    private static final DefaultOverdueState<SubscriptionBundle> DISABLED_BUNDLE_STATE = new MockOverdueState<SubscriptionBundle>(DISABLED_BUNDLE, false, true);
//    private static final DefaultOverdueState<SubscriptionBundle> DISABLED_AND_BLOCKED_BUNDLE_STATE = new MockOverdueState<SubscriptionBundle>(DISABLED_AND_BLOCKED_BUNDLE, true, true) ;
//   
//    
//    private OverdueChecker checker;
//    private OverdueAccessApi overdueAccessApi;    
//    private Subscription subscription;
//    private SubscriptionBundle bundle;
//    
//    @BeforeClass(groups={"fast"})
//    public void setup() {
//        overdueAccessApi = BrainDeadProxyFactory.createBrainDeadProxyFor(OverdueAccessApi.class);
//        subscription = BrainDeadProxyFactory.createBrainDeadProxyFor(Subscription.class);
//        bundle = BrainDeadProxyFactory.createBrainDeadProxyFor(SubscriptionBundle.class);
//        ((ZombieControl) bundle).addResult("getAccountId", new UUID(0L,0L));
//        ((ZombieControl) bundle).addResult("getId", new UUID(0L,0L));
//        ((ZombieControl) bundle).addResult("getKey", "key");
//        ((ZombieControl) subscription).addResult("getBundleId", new UUID(0L,0L));
//       
//        @SuppressWarnings("unchecked")
//        final OverdueStatesBundle bundleODS =  new MockOverdueStatesBundle(new DefaultOverdueState[] {
//                CLEAR_BUNDLE_STATE,BLOCKED_BUNDLE_STATE, DISABLED_BUNDLE_STATE, DISABLED_AND_BLOCKED_BUNDLE_STATE
//        });
//
//        Injector i = Guice.createInjector(new AbstractModule() {
//
//            @Override
//            protected void configure() {
//                bind(OverdueChecker.class).to(DefaultOverdueChecker.class).asEagerSingleton();
//                CatalogService catalogService = BrainDeadProxyFactory.createBrainDeadProxyFor(CatalogService.class);
//                ((ZombieControl) catalogService).addResult("getCurrentCatalog", new MockCatalog() {
//
//                    @Override
//                    public void setOverdueRules() {
//                         OverdueRules overdueRules = new MockOverdueRules().setOverdueStatesBundle(bundleODS);                       
//                        setOverdueRules(overdueRules);  
//                    }
//                    
//                });
//                bind(CatalogService.class).toInstance(catalogService);
//                
//               
//                bind(OverdueAccessDao.class).toInstance(BrainDeadProxyFactory.createBrainDeadProxyFor(OverdueAccessDao.class));
//                bind(OverdueAccessApi.class).toInstance(overdueAccessApi);
//                
//                
//                EntitlementDao entitlementDao = BrainDeadProxyFactory.createBrainDeadProxyFor(EntitlementDao.class);
//                //((ZombieControl) entitlementDao).addResult("", result)
//                bind(EntitlementDao.class).toInstance(entitlementDao);
//                ((ZombieControl) entitlementDao).addResult("getSubscriptionBundleFromId",bundle);
//                
//            }
//            
//        });
//        checker = i.getInstance(OverdueChecker.class);
//    }
//
//
//    private void setStateBundle(OverdueState<SubscriptionBundle> state) {
//        ((ZombieControl) bundle).addResult("getOverdueState", state);
//    }
//
//    @Test(groups={"fast"}, enabled = true)
//    public void testSubscriptionChecker() throws EntitlementUserApiException {
//        setStateBundle(CLEAR_BUNDLE_STATE);
//        checker.checkBlocked(bundle);
//
//        //BLOCKED BUNDLE
//        try {
//            setStateBundle(BLOCKED_BUNDLE_STATE);
//            checker.checkBlocked(subscription);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
//        
//        //DISABLED BUNDLE
//        try {
//            setStateBundle(DISABLED_BUNDLE_STATE);
//            checker.checkBlocked(subscription);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
// 
//        try {
//            setStateBundle(DISABLED_AND_BLOCKED_BUNDLE_STATE);
//            checker.checkBlocked(subscription);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
//        
//         setStateBundle(CLEAR_BUNDLE_STATE);
//        checker.checkBlocked(subscription);
//        
//    }
//    
//    @Test(groups={"fast"}, enabled = true)
//    public void testBundleChecker() throws EntitlementUserApiException {
//        setStateBundle(CLEAR_BUNDLE_STATE);
//        checker.checkBlocked(bundle);
//
// 
//        //BLOCKED BUNDLE
//        try {
//            setStateBundle(BLOCKED_BUNDLE_STATE);
//            checker.checkBlocked(bundle);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
//        
//       
//        //DISABLED BUNDLE
//        try {
//            setStateBundle(DISABLED_BUNDLE_STATE);
//            checker.checkBlocked(bundle);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
//
//        //BLOCKED AND DISABLED BUNDLE
//        try {
//            setStateBundle(DISABLED_AND_BLOCKED_BUNDLE_STATE);
//             checker.checkBlocked(bundle);
//            Assert.fail("The call should have been blocked!");
//        } catch (EntitlementUserApiException e) {
//            //Expected behavior
//        }
//
//        setStateBundle(CLEAR_BUNDLE_STATE);
//        checker.checkBlocked(bundle);
//        
//    }
//    
//
//     
}
