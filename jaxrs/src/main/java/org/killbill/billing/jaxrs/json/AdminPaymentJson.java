/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="AdminPayment")
public class AdminPaymentJson {

    private final String lastSuccessPaymentState;
    private final String currentPaymentStateName;
    private final String transactionStatus;

    @JsonCreator
    public AdminPaymentJson(@JsonProperty("lastSuccessPaymentState") final String lastSuccessPaymentState,
                            @JsonProperty("currentPaymentStateName") final String currentPaymentStateName,
                            @JsonProperty("transactionStatus") final String transactionStatus) {
        this.lastSuccessPaymentState = lastSuccessPaymentState;
        this.currentPaymentStateName = currentPaymentStateName;
        this.transactionStatus = transactionStatus;
    }

    public String getLastSuccessPaymentState() {
        return lastSuccessPaymentState;
    }

    public String getCurrentPaymentStateName() {
        return currentPaymentStateName;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    @Override
    public String toString() {
        return "AdminPaymentJson{" +
               "lastSuccessPaymentState='" + lastSuccessPaymentState + '\'' +
               ", currentPaymentStateName='" + currentPaymentStateName + '\'' +
               ", transactionStatus='" + transactionStatus + '\'' +
               '}';
    }
}
