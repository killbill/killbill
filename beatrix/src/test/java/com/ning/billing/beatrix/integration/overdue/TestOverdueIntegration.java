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

package com.ning.billing.beatrix.integration.overdue;

import static org.testng.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.integration.BeatrixModule;
import com.ning.billing.beatrix.integration.TestIntegrationBase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.XMLLoader;

@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestOverdueIntegration extends TestIntegrationBase {
    private final String configXml =  
            "<overdueConfig>" +
                    "   <bundleOverdueStates>" +
                    "       <state name=\"OD1\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>MONTHS</unit><number>1</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD1</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                    "       </state>" +
                    "       <state name=\"OD2\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>MONTHS</unit><number>2</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD1</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                    "       </state>" +
                    "   </bundleOverdueStates>" +
                    "</overdueConfig>";
    private OverdueConfig config; 
    
    @Inject
    private ClockMock clock;
    
    @Inject
    private MockPaymentProviderPlugin paymentPlugin;
    
    @Inject
    private BlockingApi blockingApi;
    
    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;
    
    @BeforeMethod(groups = {"slow"})
    public void setupOverdue() throws Exception {
        InputStream is = new ByteArrayInputStream(configXml.getBytes());
        config = XMLLoader.getObjectFromStreamNoValidation(is,  OverdueConfig.class);
        Account account = accountUserApi.createAccount(getAccountData(25), null, null, context);
        assertNotNull(account);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        
        // create account
        // set mock payments to fail
        // reset clock
        // configure basic OD state rules for 2 states OD1 1-2month, OD2 2-3 month
    }
    
    @AfterMethod
    public void cleanup(){
        // Clear databases
    }
    
    @Test(groups={"slow"}, enabled = true)
    public void testBasicOverdueState() throws Exception {
        clock.setTime(new DateTime(2012, 5, 1, 0, 3, 42, 0));
               
        // set next invoice to fail and create network 
        paymentPlugin.makeNextInvoiceFail();
        SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);

       // advance time 2weeks
       clock.addWeeks(2);
        
       // should still be in clear state
       BlockingState state = blockingApi.getBlockingStateFor(bundle);
       Assert.assertEquals(state.getStateName(), BlockingApi.CLEAR_STATE_NAME);
       // set next invoice to fail and advance time 1 month
       clock.addWeeks(4);
       
       // should now be in OD1 state
       // set next invoice to fail and advance time 1 month
       // should now be in OD2 state
       clock.addWeeks(4);
        
    }
}
