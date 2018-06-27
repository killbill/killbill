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

package org.killbill.billing.beatrix.util;

import java.util.UUID;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.util.callcontext.CallContext;

public class AccountChecker {

    private static final Logger log = LoggerFactory.getLogger(AccountChecker.class);

    private final AccountUserApi accountApi;
    private final AuditChecker auditChecker;

    @Inject
    public AccountChecker(final AccountUserApi accountApi, final AuditChecker auditChecker) {
        this.accountApi = accountApi;
        this.auditChecker = auditChecker;
    }

    public Account checkAccount(final UUID accountId, final AccountData accountData, final CallContext context) throws Exception {

        final Account account = accountApi.getAccountById(accountId, context);
        // Not all test pass it, since this is always the same test
        if (accountData != null) {
            Assert.assertEquals(account.getName(), accountData.getName());
            Assert.assertEquals(account.getFirstNameLength(), accountData.getFirstNameLength());
            Assert.assertEquals(account.getEmail(), accountData.getEmail());
            Assert.assertEquals(account.getPhone(), accountData.getPhone());
            Assert.assertEquals(account.getExternalKey(), accountData.getExternalKey());
            Assert.assertEquals(account.getBillCycleDayLocal(), accountData.getBillCycleDayLocal());
            Assert.assertEquals(account.getCurrency(), accountData.getCurrency());
            Assert.assertEquals(account.getTimeZone(), accountData.getTimeZone());
            // createWithPaymentMethod will update the paymentMethod
            //Assert.assertEquals(account.getPaymentMethodId(), accountData.getPaymentMethodId());
        }

        auditChecker.checkAccountCreated(account, context);
        return account;
    }
}
