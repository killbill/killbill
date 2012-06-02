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

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.integration.BeatrixModule;
import com.ning.billing.beatrix.integration.TestIntegrationBase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.XMLLoader;

@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestOverdueIntegration extends TestIntegrationBase {
    private final String configXml =  
            "<overdueConfig>" +
                    "   <bundleOverdueStates>" +
                    "       <state name=\"OD3\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>50</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD3</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>5</number>" +
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "       <state name=\"OD2\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>40</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD2</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>5</number>" +
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "       <state name=\"OD1\">" +
                    "           <condition>" +
                    "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "                   <unit>DAYS</unit><number>30</number>" +
                    "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                    "           </condition>" +
                    "           <externalMessage>Reached OD1</externalMessage>" +
                    "           <blockChanges>true</blockChanges>" +
                    "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                    "           <autoReevaluationInterval>" +
                    "               <unit>DAYS</unit><number>100</number>" + // this number is intentionally too high
                    "           </autoReevaluationInterval>" +
                    "       </state>" +
                    "   </bundleOverdueStates>" +
                    "</overdueConfig>";
    private OverdueConfig config; 

    @Inject
    private ClockMock clock;

    @Inject
    private @Named("yoyo") MockPaymentProviderPlugin paymentPlugin;

    @Inject
    private BlockingApi blockingApi;
    
    @Inject
    private OverdueWrapperFactory overdueWrapperFactory;
    
    @Inject
    private OverdueUserApi overdueApi;
    
    @Inject
    private PaymentApi paymentApi;
    
    @Inject
    private InvoiceUserApi invoiceApi;

    private Account account;
    private SubscriptionBundle bundle;
    private String productName;
    private BillingPeriod term;
    private String planSetName;

    @BeforeMethod(groups = {"slow"})
    public void setupOverdue() throws Exception {
        InputStream is = new ByteArrayInputStream(configXml.getBytes());
        config = XMLLoader.getObjectFromStreamNoValidation(is,  OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);
        
        account = accountUserApi.createAccount(getAccountData(25), null, null, context);
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
        paymentPlugin.makeAllInvoicesFail(true);
        
        // set next invoice to fail and create network 
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(30); // DAY 30 have to get out of trial before first payment
        
        // STEPH
        //Thread.sleep(600000);
        
        assertTrue(busHandler.isCompleted(DELAY));
        
        // should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);
    
        clock.addDays(15); // DAY 45 - 15 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        overdueApi.refreshOverdueStateFor(bundle); // trigger a refresh because there are no events to do it for us
        //should still be in clear state
        checkODState(BlockingApi.CLEAR_STATE_NAME);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(20); // DAY 65 - 35 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        
        //Now we should be in OD1
        checkODState("OD1");
          
        clock.addDays(2); //DAY 67 - 37 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        overdueApi.refreshOverdueStateFor(bundle); // trigger a refresh because there are no events to do it for us
        // should still be in OD1
        checkODState("OD1");

        //busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(8); //DAY 75 - 45 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        overdueApi.refreshOverdueStateFor(bundle); // trigger a refresh because there are no events to do it for us
        // should still be in OD1
        checkODState("OD2");
        
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT_ERROR);
        clock.addDays(10); //DAY 85 - 55 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        // should now be in OD2 state once the update is processed
        checkODState("OD3");
        
        paymentPlugin.makeAllInvoicesFail(false);
        Collection<Invoice> invoices = invoiceApi.getUnpaidInvoicesByAccountId(account.getId(), clock.getUTCNow());
        List<String> invoiceIds = new ArrayList<String>();
        for (Invoice invoice : invoices) {
            invoiceIds.add(invoice.getId().toString()); 
            if (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                busHandler.pushExpectedEvent(NextEvent.PAYMENT);
                paymentApi.createPayment(account, invoice.getId(), invoice.getBalance(), new DefaultCallContext("test", null, null, clock));
                assertTrue(busHandler.isCompleted(DELAY));
            }
        }
        
        overdueApi.refreshOverdueStateFor(bundle);
        checkODState(BlockingApi.CLEAR_STATE_NAME);

    }

    private void checkODState(final String expected) {
        
        try {
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return expected.equals(blockingApi.getBlockingStateFor(bundle).getStateName()) ;
                }
            });
        } catch (Exception e) {
           Assert.assertEquals(blockingApi.getBlockingStateFor(bundle).getStateName(), expected);
        }
    }
}
