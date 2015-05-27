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

package org.killbill.billing.util.security.shiro.dao;

import org.joda.time.DateTime;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.stringtemplate.UseStringTemplate3StatementLocator;

@UseStringTemplate3StatementLocator
public interface JDBCSessionSqlDao extends Transactional<JDBCSessionSqlDao> {

    @SqlQuery
    public SessionModelDao read(@Bind("recordId") final Long sessionId);

    @SqlUpdate
    public void create(@SmartBindBean final SessionModelDao sessionModelDao);

    @SqlUpdate
    public void update(@SmartBindBean final SessionModelDao sessionModelDao);

    @SqlUpdate
    public void updateLastAccessTime(@Bind("lastAccessTime") final DateTime lastAccessTime, @Bind("recordId") final Long sessionId);

    @SqlUpdate
    public void delete(@SmartBindBean final SessionModelDao sessionModelDao);

    @SqlQuery
    public Long getLastInsertId();
}
