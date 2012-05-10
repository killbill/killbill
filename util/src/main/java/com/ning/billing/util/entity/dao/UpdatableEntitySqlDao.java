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

package com.ning.billing.util.entity.dao;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.entity.Entity;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

// this interface needs to be extended by an interface that provides (externalized) sql and object binders and mappers
public interface UpdatableEntitySqlDao<T extends Entity> extends EntitySqlDao<T> {
    @SqlUpdate
    public void update(final T entity, final CallContext context);

    @SqlUpdate
    public void insertHistoryFromTransaction(final EntityHistory<T> account,
                                            final CallContext context);
}
