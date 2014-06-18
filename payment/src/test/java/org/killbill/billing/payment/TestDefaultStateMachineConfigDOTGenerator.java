/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment;

import org.killbill.automaton.DefaultStateMachineConfig;
import org.killbill.automaton.dot.DefaultStateMachineConfigDOTGenerator;
import org.killbill.xmlloader.XMLLoader;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

public class TestDefaultStateMachineConfigDOTGenerator extends PaymentTestSuiteNoDB {

    @Test(groups = "fast")
    public void testStateMachine() throws Exception {
        final DefaultStateMachineConfig sms = XMLLoader.getObjectFromString(Resources.getResource("org/killbill/billing/payment/PaymentStates.xml").toExternalForm(), DefaultStateMachineConfig.class);

        final DefaultStateMachineConfigDOTGenerator generator = new DefaultStateMachineConfigDOTGenerator("Payment", sms);
        generator.build();

        System.out.println(generator.toString());
        System.out.flush();

        //Files.write((new File("/var/tmp/PaymentStates.dot")).toPath(), generator.toString().getBytes());
    }
}
