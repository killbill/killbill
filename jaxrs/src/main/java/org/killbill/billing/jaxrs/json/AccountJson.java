/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import io.swagger.annotations.ApiModel;

@ApiModel(value="Account", parent = JsonBase.class)
public class AccountJson extends JsonBase {

    private final UUID accountId;
    private final String externalKey;
    private final BigDecimal accountCBA;
    private final BigDecimal accountBalance;
    private final String name;
    private final Integer firstNameLength;
    private final String email;
    private final Integer billCycleDayLocal;
    private final Currency currency;
    private final UUID parentAccountId;
    private final Boolean isPaymentDelegatedToParent;
    private final UUID paymentMethodId;
    private final DateTime referenceTime;
    private final String timeZone;
    private final String address1;
    private final String address2;
    private final String postalCode;
    private final String company;
    private final String city;
    private final String state;
    private final String country;
    private final String locale;
    private final String phone;
    private final String notes;
    private final Boolean isMigrated;

    public AccountJson(final Account account, final BigDecimal accountBalance, final BigDecimal accountCBA, @Nullable final AccountAuditLogs accountAuditLogs) {
        super(toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForAccount()));
        this.accountCBA = accountCBA;
        this.accountBalance = accountBalance;
        this.accountId = account.getId();
        this.externalKey = account.getExternalKey();
        this.name = account.getName();
        this.firstNameLength = account.getFirstNameLength();
        this.email = account.getEmail();
        this.billCycleDayLocal = account.getBillCycleDayLocal();
        this.currency = account.getCurrency();
        this.parentAccountId = account.getParentAccountId();
        this.isPaymentDelegatedToParent = account.isPaymentDelegatedToParent();
        this.paymentMethodId = account.getPaymentMethodId();
        this.referenceTime = account.getReferenceTime();
        this.timeZone = account.getTimeZone() != null ? account.getTimeZone().toString() : null;
        this.address1 = account.getAddress1();
        this.address2 = account.getAddress2();
        this.postalCode = account.getPostalCode();
        this.company = account.getCompanyName();
        this.city = account.getCity();
        this.state = account.getStateOrProvince();
        this.country = account.getCountry();
        this.locale = account.getLocale();
        this.phone = account.getPhone();
        this.notes = account.getNotes();
        this.isMigrated = account.isMigrated();
    }

    @JsonCreator
    public AccountJson(@JsonProperty("accountId") final UUID accountId,
                       @JsonProperty("name") final String name,
                       @JsonProperty("firstNameLength") final Integer firstNameLength,
                       @JsonProperty("externalKey") final String externalKey,
                       @JsonProperty("email") final String email,
                       @JsonProperty("billCycleDayLocal") final Integer billCycleDayLocal,
                       @JsonProperty("currency") final Currency currency,
                       @JsonProperty("parentAccountId") final UUID parentAccountId,
                       @JsonProperty("isPaymentDelegatedToParent") final Boolean isPaymentDelegatedToParent,
                       @JsonProperty("paymentMethodId") final UUID paymentMethodId,
                       @JsonProperty("referenceTime") final DateTime referenceTime,
                       @JsonProperty("timeZone") final String timeZone,
                       @JsonProperty("address1") final String address1,
                       @JsonProperty("address2") final String address2,
                       @JsonProperty("postalCode") final String postalCode,
                       @JsonProperty("company") final String company,
                       @JsonProperty("city") final String city,
                       @JsonProperty("state") final String state,
                       @JsonProperty("country") final String country,
                       @JsonProperty("locale") final String locale,
                       @JsonProperty("phone") final String phone,
                       @JsonProperty("notes") final String notes,
                       @JsonProperty("isMigrated") final Boolean isMigrated,
                       @JsonProperty("accountBalance") final BigDecimal accountBalance,
                       @JsonProperty("accountCBA") final BigDecimal accountCBA,
                       @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.accountBalance = accountBalance;
        this.externalKey = externalKey;
        this.accountId = accountId;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.email = email;
        this.billCycleDayLocal = billCycleDayLocal;
        this.currency = currency;
        this.parentAccountId = parentAccountId;
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
        this.paymentMethodId = paymentMethodId;
        this.referenceTime = referenceTime;
        this.timeZone = timeZone;
        this.address1 = address1;
        this.address2 = address2;
        this.postalCode = postalCode;
        this.company = company;
        this.city = city;
        this.state = state;
        this.country = country;
        this.locale = locale;
        this.phone = phone;
        this.notes = notes;
        this.isMigrated = isMigrated;
        this.accountCBA = accountCBA;
    }

    public Account toAccount(@Nullable final UUID accountId) {
        return new Account() {

            @Override
            public UUID getId() {
                return accountId;
            }

            @Override
            public DateTimeZone getTimeZone() {
                if (Strings.emptyToNull(timeZone) == null) {
                    return null;
                } else {
                    return DateTimeZone.forID(timeZone);
                }
            }

            @Override
            public String getStateOrProvince() {
                return state;
            }

            @Override
            public String getPostalCode() {
                return postalCode;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public String getNotes() {
                return notes;
            }

            @Override
            public Boolean isMigrated() {
                return isMigrated;
            }

            @Override
            public UUID getPaymentMethodId() {
                return paymentMethodId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getLocale() {
                return locale;
            }

            @Override
            public Integer getFirstNameLength() {
                return firstNameLength;
            }


            @Override
            public String getExternalKey() {
                return externalKey;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public Currency getCurrency() {
                return currency;
            }

            @Override
            public String getCountry() {
                return country;
            }

            @Override
            public String getCompanyName() {
                return company;
            }

            @Override
            public String getCity() {
                return city;
            }

            @Override
            public Integer getBillCycleDayLocal() {
                return billCycleDayLocal;
            }

            @Override
            public String getAddress2() {
                return address2;
            }

            @Override
            public String getAddress1() {
                return address1;
            }

            @Override
            public UUID getParentAccountId() {
                return parentAccountId;
            }

            @Override
            public Boolean isPaymentDelegatedToParent() {
                return isPaymentDelegatedToParent;
            }

            //
            // Non implemented
            //
            @Override
            public DateTimeZone getFixedOffsetTimeZone() {
                return null;
            }

            @Override
            public DateTime getReferenceTime() {
                return referenceTime;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }

            @Override
            public MutableAccountData toMutableAccountData() {
                return null;
            }

            @Override
            public Account mergeWithDelegate(final Account delegate) {
                return null;
            }
        };
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public BigDecimal getAccountCBA() {
        return accountCBA;
    }

    public String getName() {
        return name;
    }

    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    public String getEmail() {
        return email;
    }

    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    public Currency getCurrency() {
        return currency;
    }

    public UUID getParentAccountId() {
        return parentAccountId;
    }

    @JsonProperty("isPaymentDelegatedToParent")
    public Boolean isPaymentDelegatedToParent() {
        return isPaymentDelegatedToParent;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public DateTime getReferenceTime() {
        return referenceTime;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCompany() {
        return company;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }

    public String getLocale() {
        return locale;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    @JsonProperty("isMigrated")
    public Boolean isMigrated() {
        return isMigrated;
    }

    @Override
    public String toString() {
        return "AccountJson{" +
               "accountId='" + accountId + '\'' +
               ", externalKey='" + externalKey + '\'' +
               ", accountCBA=" + accountCBA +
               ", accountBalance=" + accountBalance +
               ", name='" + name + '\'' +
               ", firstNameLength=" + firstNameLength +
               ", email='" + email + '\'' +
               ", billCycleDayLocal=" + billCycleDayLocal +
               ", currency='" + currency + '\'' +
               ", parentAccountId=" + parentAccountId + '\'' +
               ", isPaymentDelegatedToParent=" + isPaymentDelegatedToParent + '\'' +
               ", paymentMethodId='" + paymentMethodId + '\'' +
               ", referenceTime='" + referenceTime + '\'' +
               ", timeZone='" + timeZone + '\'' +
               ", address1='" + address1 + '\'' +
               ", address2='" + address2 + '\'' +
               ", postalCode='" + postalCode + '\'' +
               ", company='" + company + '\'' +
               ", city='" + city + '\'' +
               ", state='" + state + '\'' +
               ", country='" + country + '\'' +
               ", locale='" + locale + '\'' +
               ", phone='" + phone + '\'' +
               ", notes='" + notes + '\'' +
               ", isMigrated=" + isMigrated +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final AccountJson that = (AccountJson) o;

        if (accountBalance != null ? accountBalance.compareTo(that.accountBalance) != 0 : that.accountBalance != null) {
            return false;
        }
        if (accountCBA != null ? accountCBA.compareTo(that.accountCBA) != 0 : that.accountCBA != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
            return false;
        }
        if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
            return false;
        }
        if (billCycleDayLocal != null ? !billCycleDayLocal.equals(that.billCycleDayLocal) : that.billCycleDayLocal != null) {
            return false;
        }
        if (city != null ? !city.equals(that.city) : that.city != null) {
            return false;
        }
        if (company != null ? !company.equals(that.company) : that.company != null) {
            return false;
        }
        if (country != null ? !country.equals(that.country) : that.country != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (parentAccountId != null ? !parentAccountId.equals(that.parentAccountId) : that.parentAccountId != null) {
            return false;
        }
        if (isPaymentDelegatedToParent != null ? !isPaymentDelegatedToParent.equals(that.isPaymentDelegatedToParent) : that.isPaymentDelegatedToParent != null) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (firstNameLength != null ? !firstNameLength.equals(that.firstNameLength) : that.firstNameLength != null) {
            return false;
        }
        if (isMigrated != null ? !isMigrated.equals(that.isMigrated) : that.isMigrated != null) {
            return false;
        }
        if (locale != null ? !locale.equals(that.locale) : that.locale != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (phone != null ? !phone.equals(that.phone) : that.phone != null) {
            return false;
        }
        if (notes != null ? !notes.equals(that.notes) : that.notes != null) {
            return false;
        }
        if (postalCode != null ? !postalCode.equals(that.postalCode) : that.postalCode != null) {
            return false;
        }
        if (state != null ? !state.equals(that.state) : that.state != null) {
            return false;
        }
        if (referenceTime != null ? referenceTime.compareTo(that.referenceTime) != 0 : that.referenceTime != null) {
            return false;
        }
        if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (accountCBA != null ? accountCBA.hashCode() : 0);
        result = 31 * result + (accountBalance != null ? accountBalance.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (firstNameLength != null ? firstNameLength.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (billCycleDayLocal != null ? billCycleDayLocal.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (parentAccountId != null ? parentAccountId.hashCode() : 0);
        result = 31 * result + (isPaymentDelegatedToParent != null ? isPaymentDelegatedToParent.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (referenceTime != null ? referenceTime.hashCode() : 0);
        result = 31 * result + (timeZone != null ? timeZone.hashCode() : 0);
        result = 31 * result + (address1 != null ? address1.hashCode() : 0);
        result = 31 * result + (address2 != null ? address2.hashCode() : 0);
        result = 31 * result + (postalCode != null ? postalCode.hashCode() : 0);
        result = 31 * result + (company != null ? company.hashCode() : 0);
        result = 31 * result + (city != null ? city.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (locale != null ? locale.hashCode() : 0);
        result = 31 * result + (phone != null ? phone.hashCode() : 0);
        result = 31 * result + (notes != null ? notes.hashCode() : 0);
        result = 31 * result + (isMigrated != null ? isMigrated.hashCode() : 0);
        return result;
    }
}
