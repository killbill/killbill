/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.analytics.model.BusinessAccountTag;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(BusinessAccountTagMapper.class)
public interface BusinessAccountTagSqlDao {
    @SqlQuery
    List<BusinessAccountTag> getTagsForAccount(@Bind("account_key") final String accountKey);

    @SqlUpdate
    int addTag(@Bind("account_key") final String accountKey, @Bind("name") final String name);

    @SqlUpdate
    int removeTag(@Bind("account_key") final String accountKey, @Bind("name") final String name);

    @SqlUpdate
    void test();
}
