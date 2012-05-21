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

package com.ning.billing.overdue;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.overdue.applicator.TestOverdueStateApplicator;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.overdue.glue.DefaultOverdueModule;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.clock.ClockMock;

@Guice(modules = {MockJunctionModule.class, MockInvoiceModule.class, DefaultOverdueModule.class})
public class OverdueTestBase {
    protected final String configXml =  
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
    protected OverdueConfig config; 

    @Inject
    protected ClockMock clock;

    @Inject
    protected BlockingApi blockingApi;
    
    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;
    
    @Inject
    protected OverdueUserApi overdueApi;
   
    
    @Inject
    protected InvoiceUserApi invoiceApi;

    protected Account account;
    protected SubscriptionBundle bundle;
    protected String productName;
    protected BillingPeriod term;
    protected String planSetName;
    
    @Inject
    protected DefaultOverdueService service;
    @Inject
    protected BusService busService;
    @Inject
    protected MysqlTestingHelper helper;

    protected void setupMySQL() throws IOException
    {
        final String utilDdl = IOUtils.toString(TestOverdueStateApplicator.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        helper.startMysql();

        helper.initDb(utilDdl);
    }

  
    @BeforeClass(groups = "slow")
    public void setup() throws Exception{

        setupMySQL();
    }

    @AfterClass(groups = "slow")
    public void tearDown() throws Exception {
        helper.stopMysql();
    }


    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {

        // Pre test cleanup
        helper.cleanupAllTables();

        clock.resetDeltaFromReality();

    }
}
