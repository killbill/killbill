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

package org.killbill.billing.payment.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.util.entity.Pagination;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MockPaymentDao implements PaymentDao {

    private final Map<UUID, PaymentModelDao> payments = new HashMap<UUID, PaymentModelDao>();
    private final Map<UUID, PaymentTransactionModelDao> transactions = new HashMap<UUID, PaymentTransactionModelDao>();
    private final Map<UUID, PaymentAttemptModelDao> attempts = new HashMap<UUID, PaymentAttemptModelDao>();
    private final List<PluginPropertyModelDao> properties = new ArrayList<PluginPropertyModelDao>();

    public void reset() {
        synchronized (this) {
            payments.clear();
            transactions.clear();
            attempts.clear();
            properties.clear();
        }
    }

    @Override
    public List<PluginPropertyModelDao> getProperties(final UUID attemptId, final InternalCallContext context) {
        return properties;
    }

    @Override
    public PaymentAttemptModelDao insertPaymentAttemptWithProperties(final PaymentAttemptModelDao attempt, final List<PluginPropertyModelDao> properties, final InternalCallContext context) {
        synchronized (this) {
            attempts.put(attempt.getId(), attempt);
            this.properties.addAll(properties);
            return attempt;
        }
    }

    @Override
    public void updatePaymentAttempt(final UUID paymentAttemptId, final UUID transactionId, final String state, final InternalCallContext context) {
        boolean success = false;
        synchronized (this) {
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getId().equals(paymentAttemptId)) {
                    cur.setStateName(state);
                    cur.setTransactionId(transactionId);
                    success = true;
                    break;
                }
            }
        }
        if (!success) {
            throw new RuntimeException("Could not find attempt " + paymentAttemptId);
        }
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttempts(final String paymentExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getPaymentExternalKey().equals(paymentExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentAttemptModelDao> getPaymentAttemptByTransactionExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentAttemptModelDao> result = new ArrayList<PaymentAttemptModelDao>();
            for (PaymentAttemptModelDao cur : attempts.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getDirectPaymentTransactionsByExternalKey(final String transactionExternalKey, final InternalTenantContext context) {
        final List<PaymentTransactionModelDao> result = new ArrayList<PaymentTransactionModelDao>();
        synchronized (this) {
            for (PaymentTransactionModelDao cur : transactions.values()) {
                if (cur.getTransactionExternalKey().equals(transactionExternalKey)) {
                    result.add(cur);
                }
            }
        }
        return result;
    }

    @Override
    public PaymentModelDao getDirectPaymentByExternalKey(final String externalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (PaymentModelDao cur : payments.values()) {
                if (cur.getExternalKey().equals(externalKey)) {
                    return cur;
                }
            }
        }
        return null;
    }

    @Override
    public Pagination<PaymentModelDao> getDirectPayments(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        return null;
    }

    @Override
    public PaymentModelDao insertDirectPaymentWithFirstTransaction(final PaymentModelDao directPayment, final PaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        synchronized (this) {
            payments.put(directPayment.getId(), directPayment);
            transactions.put(directPaymentTransaction.getId(), directPaymentTransaction);
        }
        return directPayment;
    }

    @Override
    public PaymentTransactionModelDao updateDirectPaymentWithNewTransaction(final UUID directPaymentId, final PaymentTransactionModelDao directPaymentTransaction, final InternalCallContext context) {
        synchronized (this) {
            transactions.put(directPaymentTransaction.getId(), directPaymentTransaction);
        }
        return directPaymentTransaction;
    }

    @Override
    public void updateDirectPaymentAndTransactionOnCompletion(final UUID directPaymentId, final String currentPaymentStateName, final UUID directTransactionId, final TransactionStatus paymentStatus, final BigDecimal processedAmount, final Currency processedCurrency, final String gatewayErrorCode, final String gatewayErrorMsg, final InternalCallContext context) {
        synchronized (this) {
            final PaymentModelDao payment = payments.get(directPaymentId);
            if (payment != null) {
                payment.setStateName(currentPaymentStateName);
            }
            final PaymentTransactionModelDao transaction = transactions.get(directTransactionId);
            if (transaction != null) {
                transaction.setTransactionStatus(paymentStatus);
                transaction.setProcessedAmount(processedAmount);
                transaction.setProcessedCurrency(processedCurrency);
                transaction.setGatewayErrorCode(gatewayErrorCode);
                transaction.setGatewayErrorMsg(gatewayErrorMsg);
            }
        }
    }

    @Override
    public PaymentModelDao getDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        synchronized (this) {
            return payments.get(directPaymentId);
        }
    }

    @Override
    public PaymentTransactionModelDao getDirectPaymentTransaction(final UUID directTransactionId, final InternalTenantContext context) {
        synchronized (this) {
            return transactions.get(directTransactionId);
        }
    }

    @Override
    public List<PaymentModelDao> getDirectPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(payments.values(), new Predicate<PaymentModelDao>() {
                @Override
                public boolean apply(final PaymentModelDao input) {
                    return input.getAccountId().equals(accountId);
                }
            }));
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getDirectTransactionsForAccount(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    final PaymentModelDao payment = payments.get(input.getPaymentId());
                    if (payment != null) {
                        return payment.getAccountId().equals(accountId);
                    } else {
                        return false;
                    }
                }
            }));
        }
    }

    @Override
    public List<PaymentTransactionModelDao> getDirectTransactionsForDirectPayment(final UUID directPaymentId, final InternalTenantContext context) {
        synchronized (this) {
            return ImmutableList.copyOf(Iterables.filter(transactions.values(), new Predicate<PaymentTransactionModelDao>() {
                @Override
                public boolean apply(final PaymentTransactionModelDao input) {
                    return input.getPaymentId().equals(directPaymentId);
                }
            }));
        }
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        synchronized (this) {
            return Iterables.tryFind(attempts.values(), new Predicate<PaymentAttemptModelDao>() {
                @Override
                public boolean apply(final PaymentAttemptModelDao input) {
                    return input.getId().equals(attemptId);
                }
            }).orNull();
        }
    }

    private final List<PaymentMethodModelDao> paymentMethods = new LinkedList<PaymentMethodModelDao>();

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        synchronized (this) {
            paymentMethods.add(paymentMethod);
            return paymentMethod;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getId().equals(paymentMethodId)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKey(final String paymentMethodExternalKey, final InternalTenantContext context) {
        synchronized (this) {
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getExternalKey().equals(paymentMethodExternalKey)) {
                    return cur;
                }
            }
            return null;
        }
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        synchronized (this) {
            final List<PaymentMethodModelDao> result = new ArrayList<PaymentMethodModelDao>();
            for (final PaymentMethodModelDao cur : paymentMethods) {
                if (cur.getAccountId().equals(accountId)) {
                    result.add(cur);
                }
            }
            return result;
        }
    }

    @Override
    public Pagination<PaymentMethodModelDao> getPaymentMethods(final String pluginName, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        synchronized (this) {
            final Iterator<PaymentMethodModelDao> it = paymentMethods.iterator();
            while (it.hasNext()) {
                final PaymentMethodModelDao cur = it.next();
                if (cur.getId().equals(paymentMethodId)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final String pluginName, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return ImmutableList.<PaymentMethodModelDao>of();
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethod(paymentMethodId, context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodByExternalKeyIncludedDeleted(final String paymentMethodExternalKey, final InternalTenantContext context) {
        return getPaymentMethodByExternalKey(paymentMethodExternalKey, context);
    }
}
