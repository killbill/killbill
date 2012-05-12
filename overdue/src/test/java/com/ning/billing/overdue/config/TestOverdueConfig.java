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

import org.testng.annotations.Test;

import com.ning.billing.util.config.XMLLoader;

public class TestOverdueConfig {
    private String xml = 
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
                    "       </state>" +
                    "   </bundleOverdueStates>" +
                    "</overdueConfig>";

    @Test
    public void testParseConfig() throws Exception {
        InputStream is = new ByteArrayInputStream(xml.getBytes());
        OverdueConfig c = XMLLoader.getObjectFromStreamNoValidation(is,  OverdueConfig.class);

    }

}
