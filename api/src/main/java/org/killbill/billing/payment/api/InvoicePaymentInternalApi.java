/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoicePayment;

public interface InvoicePaymentInternalApi {

    public InvoicePayment createPurchaseForInvoicePayment(boolean isApiPayment,
                                                          Account account,
                                                          UUID invoiceId,
                                                          UUID paymentMethodId,
                                                          UUID paymentId,
                                                          BigDecimal amount,
                                                          Currency currency,
                                                          DateTime effectiveDate,
                                                          String paymentExternalKey,
                                                          String paymentTransactionExternalKey,
                                                          Iterable<PluginProperty> properties,
                                                          PaymentOptions paymentOptions,
                                                          InternalCallContext context) throws PaymentApiException;
}
