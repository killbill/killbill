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

package org.killbill.billing.account.dao;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface AccountDao extends EntityDao<AccountModelDao, Account, AccountApiException> {

    AccountModelDao getAccountByKey(String key, InternalTenantContext context);

    Pagination<AccountModelDao> searchAccounts(String searchKey, Long offset, Long limit, InternalTenantContext context);

    /**
     * @throws AccountApiException when externalKey is null
     */
    UUID getIdFromKey(String externalKey, InternalTenantContext context) throws AccountApiException;

    /**
     * @param accountId       the id of the account
     * @param paymentMethodId the is of the current default paymentMethod
     */
    void updatePaymentMethod(UUID accountId, UUID paymentMethodId, InternalCallContext context) throws AccountApiException;

    void update(AccountModelDao account, InternalCallContext context) throws AccountApiException;

    void addEmail(AccountEmailModelDao email, InternalCallContext context) throws AccountApiException;

    void removeEmail(AccountEmailModelDao email, InternalCallContext context);

    List<AccountEmailModelDao> getEmailsByAccountId(UUID accountId, InternalTenantContext context);

    Integer getAccountBCD(UUID accountId, InternalTenantContext context);

    List<AccountModelDao> getAccountsByParentId(UUID parentAccountId, InternalTenantContext context);

    List<AuditLogWithHistory> getAuditLogsWithHistoryForId(UUID accountId, AuditLevel auditLevel, InternalTenantContext context) throws AccountApiException;

    List<AuditLogWithHistory> getEmailAuditLogsWithHistoryForId(UUID accountEmailId, AuditLevel auditLevel, InternalTenantContext context) throws AccountApiException;

}
