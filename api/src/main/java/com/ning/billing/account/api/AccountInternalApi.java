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

package com.ning.billing.account.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.Pagination;

public interface AccountInternalApi {

    public Account getAccountByKey(String key, InternalTenantContext context) throws AccountApiException;

    public Account getAccountById(UUID accountId, InternalTenantContext context) throws AccountApiException;

    public Account getAccountByRecordId(Long recordId, InternalTenantContext context) throws AccountApiException;

    public Pagination<Account> getAccounts(Long offset, Long limit, InternalTenantContext context);

    public void updateAccount(String key, AccountData accountData, InternalCallContext context) throws AccountApiException;

    public List<AccountEmail> getEmails(UUID accountId, InternalTenantContext context);

    public void removePaymentMethod(UUID accountId, InternalCallContext context) throws AccountApiException;

    public void updatePaymentMethod(UUID accountId, UUID paymentMethodId, InternalCallContext context) throws AccountApiException;

    public UUID getByRecordId(Long recordId, InternalTenantContext context) throws AccountApiException;
}
