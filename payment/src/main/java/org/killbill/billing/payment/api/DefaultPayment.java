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

package org.killbill.billing.payment.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.payment.dao.PaymentAttemptModelDao;
import org.killbill.billing.payment.dao.PaymentModelDao;
import org.killbill.billing.payment.dao.RefundModelDao;
import org.killbill.billing.payment.plugin.api.PaymentInfoPlugin;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class DefaultPayment extends EntityBase implements Payment {

    private final UUID accountId;
    private final UUID invoiceId;
    private final UUID paymentMethodId;
    private final BigDecimal amount;
    private final BigDecimal paidAmount;
    private final Currency currency;
    private final DateTime effectiveDate;
    private final Integer paymentNumber;
    private final PaymentStatus paymentStatus;
    private final List<PaymentAttempt> attempts;
    private final PaymentInfoPlugin paymentPluginInfo;

    private DefaultPayment(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId, final UUID invoiceId,
                           final UUID paymentMethodId, final BigDecimal amount, final BigDecimal paidAmount, final Currency currency,
                           final DateTime effectiveDate, final Integer paymentNumber,
                           final PaymentStatus paymentStatus,
                           @Nullable final PaymentInfoPlugin paymentPluginInfo,
                           final List<PaymentAttempt> attempts) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.invoiceId = invoiceId;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.paymentNumber = paymentNumber;
        this.paymentStatus = paymentStatus;
        this.attempts = attempts;
        this.paymentPluginInfo = paymentPluginInfo;
    }

    public DefaultPayment(final PaymentModelDao src, @Nullable final PaymentInfoPlugin paymentPluginInfo, final List<PaymentAttemptModelDao> attempts, final List<RefundModelDao> refunds) {
        this(src.getId(),
             src.getCreatedDate(),
             src.getUpdatedDate(),
             src.getAccountId(),
             src.getInvoiceId(),
             src.getPaymentMethodId(),
             src.getAmount(),
             toPaidAmount(src.getPaymentStatus(), src.getAmount(), refunds),
             src.getCurrency(),
             src.getEffectiveDate(),
             src.getPaymentNumber(),
             src.getPaymentStatus(),
             paymentPluginInfo,
             toPaymentAttempts(attempts));
    }

    @Override
    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    @Override
    public PaymentInfoPlugin getPaymentInfoPlugin() {
        return paymentPluginInfo;
    }

    @Override
    public List<PaymentAttempt> getAttempts() {
        return attempts;
    }

    private static BigDecimal toPaidAmount(final PaymentStatus paymentStatus, final BigDecimal amount, final Iterable<RefundModelDao> refunds) {
        if (paymentStatus != PaymentStatus.SUCCESS) {
            return BigDecimal.ZERO;
        }

        BigDecimal result = amount;
        for (final RefundModelDao cur : refunds) {
            if (cur.getRefundStatus() == RefundStatus.COMPLETED) {
                result = result.subtract(cur.getAmount());
            }
        }
        return result;
    }

    private static List<PaymentAttempt> toPaymentAttempts(final Collection<PaymentAttemptModelDao> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return Collections.emptyList();
        }

        return new ArrayList<PaymentAttempt>(Collections2.transform(attempts, new Function<PaymentAttemptModelDao, PaymentAttempt>() {
            @Override
            public PaymentAttempt apply(final PaymentAttemptModelDao input) {
                return new PaymentAttempt() {
                    @Override
                    public PaymentStatus getPaymentStatus() {
                        return input.getProcessingStatus();
                    }

                    @Override
                    public DateTime getEffectiveDate() {
                        return input.getEffectiveDate();
                    }

                    @Override
                    public UUID getId() {
                        return input.getId();
                    }

                    @Override
                    public DateTime getCreatedDate() {
                        return input.getCreatedDate();
                    }

                    @Override
                    public DateTime getUpdatedDate() {
                        return input.getUpdatedDate();
                    }

                    @Override
                    public String getGatewayErrorCode() {
                        return input.getGatewayErrorCode();
                    }

                    @Override
                    public String getGatewayErrorMsg() {
                        return input.getGatewayErrorMsg();
                    }
                };
            }
        }));
    }
}
