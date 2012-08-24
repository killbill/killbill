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

package com.ning.billing.junction.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingApiException;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.BinderBase;
import com.ning.billing.util.dao.MapperBase;

@ExternalizedSqlViaStringTemplate3()
public interface BlockingStateSqlDao extends BlockingStateDao, CloseMe, Transmogrifier {

    @Override
    @SqlUpdate
    public abstract <T extends Blockable> void setBlockingState(
            @Bind(binder = BlockingStateBinder.class) BlockingState state,
            @Bind(binder = CurrentTimeBinder.class) Clock clock);


    @Override
    @SqlQuery
    @Mapper(BlockingHistorySqlMapper.class)
    public abstract BlockingState getBlockingStateFor(@Bind(binder = BlockableBinder.class) Blockable overdueable);

    @Override
    @SqlQuery
    @Mapper(BlockingHistorySqlMapper.class)
    public abstract BlockingState getBlockingStateFor(@Bind(binder = UUIDBinder.class) UUID overdueableId);

    @Override
    @SqlQuery
    @Mapper(BlockingHistorySqlMapper.class)
    public abstract List<BlockingState> getBlockingHistoryFor(@Bind(binder = BlockableBinder.class) Blockable blockable);

    @Override
    @SqlQuery
    @Mapper(BlockingHistorySqlMapper.class)
    public abstract List<BlockingState> getBlockingHistoryFor(@Bind(binder = UUIDBinder.class) UUID blockableId);


    public class BlockingHistorySqlMapper extends MapperBase implements ResultSetMapper<BlockingState> {

        @Override
        public BlockingState map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {

            final DateTime timestamp;
            final UUID blockableId;
            final String stateName;
            final String service;
            final boolean blockChange;
            final boolean blockEntitlement;
            final boolean blockBilling;
            final Type type;
            try {
                timestamp = new DateTime(r.getDate("created_date"));
                blockableId = UUID.fromString(r.getString("id"));
                stateName = r.getString("state") == null ? BlockingApi.CLEAR_STATE_NAME : r.getString("state");
                type = Type.get(r.getString("type"));
                service = r.getString("service");
                blockChange = r.getBoolean("block_change");
                blockEntitlement = r.getBoolean("block_entitlement");
                blockBilling = r.getBoolean("block_billing");
            } catch (BlockingApiException e) {
                throw new SQLException(e);
            }
            return new DefaultBlockingState(blockableId, stateName, type, service, blockChange, blockEntitlement, blockBilling, timestamp);
        }
    }

    public static class BlockingStateSqlMapper extends MapperBase implements ResultSetMapper<String> {

        @Override
        public String map(final int index, final ResultSet r, final StatementContext ctx)
                throws SQLException {
            return r.getString("state") == null ? BlockingApi.CLEAR_STATE_NAME : r.getString("state");
        }
    }

    public static class BlockingStateBinder extends BinderBase implements Binder<Bind, DefaultBlockingState> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final DefaultBlockingState state) {
            stmt.bind("id", state.getBlockedId().toString());
            stmt.bind("state", state.getStateName().toString());
            stmt.bind("type", state.getType().toString());
            stmt.bind("service", state.getService().toString());
            stmt.bind("block_change", state.isBlockChange());
            stmt.bind("block_entitlement", state.isBlockEntitlement());
            stmt.bind("block_billing", state.isBlockBilling());
        }
    }

    public static class UUIDBinder extends BinderBase implements Binder<Bind, UUID> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final UUID id) {
            stmt.bind("id", id.toString());
        }
    }

    public static class BlockableBinder extends BinderBase implements Binder<Bind, Blockable> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final Blockable overdueable) {
            stmt.bind("id", overdueable.getId().toString());
        }
    }

    public static class OverdueStateBinder<T extends Blockable> extends BinderBase implements Binder<Bind, OverdueState<T>> {
        @Override
        public void bind(final SQLStatement<?> stmt, final Bind bind, final OverdueState<T> overdueState) {
            stmt.bind("state", overdueState.getName());
        }
    }

    public class BlockableTypeBinder extends BinderBase implements Binder<Bind, Blockable.Type> {

        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final Type type) {
            stmt.bind("type", type.name());
        }

    }

    public static class CurrentTimeBinder extends BinderBase implements Binder<Bind, Clock> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") final SQLStatement stmt, final Bind bind, final Clock clock) {
            stmt.bind("created_date", clock.getUTCNow().toDate());
        }

    }

}
