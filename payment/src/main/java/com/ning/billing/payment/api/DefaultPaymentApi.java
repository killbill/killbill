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

package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.payment.core.AccountProcessor;
import com.ning.billing.payment.core.PaymentMethodProcessor;
import com.ning.billing.payment.core.PaymentProcessor;
import com.ning.billing.payment.core.RefundProcessor;
import com.ning.billing.payment.plugin.api.PaymentProviderAccount;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultPaymentApi implements PaymentApi {
    

    private final PaymentMethodProcessor methodProcessor;
    private final PaymentProcessor paymentProcessor;
    private final RefundProcessor refundProcessor;
    private final AccountProcessor accountProcessor;
  
    @Inject
    public DefaultPaymentApi(final PaymentMethodProcessor methodProcessor,
            final AccountProcessor accountProcessor,
            final PaymentProcessor paymentProcessor,
            final RefundProcessor refundProcessor) {
        this.methodProcessor = methodProcessor;
        this.accountProcessor = accountProcessor;
        this.paymentProcessor = paymentProcessor;
        this.refundProcessor = refundProcessor;
    }
     
 
    @Override
    public PaymentMethodInfo getPaymentMethod(String accountKey, String paymentMethodId) 
        throws PaymentApiException {
        return methodProcessor.getPaymentMethod(accountKey, paymentMethodId);
    }

    @Override
    public List<PaymentMethodInfo> getPaymentMethods(String accountKey)
    throws PaymentApiException {
        return methodProcessor.getPaymentMethods(accountKey);
    }

    @Override
    public void updatePaymentGateway(final String accountKey, final CallContext context) 
    throws PaymentApiException {
        methodProcessor.updatePaymentGateway(accountKey, context);
    }


    @Override
    public String addPaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethod, final CallContext context) 
    throws PaymentApiException {
        return methodProcessor.addPaymentMethod(accountKey, paymentMethod, context);
    }


    @Override
    public void deletePaymentMethod(final String accountKey, final String paymentMethodId, final CallContext context) 
    throws PaymentApiException {
        methodProcessor.deletePaymentMethod(accountKey, paymentMethodId, context);
    }

    @Override
    public PaymentMethodInfo updatePaymentMethod(final String accountKey, final PaymentMethodInfo paymentMethodInfo, final CallContext context) 
    throws PaymentApiException {

        return methodProcessor.updatePaymentMethod(accountKey, paymentMethodInfo, context);
     }
   
    @Override
    public Payment createPayment(final String accountKey, final UUID invoiceId, final BigDecimal amount, final CallContext context) 
    throws PaymentApiException {
        return paymentProcessor.createPayment(accountKey, invoiceId, amount, context, true);
     }
    
    @Override
    public Payment createPayment(Account account, UUID invoiceId,
            final BigDecimal amount, CallContext context) throws PaymentApiException {
        return paymentProcessor.createPayment(account, invoiceId, amount, context, true);        
    }

    
    @Override
    public List<Payment> getInvoicePayments(UUID invoiceId) {
        return paymentProcessor.getInvoicePayments(invoiceId);
    }

    @Override
    public List<Payment> getAccountPayments(UUID accountId)
            throws PaymentApiException {
        return paymentProcessor.getAccountPayments(accountId);
    }

    @Override
    public String createPaymentProviderAccount(Account account, CallContext context) 
    throws PaymentApiException {
        return accountProcessor.createPaymentProviderAccount(account, context);
    }

    @Override
    public void updatePaymentProviderAccountContact(String externalKey, CallContext context) 
        throws PaymentApiException {
        accountProcessor.updatePaymentProviderAccountContact(externalKey, context);
    }


    @Override
    public Refund createRefund(Account account, UUID paymentId, CallContext context)
        throws PaymentApiException {
        return refundProcessor.createRefund(account, paymentId, context);
    }


    @Override
    public PaymentProviderAccount getPaymentProviderAccount(String accountKey)
            throws PaymentApiException {
        return accountProcessor.getPaymentProviderAccount(accountKey);
    }
}
