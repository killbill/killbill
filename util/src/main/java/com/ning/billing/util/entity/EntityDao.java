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

package com.ning.billing.util.entity;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import com.ning.billing.account.api.AccountApiException;

public interface EntityDao<T extends Entity> {
    @SqlUpdate
    public void create(@BindBean final T entity) throws AccountApiException;

    @SqlUpdate
    public void update(@BindBean final T entity) throws AccountApiException;

    @SqlQuery
    public T getById(@Bind("id") final String id);

    @SqlQuery
    public List<T> get();

    @SqlUpdate
    public void test();

    @SqlUpdate
    public void deleteByKey(String key) throws AccountApiException;
}
