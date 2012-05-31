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
package com.ning.billing.payment.plugin.api;

import java.math.BigDecimal;

import org.joda.time.DateTime;

public interface PaymentInfoPlugin {
    
    public enum PaymentPluginStatus {
        UNDEFINED,
        PROCESSED,
        ERROR
    };
    
    public BigDecimal getAmount();

    public DateTime getCreatedDate();

    public DateTime getEffectiveDate();

    public PaymentPluginStatus getStatus();
    
    public String getError();
    
    
    /** 
     * STEPH  

     * Zuora specific

    public String getExternalPaymentId();
    
    public String getReferenceId();
    
    public String getPaymentMethodId();

    public String getPaymentNumber();

    public String getStatus();

    public String getType();
     *
     */    
}
