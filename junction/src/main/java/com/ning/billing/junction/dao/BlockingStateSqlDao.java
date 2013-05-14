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

package com.ning.billing.junction.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.junction.api.Type;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

@EntitySqlDaoStringTemplate
@RegisterMapper(BlockingStateSqlDao.BlockingHistorySqlMapper.class)
public interface BlockingStateSqlDao extends EntitySqlDao<BlockingStateModelDao, BlockingState> {

    @SqlQuery
    public BlockingStateModelDao getBlockingStateFor(@Bind("blockableId") UUID blockableId, @BindBean final InternalTenantContext context);

    @SqlQuery
    public abstract List<BlockingStateModelDao> getBlockingHistoryFor(@Bind("blockableId") UUID blockableId,
                                                                      @BindBean final InternalTenantContext context);

    public class BlockingHistorySqlMapper extends MapperBase implements ResultSetMapper<BlockingStateModelDao> {

        @Override
        public BlockingStateModelDao map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final UUID id;
            final UUID blockableId;
            final String stateName;
            final String service;
            final boolean blockChange;
            final boolean blockEntitlement;
            final boolean blockBilling;
            final Type type;
            final DateTime createdDate;

            try {
                id = UUID.fromString(r.getString("id"));
                blockableId = UUID.fromString(r.getString("blockable_id"));
                stateName = r.getString("state") == null ? DefaultBlockingState.CLEAR_STATE_NAME : r.getString("state");
                type = Type.get(r.getString("type"));
                service = r.getString("service");
                blockChange = r.getBoolean("block_change");
                blockEntitlement = r.getBoolean("block_entitlement");
                blockBilling = r.getBoolean("block_billing");
                createdDate = getDateTime(r, "created_date");
            } catch (BlockingApiException e) {
                throw new SQLException(e);
            }
            return new BlockingStateModelDao(id, blockableId, type, stateName, service, blockChange, blockEntitlement, blockBilling, createdDate, createdDate);
        }
    }
}
