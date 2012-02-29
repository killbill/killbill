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

package com.ning.billing.payment.dao;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.ning.billing.util.clock.Clock;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;

import javax.annotation.concurrent.Immutable;

public class DefaultPaymentDao implements PaymentDao {
    private final PaymentSqlDao sqlDao;
    private final Clock clock;

    @Inject
    public DefaultPaymentDao(IDBI dbi, Clock clock) {
        this.sqlDao = dbi.onDemand(PaymentSqlDao.class);
        this.clock = clock;
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String paymentId) {
        return sqlDao.getPaymentAttemptForPaymentId(paymentId);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForInvoiceId(String invoiceId) {
        return sqlDao.getPaymentAttemptForInvoiceId(invoiceId);
    }

    @Override
    public PaymentAttempt createPaymentAttempt(PaymentAttempt paymentAttempt) {
        sqlDao.insertPaymentAttempt(paymentAttempt);
        return paymentAttempt;
    }

    @Override
    public PaymentAttempt createPaymentAttempt(Invoice invoice) {
        final PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);

        sqlDao.insertPaymentAttempt(paymentAttempt);
        return paymentAttempt;
    }

    @Override
    public void savePaymentInfo(PaymentInfo info) {
        sqlDao.insertPaymentInfo(info);
    }

    @Override
    public void updatePaymentAttemptWithPaymentId(UUID paymentAttemptId, String paymentId) {
        sqlDao.updatePaymentAttemptWithPaymentId(paymentAttemptId.toString(), paymentId, clock.getUTCNow().toDate());
    }

    @Override
    public void updatePaymentInfo(String type, String paymentId, String cardType, String cardCountry) {
        sqlDao.updatePaymentInfo(type, paymentId, cardType, cardCountry, clock.getUTCNow().toDate());
    }

    @Override
    public List<PaymentInfo> getPaymentInfo(List<String> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentInfo>of();
        } else {
            return sqlDao.getPaymentInfos(invoiceIds);
        }
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<String> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentAttempt>of();
        } else {
            return sqlDao.getPaymentAttemptsForInvoiceIds(invoiceIds);
        }
    }

    @Override
    public void updatePaymentAttemptWithRetryInfo(UUID paymentAttemptId, int retryCount, DateTime nextRetryDate) {
        final Date retryDate = nextRetryDate == null ? null : nextRetryDate.toDate();
        sqlDao.updatePaymentAttemptWithRetryInfo(paymentAttemptId.toString(), retryCount, retryDate, clock.getUTCNow().toDate());
    }

    @Override
    public PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId) {
        return sqlDao.getPaymentAttemptById(paymentAttemptId.toString());
    }

    @Override
    public PaymentInfo getPaymentInfoForPaymentAttemptId(String paymentAttemptIdStr) {
        return sqlDao.getPaymentInfoForPaymentAttemptId(paymentAttemptIdStr);
    }

}
