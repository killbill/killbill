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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;

public class BusinessAccount extends BusinessEntityBase {

    private final String email;
    private final Integer firstNameLength;
    private final String currency;
    private final Integer billingCycleDayLocal;
    private final String address1;
    private final String address2;
    private final String companyName;
    private final String city;
    private final String stateOrProvince;
    private final String country;
    private final String postalCode;
    private final String phone;
    private final Boolean isMigrated;
    private final Boolean isNotifiedForInvoices;
    private final BigDecimal balance;
    private final LocalDate lastInvoiceDate;
    private final DateTime lastPaymentDate;
    private final String lastPaymentStatus;

    public BusinessAccount(final BusinessAccountModelDao businessAccountModelDao) {
        super(businessAccountModelDao.getCreatedDate(),
              businessAccountModelDao.getCreatedBy(),
              businessAccountModelDao.getCreatedReasonCode(),
              businessAccountModelDao.getCreatedComments(),
              businessAccountModelDao.getAccountId(),
              businessAccountModelDao.getAccountName(),
              businessAccountModelDao.getAccountExternalKey());
        this.email = businessAccountModelDao.getEmail();
        this.firstNameLength = businessAccountModelDao.getFirstNameLength();
        this.currency = businessAccountModelDao.getCurrency();
        this.billingCycleDayLocal = businessAccountModelDao.getBillingCycleDayLocal();
        this.address1 = businessAccountModelDao.getAddress1();
        this.address2 = businessAccountModelDao.getAddress2();
        this.companyName = businessAccountModelDao.getCompanyName();
        this.city = businessAccountModelDao.getCity();
        this.stateOrProvince = businessAccountModelDao.getStateOrProvince();
        this.country = businessAccountModelDao.getCountry();
        this.postalCode = businessAccountModelDao.getPostalCode();
        this.phone = businessAccountModelDao.getPhone();
        this.isMigrated = businessAccountModelDao.getMigrated();
        this.isNotifiedForInvoices = businessAccountModelDao.getNotifiedForInvoices();
        this.balance = businessAccountModelDao.getBalance();
        this.lastInvoiceDate = businessAccountModelDao.getLastInvoiceDate();
        this.lastPaymentDate = businessAccountModelDao.getLastPaymentDate();
        this.lastPaymentStatus = businessAccountModelDao.getLastPaymentStatus();
    }
}
