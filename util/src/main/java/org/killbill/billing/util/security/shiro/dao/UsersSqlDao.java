/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.security.shiro.dao;

import java.util.Date;

import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@EntitySqlDaoStringTemplate
public interface UsersSqlDao extends Transactional<UsersSqlDao> {

    @SqlQuery
    public UserModelDao getByRecordId(@Bind("recordId") Long recordId);

    @SqlQuery
    public UserModelDao getByUsername(@Bind("username") String username);

    @SqlUpdate
    public void create(@SmartBindBean UserModelDao userModelDao);

    @SqlUpdate
    public void updatePassword(@Bind("username") String username,
                               @Bind("password") String password,
                               @Bind("passwordSalt") String passwordSalt,
                               @Bind("updatedDate") Date updatedDate,
                               @Bind("updatedBy") String updatedBy);

    @SqlUpdate
    public void invalidate(@Bind("username") String username,
                           @Bind("updatedDate") Date updatedDate,
                           @Bind("updatedBy") String updatedBy);
}
