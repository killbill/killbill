/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.analytics.model.BusinessInvoiceItemModelDao;
import com.ning.billing.analytics.model.BusinessInvoiceModelDao;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessInvoice extends EntityBase implements BusinessInvoice {

    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final UUID accountId;
    private final String accountKey;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final BigDecimal balance;
    private final BigDecimal amountPaid;
    private final BigDecimal amountCharged;
    private final BigDecimal amountCredited;
    private final List<BusinessInvoiceItem> invoiceItems;

    public DefaultBusinessInvoice(final BusinessInvoiceModelDao businessInvoiceModelDao,
                                  final Collection<BusinessInvoiceItemModelDao> businessInvoiceItemModelDaos) {
        super(businessInvoiceModelDao.getInvoiceId(), businessInvoiceModelDao.getCreatedDate(), businessInvoiceModelDao.getUpdatedDate());
        this.accountId = businessInvoiceModelDao.getAccountId();
        this.accountKey = businessInvoiceModelDao.getAccountKey();
        this.amountCharged = businessInvoiceModelDao.getAmountCharged();
        this.amountCredited = businessInvoiceModelDao.getAmountCredited();
        this.amountPaid = businessInvoiceModelDao.getAmountPaid();
        this.balance = businessInvoiceModelDao.getBalance();
        this.currency = businessInvoiceModelDao.getCurrency();
        this.invoiceDate = businessInvoiceModelDao.getInvoiceDate();
        this.invoiceId = businessInvoiceModelDao.getInvoiceId();
        this.invoiceNumber = businessInvoiceModelDao.getInvoiceNumber();
        this.targetDate = businessInvoiceModelDao.getTargetDate();
        this.invoiceItems = toInvoiceItems(businessInvoiceItemModelDaos);
    }

    private List<BusinessInvoiceItem> toInvoiceItems(final Collection<BusinessInvoiceItemModelDao> businessInvoiceItemModelDaos) {
        final List<BusinessInvoiceItem> businessInvoiceItems = new ArrayList<BusinessInvoiceItem>();
        for (final BusinessInvoiceItemModelDao businessInvoiceItemModelDao : businessInvoiceItemModelDaos) {
            businessInvoiceItems.add(new BusinessInvoiceItem() {
                @Override
                public UUID getItemId() {
                    return businessInvoiceItemModelDao.getItemId();
                }

                @Override
                public UUID getInvoiceId() {
                    return businessInvoiceItemModelDao.getInvoiceId();
                }

                @Override
                public String getItemType() {
                    return businessInvoiceItemModelDao.getItemType();
                }

                @Override
                public String getExternalKey() {
                    return businessInvoiceItemModelDao.getExternalKey();
                }

                @Override
                public String getProductName() {
                    return businessInvoiceItemModelDao.getProductName();
                }

                @Override
                public String getProductType() {
                    return businessInvoiceItemModelDao.getProductType();
                }

                @Override
                public String getProductCategory() {
                    return businessInvoiceItemModelDao.getProductCategory();
                }

                @Override
                public String getSlug() {
                    return businessInvoiceItemModelDao.getSlug();
                }

                @Override
                public String getPhase() {
                    return businessInvoiceItemModelDao.getPhase();
                }

                @Override
                public String getBillingPeriod() {
                    return businessInvoiceItemModelDao.getBillingPeriod();
                }

                @Override
                public LocalDate getStartDate() {
                    return businessInvoiceItemModelDao.getStartDate();
                }

                @Override
                public LocalDate getEndDate() {
                    return businessInvoiceItemModelDao.getEndDate();
                }

                @Override
                public BigDecimal getAmount() {
                    return businessInvoiceItemModelDao.getAmount();
                }

                @Override
                public Currency getCurrency() {
                    return businessInvoiceItemModelDao.getCurrency();
                }

                @Override
                public UUID getLinkedItemId() {
                    return businessInvoiceItemModelDao.getLinkedItemId();
                }
            });
        }

        return businessInvoiceItems;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getAccountKey() {
        return accountKey;
    }

    @Override
    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    @Override
    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    @Override
    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    @Override
    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    @Override
    public List<BusinessInvoiceItem> getInvoiceItems() {
        return invoiceItems;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessInvoice");
        sb.append("{invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", accountId=").append(accountId);
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", currency=").append(currency);
        sb.append(", balance=").append(balance);
        sb.append(", amountPaid=").append(amountPaid);
        sb.append(", amountCharged=").append(amountCharged);
        sb.append(", amountCredited=").append(amountCredited);
        sb.append(", invoiceItemsSize=").append(invoiceItems.size());
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

        final DefaultBusinessInvoice that = (DefaultBusinessInvoice) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (amountCharged != null ? !amountCharged.equals(that.amountCharged) : that.amountCharged != null) {
            return false;
        }
        if (amountCredited != null ? !amountCredited.equals(that.amountCredited) : that.amountCredited != null) {
            return false;
        }
        if (amountPaid != null ? !amountPaid.equals(that.amountPaid) : that.amountPaid != null) {
            return false;
        }
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceItems != null ? invoiceItems.size() != that.invoiceItems.size() : that.invoiceItems != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (targetDate != null ? !targetDate.equals(that.targetDate) : that.targetDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (amountPaid != null ? amountPaid.hashCode() : 0);
        result = 31 * result + (amountCharged != null ? amountCharged.hashCode() : 0);
        result = 31 * result + (amountCredited != null ? amountCredited.hashCode() : 0);
        result = 31 * result + (invoiceItems != null ? invoiceItems.hashCode() : 0);
        return result;
    }
}
