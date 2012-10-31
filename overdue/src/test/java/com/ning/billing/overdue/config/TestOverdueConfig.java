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

package com.ning.billing.overdue.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.overdue.EmailNotification;
import com.ning.billing.overdue.OverdueTestSuite;
import com.ning.billing.util.config.catalog.XMLLoader;

public class TestOverdueConfig extends OverdueTestSuite {

    @Test(groups = "fast")
    public void testParseConfig() throws Exception {
        final String xml = "<overdueConfig>" +
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
                           "           <autoReevaluationInterval>" +
                           "               <unit>DAYS</unit><number>15</number>" +
                           "           </autoReevaluationInterval>" +
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
                           "           <autoReevaluationInterval>" +
                           "               <unit>DAYS</unit><number>15</number>" +
                           "           </autoReevaluationInterval>" +
                           "           <enterStateEmailNotification>" +
                           "               <subject>ToTo</subject><templateName>Titi</templateName>" +
                           "           </enterStateEmailNotification>" +
                           "       </state>" +
                           "   </bundleOverdueStates>" +
                           "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(xml.getBytes());
        final OverdueConfig c = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        Assert.assertEquals(c.getBundleStateSet().size(), 2);

        Assert.assertNull(c.getBundleStateSet().getStates()[0].getEnterStateEmailNotification());

        final EmailNotification secondNotification = c.getBundleStateSet().getStates()[1].getEnterStateEmailNotification();
        Assert.assertEquals(secondNotification.getSubject(), "ToTo");
        Assert.assertEquals(secondNotification.getTemplateName(), "Titi");
        Assert.assertFalse(secondNotification.isHTML());
    }
}
