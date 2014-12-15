/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.glue;

import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.payment.PaymentTestSuiteNoDB;
import org.killbill.billing.payment.glue.PaymentModule.StateMachineProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestStateMachineProvider extends PaymentTestSuiteNoDB {

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/226")
    public void testStateMachineProvider() throws Exception {
        final StateMachineProvider retryStateMachineProvider = new StateMachineProvider(PaymentModule.DEFAULT_STATE_MACHINE_RETRY_XML);
        final StateMachineConfig retryStateMachineConfig = retryStateMachineProvider.get();
        Assert.assertEquals(retryStateMachineConfig.getStateMachines().length, 1);

        final StateMachineProvider paymentStateMachineProvider = new StateMachineProvider(PaymentModule.DEFAULT_STATE_MACHINE_PAYMENT_XML);
        final StateMachineConfig paymentStateMachineConfig = paymentStateMachineProvider.get();
        Assert.assertEquals(paymentStateMachineConfig.getStateMachines().length, 8);
    }
}
