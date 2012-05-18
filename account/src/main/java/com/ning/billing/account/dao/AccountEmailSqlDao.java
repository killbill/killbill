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

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.dao.ObjectTypeBinder;
import com.ning.billing.util.entity.collection.dao.UpdatableEntityCollectionSqlDao;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import java.util.List;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(AccountEmailMapper.class)
public interface AccountEmailSqlDao extends UpdatableEntityCollectionSqlDao<AccountEmail>, Transactional<AccountEmailSqlDao>, Transmogrifier {
    @Override
    @SqlBatch(transactional=false)
    public void insertFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @AccountEmailBinder final List<AccountEmail> entities,
                                      @CallContextBinder final CallContext context);

    @Override
    @SqlBatch(transactional=false)
    public void updateFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @AccountEmailBinder final List<AccountEmail> entities,
                                      @CallContextBinder final CallContext context);

    @Override
    @SqlBatch(transactional=false)
    public void deleteFromTransaction(@Bind("objectId") final String objectId,
                                      @ObjectTypeBinder final ObjectType objectType,
                                      @AccountEmailBinder final List<AccountEmail> entities,
                                      @CallContextBinder final CallContext context);

    @Override
    @SqlBatch(transactional=false)
    public void addHistoryFromTransaction(@Bind("objectId") final String objectId,
                                               @ObjectTypeBinder final ObjectType objectType,
                                               @AccountEmailHistoryBinder final List<EntityHistory<AccountEmail>> entities,
                                               @CallContextBinder final CallContext context);
//    @Override
//    @SqlUpdate
//    public void create(@AccountEmailBinder final AccountEmail accountEmail,
//                       @CallContextBinder final CallContext context);

//    @SqlBatch(transactional = false)
//    public void create(@AccountEmailBinder final List<AccountEmail> accountEmailList,
//                       @CallContextBinder final CallContext context);

//    @Override
//    @SqlUpdate
//    public void update(@AccountEmailBinder final AccountEmail accountEmail,
//                       @CallContextBinder final CallContext context);
//
//    @SqlBatch(transactional = false)
//    public void update(@AccountEmailBinder final List<AccountEmail> accountEmailList,
//                       @CallContextBinder final CallContext context);
//
//    @SqlUpdate
//    public void delete(@AccountEmailBinder final AccountEmail accountEmail,
//                       @CallContextBinder final CallContext context);
//
//    @SqlBatch(transactional = false)
//    public void delete(@AccountEmailBinder final List<AccountEmail> accountEmailList,
//                       @CallContextBinder final CallContext context);
//
//    @SqlBatch(transactional=false)
//    public void insertAccountEmailHistoryFromTransaction(@Bind("historyRecordId") final List<String> historyRecordIdList,
//                                                         @AccountEmailBinder final List<AccountEmail> accountEmail,
//                                                         @ChangeTypeBinder final ChangeType changeType,
//                                                         @CallContextBinder final CallContext context);
//
//    @SqlQuery
//    public List<AccountEmail> getByAccountId(@Bind("accountId") final String accountId);
}
