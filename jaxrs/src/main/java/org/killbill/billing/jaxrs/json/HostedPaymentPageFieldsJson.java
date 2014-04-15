/*
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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.Map;

import org.killbill.billing.catalog.api.Currency;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class HostedPaymentPageFieldsJson extends JsonBase {

    private final String credential2;
    private final String credential3;
    private final String credential4;
    private final BigDecimal amount;
    private final Currency currency;
    private final String transactionType;
    private final String authCode;
    private final String notifyUrl;
    private final String returnUrl;
    private final String forwardUrl;
    private final String cancelReturnUrl;
    private final String redirectParam;
    private final String accountName;
    private final HostedPaymentPageCustomerJson customer;
    private final HostedPaymentPageBillingAddressJson billingAddress;
    private final String order;
    private final String description;
    private final String tax;
    private final String shipping;
    private final Map<String, String> customFields;

    @JsonCreator
    public HostedPaymentPageFieldsJson(@JsonProperty("credential2") final String credential2,
                                       @JsonProperty("credential3") final String credential3,
                                       @JsonProperty("credential4") final String credential4,
                                       @JsonProperty("amount") final BigDecimal amount,
                                       @JsonProperty("currency") final Currency currency,
                                       @JsonProperty("transactionType") final String transactionType,
                                       @JsonProperty("authCode") final String authCode,
                                       @JsonProperty("notifyUrl") final String notifyUrl,
                                       @JsonProperty("returnUrl") final String returnUrl,
                                       @JsonProperty("forwardUrl") final String forwardUrl,
                                       @JsonProperty("cancelReturnUrl") final String cancelReturnUrl,
                                       @JsonProperty("redirectParam") final String redirectParam,
                                       @JsonProperty("accountName") final String accountName,
                                       @JsonProperty("customer") final HostedPaymentPageCustomerJson customer,
                                       @JsonProperty("billingAddress") final HostedPaymentPageBillingAddressJson billingAddress,
                                       @JsonProperty("order") final String order,
                                       @JsonProperty("description") final String description,
                                       @JsonProperty("tax") final String tax,
                                       @JsonProperty("shipping") final String shipping,
                                       @JsonProperty("customFields") final Map<String, String> customFields) {
        this.credential2 = credential2;
        this.credential3 = credential3;
        this.credential4 = credential4;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.authCode = authCode;
        this.notifyUrl = notifyUrl;
        this.returnUrl = returnUrl;
        this.forwardUrl = forwardUrl;
        this.cancelReturnUrl = cancelReturnUrl;
        this.redirectParam = redirectParam;
        this.accountName = accountName;
        this.customer = customer;
        this.billingAddress = billingAddress;
        this.order = order;
        this.description = description;
        this.tax = tax;
        this.shipping = shipping;
        this.customFields = customFields;
    }

    public String getCredential2() {
        return credential2;
    }

    public String getCredential3() {
        return credential3;
    }

    public String getCredential4() {
        return credential4;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getAuthCode() {
        return authCode;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getForwardUrl() {
        return forwardUrl;
    }

    public String getCancelReturnUrl() {
        return cancelReturnUrl;
    }

    public String getRedirectParam() {
        return redirectParam;
    }

    public String getAccountName() {
        return accountName;
    }

    public HostedPaymentPageCustomerJson getCustomer() {
        return customer;
    }

    public HostedPaymentPageBillingAddressJson getBillingAddress() {
        return billingAddress;
    }

    public String getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }

    public String getTax() {
        return tax;
    }

    public String getShipping() {
        return shipping;
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HostedPaymentPageFieldsJson{");
        sb.append("credential2='").append(credential2).append('\'');
        sb.append(", credential3='").append(credential3).append('\'');
        sb.append(", credential4='").append(credential4).append('\'');
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", transactionType='").append(transactionType).append('\'');
        sb.append(", authCode='").append(authCode).append('\'');
        sb.append(", notifyUrl='").append(notifyUrl).append('\'');
        sb.append(", returnUrl='").append(returnUrl).append('\'');
        sb.append(", forwardUrl='").append(forwardUrl).append('\'');
        sb.append(", cancelReturnUrl='").append(cancelReturnUrl).append('\'');
        sb.append(", redirectParam='").append(redirectParam).append('\'');
        sb.append(", accountName='").append(accountName).append('\'');
        sb.append(", customer=").append(customer);
        sb.append(", billingAddress=").append(billingAddress);
        sb.append(", order='").append(order).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", tax='").append(tax).append('\'');
        sb.append(", shipping='").append(shipping).append('\'');
        sb.append(", customFields=").append(customFields);
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

        final HostedPaymentPageFieldsJson that = (HostedPaymentPageFieldsJson) o;

        if (accountName != null ? !accountName.equals(that.accountName) : that.accountName != null) {
            return false;
        }
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) {
            return false;
        }
        if (authCode != null ? !authCode.equals(that.authCode) : that.authCode != null) {
            return false;
        }
        if (billingAddress != null ? !billingAddress.equals(that.billingAddress) : that.billingAddress != null) {
            return false;
        }
        if (cancelReturnUrl != null ? !cancelReturnUrl.equals(that.cancelReturnUrl) : that.cancelReturnUrl != null) {
            return false;
        }
        if (credential2 != null ? !credential2.equals(that.credential2) : that.credential2 != null) {
            return false;
        }
        if (credential3 != null ? !credential3.equals(that.credential3) : that.credential3 != null) {
            return false;
        }
        if (credential4 != null ? !credential4.equals(that.credential4) : that.credential4 != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (customFields != null ? !customFields.equals(that.customFields) : that.customFields != null) {
            return false;
        }
        if (customer != null ? !customer.equals(that.customer) : that.customer != null) {
            return false;
        }
        if (description != null ? !description.equals(that.description) : that.description != null) {
            return false;
        }
        if (forwardUrl != null ? !forwardUrl.equals(that.forwardUrl) : that.forwardUrl != null) {
            return false;
        }
        if (notifyUrl != null ? !notifyUrl.equals(that.notifyUrl) : that.notifyUrl != null) {
            return false;
        }
        if (order != null ? !order.equals(that.order) : that.order != null) {
            return false;
        }
        if (redirectParam != null ? !redirectParam.equals(that.redirectParam) : that.redirectParam != null) {
            return false;
        }
        if (returnUrl != null ? !returnUrl.equals(that.returnUrl) : that.returnUrl != null) {
            return false;
        }
        if (shipping != null ? !shipping.equals(that.shipping) : that.shipping != null) {
            return false;
        }
        if (tax != null ? !tax.equals(that.tax) : that.tax != null) {
            return false;
        }
        if (transactionType != null ? !transactionType.equals(that.transactionType) : that.transactionType != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = credential2 != null ? credential2.hashCode() : 0;
        result = 31 * result + (credential3 != null ? credential3.hashCode() : 0);
        result = 31 * result + (credential4 != null ? credential4.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (transactionType != null ? transactionType.hashCode() : 0);
        result = 31 * result + (authCode != null ? authCode.hashCode() : 0);
        result = 31 * result + (notifyUrl != null ? notifyUrl.hashCode() : 0);
        result = 31 * result + (returnUrl != null ? returnUrl.hashCode() : 0);
        result = 31 * result + (forwardUrl != null ? forwardUrl.hashCode() : 0);
        result = 31 * result + (cancelReturnUrl != null ? cancelReturnUrl.hashCode() : 0);
        result = 31 * result + (redirectParam != null ? redirectParam.hashCode() : 0);
        result = 31 * result + (accountName != null ? accountName.hashCode() : 0);
        result = 31 * result + (customer != null ? customer.hashCode() : 0);
        result = 31 * result + (billingAddress != null ? billingAddress.hashCode() : 0);
        result = 31 * result + (order != null ? order.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (tax != null ? tax.hashCode() : 0);
        result = 31 * result + (shipping != null ? shipping.hashCode() : 0);
        result = 31 * result + (customFields != null ? customFields.hashCode() : 0);
        return result;
    }
}
