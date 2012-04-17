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

import java.sql.DataTruncation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.AuditedDaoBase;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.dao.TagDao;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.AccountCreationEvent;
import com.ning.billing.account.api.user.DefaultAccountChangeNotification;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.tag.Tag;

public class AuditedAccountDao extends AuditedDaoBase implements AccountDao {
    private static final String ACCOUNT_EMAIL_HISTORY_TABLE = "account_email_history";
    private final AccountSqlDao accountSqlDao;
    private final AccountEmailSqlDao accountEmailSqlDao;
    private final TagDao tagDao;
    private final CustomFieldDao customFieldDao;
    private final Bus eventBus;

    @Inject
    public AuditedAccountDao(IDBI dbi, Bus eventBus, TagDao tagDao, CustomFieldDao customFieldDao) {
        this.eventBus = eventBus;
        this.accountSqlDao = dbi.onDemand(AccountSqlDao.class);
        this.accountEmailSqlDao = dbi.onDemand(AccountEmailSqlDao.class);
        this.tagDao = tagDao;
        this.customFieldDao = customFieldDao;
    }

    @Override
    public Account getAccountByKey(final String key) {
        return accountSqlDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
                Account account = accountSqlDao.getAccountByKey(key);
                if (account != null) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }
                return account;
            }
        });
    }

    @Override
    public UUID getIdFromKey(final String externalKey) throws AccountApiException {
        if (externalKey == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_CANNOT_MAP_NULL_KEY, "");
        }
        return accountSqlDao.getIdFromKey(externalKey);
    }

    @Override
    public Account getById(final String id) {
        return accountSqlDao.inTransaction(new Transaction<Account, AccountSqlDao>() {
            @Override
            public Account inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
                Account account = accountSqlDao.getById(id);
                if (account != null) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }
                return account;
            }
        });
    }

    @Override
    public List<Account> get() {
        return accountSqlDao.inTransaction(new Transaction<List<Account>, AccountSqlDao>() {
            @Override
            public List<Account> inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws Exception {
                List<Account> accounts = accountSqlDao.get();
                for (Account account : accounts) {
                    setCustomFieldsFromWithinTransaction(account, accountSqlDao);
                    setTagsFromWithinTransaction(account, accountSqlDao);
                }

                return accounts;
            }
        });
    }

    @Override
    public void create(final Account account, final CallContext context) throws EntityPersistenceException {
        final String key = account.getExternalKey();
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao transactionalDao, final TransactionStatus status) throws AccountApiException, Bus.EventBusException {
                    Account currentAccount = transactionalDao.getAccountByKey(key);
                    if (currentAccount != null) {
                        throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, key);
                    }
                    transactionalDao.create(account, context);
                    UUID historyId = UUID.randomUUID();

                    AccountHistorySqlDao historyDao = accountSqlDao.become(AccountHistorySqlDao.class);
                    historyDao.insertAccountHistoryFromTransaction(account, historyId.toString(),
                            ChangeType.INSERT.toString(), context);

                    AuditSqlDao auditDao = accountSqlDao.become(AuditSqlDao.class);
                    auditDao.insertAuditFromTransaction("account_history", historyId.toString(),
                                                         ChangeType.INSERT, context);

                    saveTagsFromWithinTransaction(account, transactionalDao, context);
                    saveCustomFieldsFromWithinTransaction(account, transactionalDao, context);
                    AccountCreationEvent creationEvent = new DefaultAccountCreationEvent(account, context.getUserToken());
                    eventBus.post(creationEvent);
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof EntityPersistenceException) {
                throw (EntityPersistenceException) re.getCause();
            } else if (re.getCause() instanceof DataTruncation) {
                throw new EntityPersistenceException(ErrorCode.DATA_TRUNCATION, re.getCause().getMessage());
            } else {
                throw re;
            }
        }
    }

    @Override
    public void update(final Account account, final CallContext context) throws EntityPersistenceException {
        try {
            accountSqlDao.inTransaction(new Transaction<Void, AccountSqlDao>() {
                @Override
                public Void inTransaction(final AccountSqlDao accountSqlDao, final TransactionStatus status) throws EntityPersistenceException, Bus.EventBusException {
                    String accountId = account.getId().toString();
                    Account currentAccount = accountSqlDao.getById(accountId);
                    if (currentAccount == null) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
                    }

                    String currentKey = currentAccount.getExternalKey();
                    if (!currentKey.equals(account.getExternalKey())) {
                        throw new EntityPersistenceException(ErrorCode.ACCOUNT_CANNOT_CHANGE_EXTERNAL_KEY, currentKey);
                    }

                    accountSqlDao.update(account, context);

                    UUID historyId = UUID.randomUUID();
                    AccountHistorySqlDao historyDao = accountSqlDao.become(AccountHistorySqlDao.class);
                    historyDao.insertAccountHistoryFromTransaction(account, historyId.toString(), ChangeType.UPDATE.toString(), context);

                    AuditSqlDao auditDao = accountSqlDao.become(AuditSqlDao.class);
                    auditDao.insertAuditFromTransaction("account_history" ,historyId.toString(),
                                                        ChangeType.INSERT, context);

                    saveTagsFromWithinTransaction(account, accountSqlDao, context);
                    saveCustomFieldsFromWithinTransaction(account, accountSqlDao, context);

                    AccountChangeEvent changeEvent = new DefaultAccountChangeNotification(account.getId(), context.getUserToken(), currentAccount, account);
                    if (changeEvent.hasChanges()) {
                        eventBus.post(changeEvent);
                    }
                    return null;
                }
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof EntityPersistenceException) {
                throw (EntityPersistenceException) re.getCause();
            } else {
                throw re;
            }
        }
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId) {
        return accountEmailSqlDao.getByAccountId(accountId.toString());
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final CallContext context) {
        final List<AccountEmail> existingEmails = accountEmailSqlDao.getByAccountId(accountId.toString());
        final List<AccountEmail> updatedEmails = new ArrayList<AccountEmail>();

        Iterator<AccountEmail> existingEmailIterator = existingEmails.iterator();
        while (existingEmailIterator.hasNext()) {
            AccountEmail existingEmail = existingEmailIterator.next();

            Iterator<AccountEmail> newEmailIterator = emails.iterator();
            while (newEmailIterator.hasNext()) {
                AccountEmail newEmail = newEmailIterator.next();
                if (newEmail.getId().equals(existingEmail.getId())) {
                    // check equality; if not equal, add to updated
                    if (!newEmail.equals(existingEmail)) {
                        updatedEmails.add(newEmail);
                    }

                    // remove from both
                    newEmailIterator.remove();
                    existingEmailIterator.remove();
                }
            }
        }

        // remaining emails in newEmail are inserts; remaining emails in existingEmail are deletes
        accountEmailSqlDao.inTransaction(new Transaction<Void, AccountEmailSqlDao>() {
            @Override
            public Void inTransaction(AccountEmailSqlDao dao, TransactionStatus transactionStatus) throws Exception {
                dao.create(emails, context);
                dao.update(updatedEmails, context);
                dao.delete(existingEmails, context);

                List<String> insertHistoryIdList = getIdList(emails.size());
                List<String> updateHistoryIdList = getIdList(updatedEmails.size());
                List<String> deleteHistoryIdList = getIdList(existingEmails.size());

                // insert histories
                dao.insertAccountEmailHistoryFromTransaction(insertHistoryIdList, emails, ChangeType.INSERT, context);
                dao.insertAccountEmailHistoryFromTransaction(updateHistoryIdList, updatedEmails, ChangeType.UPDATE, context);
                dao.insertAccountEmailHistoryFromTransaction(deleteHistoryIdList, existingEmails, ChangeType.DELETE, context);

                // insert audits
                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(ACCOUNT_EMAIL_HISTORY_TABLE, insertHistoryIdList, ChangeType.INSERT, context);
                auditSqlDao.insertAuditFromTransaction(ACCOUNT_EMAIL_HISTORY_TABLE, updateHistoryIdList, ChangeType.UPDATE, context);
                auditSqlDao.insertAuditFromTransaction(ACCOUNT_EMAIL_HISTORY_TABLE, deleteHistoryIdList, ChangeType.DELETE, context);

                return null;
            }
        });
    }

    @Override
    public void test() {
        accountSqlDao.test();
    }

    private void setCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        CustomFieldSqlDao customFieldSqlDao = transactionalDao.become(CustomFieldSqlDao.class);
        List<CustomField> fields = customFieldSqlDao.load(account.getId().toString(), account.getObjectName());

        account.clearFields();
        if (fields != null) {
            account.setFields(fields);
        }
    }

    private void setTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao) {
        List<Tag> tags = tagDao.loadTagsFromTransaction(transactionalDao, account.getId(), Account.ObjectType);
        account.clearTags();

        if (tags != null) {
            account.addTags(tags);
        }
    }

    private void saveTagsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao,
                                               final CallContext context) {
        tagDao.saveTagsFromTransaction(transactionalDao, account.getId(), account.getObjectName(), account.getTagList(), context);
    }

    private void saveCustomFieldsFromWithinTransaction(final Account account, final AccountSqlDao transactionalDao,
                                                       final CallContext context) {
        customFieldDao.saveFields(transactionalDao, account.getId(), account.getObjectName(), account.getFieldList(), context);
    }


}
