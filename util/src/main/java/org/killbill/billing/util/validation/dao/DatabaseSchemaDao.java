/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.validation.dao;

import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

import org.killbill.billing.util.entity.dao.DBRouter;
import org.killbill.billing.util.validation.DefaultColumnInfo;
import org.skife.jdbi.v2.IDBI;

import com.google.inject.Inject;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

@Singleton
public class DatabaseSchemaDao {

    private final DBRouter<DatabaseSchemaSqlDao> dbRouter;

    @Inject
    public DatabaseSchemaDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi) {
        this.dbRouter = new DBRouter<DatabaseSchemaSqlDao>(dbi, roDbi, DatabaseSchemaSqlDao.class);
    }

    public List<DefaultColumnInfo> getColumnInfoList() {
        return getColumnInfoList(null);
    }

    public List<DefaultColumnInfo> getColumnInfoList(@Nullable final String schemaName) {
        return dbRouter.onDemand(true).getSchemaInfo(schemaName);
    }
}
