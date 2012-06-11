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

package com.ning.billing.account.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;

import com.google.inject.Inject;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.AuditedCollectionDaoBase;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;

public class AuditedAccountEmailDao extends AuditedCollectionDaoBase<AccountEmail, AccountEmail> implements AccountEmailDao {
    private final AccountEmailSqlDao accountEmailSqlDao;

    @Inject
    public AuditedAccountEmailDao(IDBI dbi) {
        this.accountEmailSqlDao = dbi.onDemand(AccountEmailSqlDao.class);
    }

    @Override
    protected AccountEmail getEquivalenceObjectFor(AccountEmail obj) {
        return obj;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId) {
        return new ArrayList<AccountEmail>(super.loadEntities(accountId, ObjectType.ACCOUNT_EMAIL).values());
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final CallContext context) {
        super.saveEntities(accountId, ObjectType.ACCOUNT_EMAIL, emails, context);
    }

    @Override
    public String getKey(AccountEmail entity) {
        return entity.getEmail();
    }

    @Override
    public void test() {
        accountEmailSqlDao.test();
    }

//    @Override
//    public List<AccountEmail> getEmails(final UUID accountId) {
//        return accountEmailSqlDao.load(accountId.toString(), null);
//        //return accountEmailSqlDao.getByAccountId(accountId.toString());
//    }

//    @Override
//    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final CallContext context) {
//        final List<AccountEmail> existingEmails = accountEmailSqlDao.getByAccountId(accountId.toString());
//        final List<AccountEmail> updatedEmails = new ArrayList<AccountEmail>();
//
//        Iterator<AccountEmail> existingEmailIterator = existingEmails.iterator();
//        while (existingEmailIterator.hasNext()) {
//            AccountEmail existingEmail = existingEmailIterator.next();
//
//            Iterator<AccountEmail> newEmailIterator = emails.iterator();
//            while (newEmailIterator.hasNext()) {
//                AccountEmail newEmail = newEmailIterator.next();
//                if (newEmail.getId().equals(existingEmail.getId())) {
//                    // check equality; if not equal, add to updated
//                    if (!newEmail.equals(existingEmail)) {
//                        updatedEmails.add(newEmail);
//                    }
//
//                    // remove from both
//                    newEmailIterator.remove();
//                    existingEmailIterator.remove();
//                }
//            }
//        }
//
//        // remaining emails in newEmail are inserts; remaining emails in existingEmail are deletes
//        accountEmailSqlDao.inTransaction(new Transaction<Void, AccountEmailSqlDao>() {
//            @Override
//            public Void inTransaction(AccountEmailSqlDao dao, TransactionStatus transactionStatus) throws Exception {
//                dao.create(emails, context);
//                dao.update(updatedEmails, context);
//                dao.delete(existingEmails, context);
//
//                List<String> insertHistoryIdList = getIdList(emails.size());
//                List<String> updateHistoryIdList = getIdList(updatedEmails.size());
//                List<String> deleteHistoryIdList = getIdList(existingEmails.size());
//
//                // insert histories
//                dao.insertAccountEmailHistoryFromTransaction(insertHistoryIdList, emails, ChangeType.INSERT, context);
//                dao.insertAccountEmailHistoryFromTransaction(updateHistoryIdList, updatedEmails, ChangeType.UPDATE, context);
//                dao.insertAccountEmailHistoryFromTransaction(deleteHistoryIdList, existingEmails, ChangeType.DELETE, context);
//
//                // insert audits
//                auditSqlDao.insertAuditFromTransaction(TableName.ACCOUNT_EMAIL_HISTORY, insertHistoryIdList, ChangeType.INSERT, context);
//                auditSqlDao.insertAuditFromTransaction(TableName.ACCOUNT_EMAIL_HISTORY, updateHistoryIdList, ChangeType.UPDATE, context);
//                auditSqlDao.insertAuditFromTransaction(TableName.ACCOUNT_EMAIL_HISTORY, deleteHistoryIdList, ChangeType.DELETE, context);
//
//                return null;
//            }
//        });
//    }
//
//    private List<String> getIdList(int size) {
//        List<String> results = new ArrayList<String>();
//        for (int i = 0; i < size; i++) {
//            results.add(UUID.randomUUID().toString());
//        }
//        return results;
//    }

    @Override
    protected TableName getTableName() {
        return TableName.ACCOUNT_EMAIL_HISTORY;
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<AccountEmail> transmogrifyDao(Transmogrifier transactionalDao) {
        return transactionalDao.become(AccountEmailSqlDao.class);
    }

    @Override
    protected UpdatableEntityCollectionSqlDao<AccountEmail> getSqlDao() {
        return accountEmailSqlDao;
    }
}
