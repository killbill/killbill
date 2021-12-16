/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.overdue.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.joda.time.Period;
import org.killbill.billing.catalog.api.Duration;
import org.killbill.billing.overdue.api.OverdueCondition;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.api.OverdueStatesAccount;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.overdue.OverdueTestSuiteNoDB;
import org.killbill.xmlloader.XMLLoader;

public class TestOverdueConfig extends OverdueTestSuiteNoDB {

    @Test(groups = "fast")
    public void testParseConfig() throws Exception {
        final String xml = "<overdueConfig>" +
                           "   <accountOverdueStates>" +
                           "       <initialReevaluationInterval>" +
                           "           <unit>DAYS</unit><number>1</number>" +
                           "       </initialReevaluationInterval>" +
                           "       <state name=\"OD2\">" +
                           "           <condition>" +
                           "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                           "                   <unit>MONTHS</unit><number>2</number>" +
                           "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                           "           </condition>" +
                           "           <externalMessage>Reached OD1</externalMessage>" +
                           "           <blockChanges>true</blockChanges>" +
                           "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                           "           <autoReevaluationInterval>" +
                           "               <unit>DAYS</unit><number>15</number>" +
                           "           </autoReevaluationInterval>" +
                           "           <enterStateEmailNotification>" +
                           "               <subject>ToTo</subject><templateName>Titi</templateName>" +
                           "           </enterStateEmailNotification>" +
                           "       </state>" +
                           "       <state name=\"OD1\">" +
                           "           <condition>" +
                           "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                           "                   <unit>MONTHS</unit><number>1</number>" +
                           "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                           "           </condition>" +
                           "           <externalMessage>Reached OD1</externalMessage>" +
                           "           <blockChanges>true</blockChanges>" +
                           "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                           "           <autoReevaluationInterval>" +
                           "               <unit>DAYS</unit><number>15</number>" +
                           "           </autoReevaluationInterval>" +
                           "       </state>" +
                           "   </accountOverdueStates>" +
                           "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final DefaultOverdueConfig c = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        Assert.assertEquals(c.getOverdueStatesAccount().size(), 2);

        Assert.assertNotNull(c.getOverdueStatesAccount().getInitialReevaluationInterval());
        Assert.assertEquals(c.getOverdueStatesAccount().getInitialReevaluationInterval().getDays(), 1);

        Assert.assertEquals(c.getOverdueStatesAccount().getFirstState().getName(), "OD1");
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1497")
    public void testBadOverdueXml() throws Exception {
        final OverdueConfig overdueConfig = getOverdueConfig("BadOverdue.xml");

        final OverdueStatesAccount statesAccount = overdueConfig.getOverdueStatesAccount();
        Assert.assertNotNull(statesAccount);

        final Period reevaluationInterval = statesAccount.getInitialReevaluationInterval();
        Assert.assertNull(reevaluationInterval);

        final OverdueState[] overdueStates = statesAccount.getStates();
        Assert.assertNotNull(overdueStates);
        Assert.assertEquals(overdueStates.length, 4);

        for (final OverdueState state : overdueStates) {
            Assert.assertNotNull(state.getAutoReevaluationInterval());

            final OverdueCondition condition = state.getOverdueCondition();
            Assert.assertNotNull(condition);

            Assert.assertNotNull(condition.getTotalUnpaidInvoiceBalanceEqualsOrExceeds());

            final Duration duration = condition.getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds();
            Assert.assertNull(duration);

            // Thus, calling method in duration will throw NullPointerException
        }
    }
}
