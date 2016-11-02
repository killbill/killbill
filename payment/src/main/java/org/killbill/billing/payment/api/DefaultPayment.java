/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.currency.KillBillMoney;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class DefaultPayment extends EntityBase implements Payment {

    private final UUID accountId;
    private final UUID paymentMethodId;
    private final Integer paymentNumber;
    private final String externalKey;
    private final BigDecimal authAmount;
    private final BigDecimal captureAmount;
    private final BigDecimal purchasedAmount;
    private final BigDecimal creditAmount;
    private final BigDecimal refundAmount;
    private final Boolean isAuthVoided;

    private final Currency currency;
    private final List<PaymentTransaction> transactions;
    private final List<PaymentAttempt> paymentAttempts;

    public DefaultPayment(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final UUID accountId,
                          final UUID paymentMethodId,
                          final Integer paymentNumber,
                          final String externalKey,
                          final List<PaymentTransaction> transactions,
                          final List<PaymentAttempt> paymentAttempts) {
        super(id, createdDate, updatedDate);
        this.accountId = accountId;
        this.paymentMethodId = paymentMethodId;
        this.paymentNumber = paymentNumber;
        this.externalKey = externalKey;
        this.transactions = transactions;
        this.paymentAttempts = paymentAttempts;

        final Collection<PaymentTransaction> voidedTransactions = new LinkedList<PaymentTransaction>();
        final Collection<PaymentTransaction> nonVoidedTransactions = new LinkedList<PaymentTransaction>();
        int nvTxToVoid = 0;
        for (final PaymentTransaction paymentTransaction : Lists.<PaymentTransaction>reverse(transactions)) {
            if (TransactionStatus.SUCCESS.equals(paymentTransaction.getTransactionStatus())) {
                if (paymentTransaction.getTransactionType() == TransactionType.VOID) {
                    nvTxToVoid++;
                } else {
                    if (nvTxToVoid > 0) {
                        nvTxToVoid--;
                        voidedTransactions.add(paymentTransaction);
                    } else {
                        nonVoidedTransactions.add(paymentTransaction);
                    }
                }
            }
        }

        final Collection<PaymentTransaction> chargebackTransactions = getChargebackTransactions(transactions);
        final Currency chargebackProcessedCurrency = getCurrencyForTransactions(chargebackTransactions, true);
        final BigDecimal chargebackProcessedAmount = chargebackProcessedCurrency == null ? BigDecimal.ZERO : getAmountForTransactions(chargebackTransactions, true);
        final Currency chargebackCurrency = getCurrencyForTransactions(chargebackTransactions, false);
        final BigDecimal chargebackAmount = chargebackCurrency == null ? BigDecimal.ZERO : getAmountForTransactions(chargebackTransactions, false);

        PaymentTransaction transactionToUseForCurrency = Iterables.<PaymentTransaction>getFirst(Iterables.<PaymentTransaction>filter(transactions,
                                                                                                                                     new Predicate<PaymentTransaction>() {
                                                                                                                                         @Override
                                                                                                                                         public boolean apply(final PaymentTransaction transaction) {
                                                                                                                                             return (transaction.getTransactionType() == TransactionType.AUTHORIZE ||
                                                                                                                                                     transaction.getTransactionType() == TransactionType.PURCHASE ||
                                                                                                                                                     transaction.getTransactionType() == TransactionType.CREDIT) &&
                                                                                                                                                    (TransactionStatus.SUCCESS.equals(transaction.getTransactionStatus()) ||
                                                                                                                                                     TransactionStatus.PENDING.equals(transaction.getTransactionStatus()));
                                                                                                                                         }
                                                                                                                                     }), null);
        if (transactionToUseForCurrency == null) {
            // No successful one, take the last non-successful one then
            transactionToUseForCurrency = Iterables.<PaymentTransaction>getLast(Iterables.<PaymentTransaction>filter(transactions,
                                                                                                                     new Predicate<PaymentTransaction>() {
                                                                                                                         @Override
                                                                                                                         public boolean apply(final PaymentTransaction transaction) {
                                                                                                                             return transaction.getTransactionType() == TransactionType.AUTHORIZE ||
                                                                                                                                    transaction.getTransactionType() == TransactionType.PURCHASE ||
                                                                                                                                    transaction.getTransactionType() == TransactionType.CREDIT;
                                                                                                                         }
                                                                                                                     }), null);
        }
        this.currency = transactionToUseForCurrency == null ? null : transactionToUseForCurrency.getCurrency();

        this.authAmount = getAmountForTransactions(this.currency,
                                                   nonVoidedTransactions,
                                                   TransactionType.AUTHORIZE,
                                                   chargebackTransactions,
                                                   chargebackProcessedAmount,
                                                   chargebackProcessedCurrency,
                                                   chargebackAmount,
                                                   chargebackCurrency);
        this.captureAmount = getAmountForTransactions(this.currency,
                                                      nonVoidedTransactions,
                                                      TransactionType.CAPTURE,
                                                      chargebackTransactions,
                                                      chargebackProcessedAmount,
                                                      chargebackProcessedCurrency,
                                                      chargebackAmount,
                                                      chargebackCurrency);
        this.purchasedAmount = getAmountForTransactions(this.currency,
                                                        nonVoidedTransactions,
                                                        TransactionType.PURCHASE,
                                                        chargebackTransactions,
                                                        chargebackProcessedAmount,
                                                        chargebackProcessedCurrency,
                                                        chargebackAmount,
                                                        chargebackCurrency);
        this.creditAmount = getAmountForTransactions(this.currency,
                                                     nonVoidedTransactions,
                                                     TransactionType.CREDIT,
                                                     chargebackTransactions,
                                                     chargebackProcessedAmount,
                                                     chargebackProcessedCurrency,
                                                     chargebackAmount,
                                                     chargebackCurrency);
        this.refundAmount = getAmountForTransactions(this.currency,
                                                     nonVoidedTransactions,
                                                     TransactionType.REFUND,
                                                     chargebackTransactions,
                                                     chargebackProcessedAmount,
                                                     chargebackProcessedCurrency,
                                                     chargebackAmount,
                                                     chargebackCurrency);

        this.isAuthVoided = Iterables.<PaymentTransaction>tryFind(voidedTransactions,
                                                                  new Predicate<PaymentTransaction>() {
                                                                      @Override
                                                                      public boolean apply(final PaymentTransaction input) {
                                                                          return input.getTransactionType() == TransactionType.AUTHORIZE && TransactionStatus.SUCCESS.equals(input.getTransactionStatus());
                                                                      }
                                                                  }).isPresent();
    }

    private static Collection<PaymentTransaction> getChargebackTransactions(final Collection<PaymentTransaction> transactions) {
        final Collection<String> successfulChargebackExternalKeys = new HashSet<String>();

        for (final PaymentTransaction transaction : transactions) {
            // We are looking for the last chargeback in state SUCCESS for a given external key
            if (TransactionType.CHARGEBACK.equals(transaction.getTransactionType()) && TransactionStatus.SUCCESS.equals(transaction.getTransactionStatus())) {
                successfulChargebackExternalKeys.add(transaction.getExternalKey());
            } else if (TransactionType.CHARGEBACK.equals(transaction.getTransactionType()) && TransactionStatus.PAYMENT_FAILURE.equals(transaction.getTransactionStatus())) {
                successfulChargebackExternalKeys.remove(transaction.getExternalKey());
            }
        }

        return Collections2.<PaymentTransaction>filter(transactions, new Predicate<PaymentTransaction>() {
            @Override
            public boolean apply(final PaymentTransaction input) {
                return successfulChargebackExternalKeys.contains(input.getExternalKey());
            }
        });
    }

    private static BigDecimal getAmountForTransactions(final Currency paymentCurrency,
                                                       final Collection<PaymentTransaction> transactions,
                                                       final TransactionType transactiontype,
                                                       final Collection<PaymentTransaction> chargebackTransactions,
                                                       final BigDecimal chargebackProcessedAmount,
                                                       final Currency chargebackProcessedCurrency,
                                                       final BigDecimal chargebackAmount,
                                                       final Currency chargebackCurrency) {
        BigDecimal unformattedAmountForTransactions = null;

        final Collection<PaymentTransaction> candidateTransactions = Collections2.<PaymentTransaction>filter(transactions,
                                                                                                             new Predicate<PaymentTransaction>() {
                                                                                                                 @Override
                                                                                                                 public boolean apply(final PaymentTransaction transaction) {
                                                                                                                     return transaction.getTransactionType() == transactiontype && TransactionStatus.SUCCESS.equals(transaction.getTransactionStatus());
                                                                                                                 }
                                                                                                             });

        final boolean takeChargebacksIntoAccount = ImmutableList.<TransactionType>of(TransactionType.CAPTURE, TransactionType.PURCHASE).contains(transactiontype);
        Currency currencyForTransactions = getCurrencyForTransactions(candidateTransactions, true);
        if (currencyForTransactions == null || currencyForTransactions != paymentCurrency) {
            currencyForTransactions = getCurrencyForTransactions(candidateTransactions, false);
            if (currencyForTransactions == null) {
                // Multiple currencies - cannot compute the total
                unformattedAmountForTransactions = BigDecimal.ZERO;
            } else if (currencyForTransactions != paymentCurrency) {
                // Different currency than the main payment currency
                unformattedAmountForTransactions = BigDecimal.ZERO;
            } else {
                final BigDecimal amountForTransactions = getAmountForTransactions(candidateTransactions, false);
                unformattedAmountForTransactions = getAmountForTransactions(amountForTransactions,
                                                                            takeChargebacksIntoAccount,
                                                                            currencyForTransactions,
                                                                            chargebackTransactions,
                                                                            chargebackProcessedAmount,
                                                                            chargebackProcessedCurrency,
                                                                            chargebackAmount,
                                                                            chargebackCurrency);
            }
        } else {
            final BigDecimal amountForTransactions = getAmountForTransactions(candidateTransactions, true);
            unformattedAmountForTransactions = getAmountForTransactions(amountForTransactions,
                                                                        takeChargebacksIntoAccount,
                                                                        currencyForTransactions,
                                                                        chargebackTransactions,
                                                                        chargebackProcessedAmount,
                                                                        chargebackProcessedCurrency,
                                                                        chargebackAmount,
                                                                        chargebackCurrency);
        }

        return unformattedAmountForTransactions == null || currencyForTransactions == null ? unformattedAmountForTransactions : KillBillMoney.of(unformattedAmountForTransactions, currencyForTransactions);
    }

    private static BigDecimal getAmountForTransactions(final BigDecimal amountForTransactions,
                                                       final boolean takeChargebacksIntoAccount,
                                                       final Currency currencyForTransactions,
                                                       final Collection<PaymentTransaction> chargebackTransactions,
                                                       final BigDecimal chargebackProcessedAmount,
                                                       final Currency chargebackProcessedCurrency,
                                                       final BigDecimal chargebackAmount,
                                                       final Currency chargebackCurrency) {
        if (!takeChargebacksIntoAccount) {
            return amountForTransactions;
        }

        final BigDecimal chargebackAmountInCorrectCurrency;
        if (currencyForTransactions == chargebackProcessedCurrency) {
            chargebackAmountInCorrectCurrency = chargebackProcessedAmount;
        } else if (currencyForTransactions == chargebackCurrency) {
            chargebackAmountInCorrectCurrency = chargebackAmount;
        } else if (!chargebackTransactions.isEmpty()) {
            // Payment has chargebacks but in a different currency - zero-out the payment
            chargebackAmountInCorrectCurrency = amountForTransactions;
        } else {
            chargebackAmountInCorrectCurrency = BigDecimal.ZERO;
        }
        return amountForTransactions.add(chargebackAmountInCorrectCurrency.negate()).max(BigDecimal.ZERO);
    }

    private static BigDecimal getAmountForTransactions(final Iterable<PaymentTransaction> candidateTransactions, final boolean useProcessedValues) {
        BigDecimal amount = BigDecimal.ZERO;
        for (final PaymentTransaction transaction : candidateTransactions) {
            if (useProcessedValues) {
                amount = amount.add(transaction.getProcessedAmount());
            } else {
                amount = amount.add(transaction.getAmount());
            }
        }
        return amount;
    }

    private static Currency getCurrencyForTransactions(final Collection<PaymentTransaction> candidateTransactions, final boolean useProcessedValues) {
        final Collection<Currency> currencies = new HashSet<Currency>(Collections2.<PaymentTransaction, Currency>transform(candidateTransactions,
                                                                                                                           new Function<PaymentTransaction, Currency>() {
                                                                                                                               @Override
                                                                                                                               public Currency apply(final PaymentTransaction transaction) {
                                                                                                                                   return useProcessedValues ? transaction.getProcessedCurrency() : transaction.getCurrency();
                                                                                                                               }
                                                                                                                           }));

        return currencies.size() > 1 ? null : Iterables.<Currency>getFirst(currencies, null);
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public Integer getPaymentNumber() {
        return paymentNumber;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public BigDecimal getAuthAmount() {
        return authAmount;
    }

    @Override
    public BigDecimal getCapturedAmount() {
        return captureAmount;
    }

    @Override
    public BigDecimal getPurchasedAmount() {
        return purchasedAmount;
    }

    @Override
    public BigDecimal getCreditedAmount() {
        return creditAmount;
    }

    @Override
    public BigDecimal getRefundedAmount() {
        return refundAmount;
    }

    @Override
    public Boolean isAuthVoided() {
        return isAuthVoided;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public List<PaymentTransaction> getTransactions() {
        return transactions;
    }

    @Override
    public List<PaymentAttempt> getPaymentAttempts() { return paymentAttempts; }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultPayment{");
        sb.append("accountId=").append(accountId);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", paymentNumber=").append(paymentNumber);
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", authAmount=").append(authAmount);
        sb.append(", captureAmount=").append(captureAmount);
        sb.append(", purchasedAmount=").append(purchasedAmount);
        sb.append(", refundAmount=").append(refundAmount);
        sb.append(", currency=").append(currency);
        sb.append(", transactions=").append(transactions);
        sb.append(", paymentAttempts=").append(paymentAttempts);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultPayment that = (DefaultPayment) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (authAmount != null ? authAmount.compareTo(that.authAmount) != 0 : that.authAmount != null) {
            return false;
        }
        if (captureAmount != null ? captureAmount.compareTo(that.captureAmount) != 0 : that.captureAmount != null) {
            return false;
        }
        if (purchasedAmount != null ? purchasedAmount.compareTo(that.purchasedAmount) != 0 : that.purchasedAmount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (paymentNumber != null ? !paymentNumber.equals(that.paymentNumber) : that.paymentNumber != null) {
            return false;
        }
        if (refundAmount != null ? refundAmount.compareTo(that.refundAmount) != 0 : that.refundAmount != null) {
            return false;
        }
        if (transactions != null ? !transactions.equals(that.transactions) : that.transactions != null) {
            return false;
        }
        if (paymentAttempts != null ? !paymentAttempts.equals(that.paymentAttempts) : that.paymentAttempts != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (paymentNumber != null ? paymentNumber.hashCode() : 0);
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (authAmount != null ? authAmount.hashCode() : 0);
        result = 31 * result + (captureAmount != null ? captureAmount.hashCode() : 0);
        result = 31 * result + (purchasedAmount != null ? purchasedAmount.hashCode() : 0);
        result = 31 * result + (refundAmount != null ? refundAmount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (transactions != null ? transactions.hashCode() : 0);
        result = 31 * result + (paymentAttempts != null ? paymentAttempts.hashCode() : 0);
        return result;
    }
}
