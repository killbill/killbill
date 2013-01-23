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

package com.ning.billing.util.entity.dao;

import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.Entity;

/**
 * ModelDao classes represent the lowest level of Entity objects. There are used to generate
 * SQL statements and retrieve objects from the database.
 *
 * @param <E> associated Entity object (used in EntitySqlDaoWrapperInvocationHandler)
 */
@SuppressWarnings("UnusedDeclaration")
public interface EntityModelDao<E extends Entity> extends Entity {

    /**
     * Retrieve the TableName associated with this entity. This is used in
     * EntitySqlDaoWrapperInvocationHandler for history and auditing purposes.
     *
     * @return the TableName object associated with this ModelDao entity
     */
    public TableName getTableName();


    public TableName getHistoryTableName();
}
