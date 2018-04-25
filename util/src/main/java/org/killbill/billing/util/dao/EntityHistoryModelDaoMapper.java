/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

package org.killbill.billing.util.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

public class EntityHistoryModelDaoMapper<M extends EntityModelDao<E>, E extends Entity> extends MapperBase implements ResultSetMapper<EntityHistoryModelDao<M, E>> {

    private final ResultSetMapper<M> entityMapper;

    public EntityHistoryModelDaoMapper(final ResultSetMapper<M> entityMapper) {
        this.entityMapper = entityMapper;
    }

    @Override
    public EntityHistoryModelDao<M, E> map(final int index, final ResultSet r, final StatementContext ctx) throws SQLException {
        final UUID id = getUUID(r, "id");
        final long targetRecordId = r.getLong("target_record_id");
        final String changeType = r.getString("change_type");
        final DateTime createdDate = getDateTime(r, "created_date");

        // preserve history record id, as it is needed to reference it with audit log
        final long historyRecordId = r.getLong("history_record_id");

        final M entityModelDao = entityMapper.map(index, r, ctx);

        // Hack -- remove the id as it is the history id, not the entity id
        ((EntityModelDaoBase) entityModelDao).setId(null);
        // Hack -- similarly, populate the right record_id
        ((EntityModelDaoBase) entityModelDao).setRecordId(targetRecordId);
        // Hack -- account is special
        if (entityModelDao.getAccountRecordId() == null) {
            ((EntityModelDaoBase) entityModelDao).setAccountRecordId(targetRecordId);
        }

        return new EntityHistoryModelDao(id, entityModelDao, targetRecordId, ChangeType.valueOf(changeType), historyRecordId, createdDate);
    }
}
