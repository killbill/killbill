/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.account.api;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;

public interface AccountInternalApi extends ImmutableAccountInternalApi {

    Account getAccountByKey(String key, InternalTenantContext context) throws AccountApiException;

    Account getAccountById(UUID accountId, InternalTenantContext context) throws AccountApiException;

    Account getAccountByRecordId(Long recordId, InternalTenantContext context) throws AccountApiException;

    void updateBCD(String key, int bcd, InternalCallContext context) throws AccountApiException;

    int getBCD(UUID accountId, InternalTenantContext context) throws AccountApiException;

    List<AccountEmail> getEmails(UUID accountId, InternalTenantContext context);

    void removePaymentMethod(UUID accountId, InternalCallContext context) throws AccountApiException;

    void updatePaymentMethod(UUID accountId, UUID paymentMethodId, InternalCallContext context) throws AccountApiException;

    UUID getByRecordId(Long recordId, InternalTenantContext context) throws AccountApiException;

    List<Account> getChildrenAccounts(UUID parentAccountId, InternalCallContext context) throws AccountApiException;
}
