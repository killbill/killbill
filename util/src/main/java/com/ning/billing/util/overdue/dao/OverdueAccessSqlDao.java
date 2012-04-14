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

package com.ning.billing.util.overdue.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.overdue.Overdueable;
import com.ning.billing.catalog.api.overdue.Overdueable.Type;
import com.ning.billing.util.entity.BinderBase;
import com.ning.billing.util.entity.MapperBase;
import com.ning.billing.util.overdue.OverdueAccessApi;
import com.ning.billing.util.overdue.OverdueEvent;

@ExternalizedSqlViaStringTemplate3()
public interface OverdueAccessSqlDao extends Transactional<OverdueAccessSqlDao>, CloseMe, Transmogrifier, OverdueAccessDao {

    @Override
    @SqlQuery
    @Mapper(OverdueStateSqlMapper.class)
    public abstract String getOverdueStateFor(@Bind(binder = OverdueableBinder.class)Overdueable overdueable) ;
    
    @Override
    @SqlQuery
    @Mapper(OverdueStateSqlMapper.class)
    public abstract String getOverdueStateForIdAndType(@Bind(binder = UUIDBinder.class) UUID overdueableId, @Bind(binder = OverdueableTypeBinder.class)  Type type);

    @Override
    @SqlQuery
    @Mapper(OverdueHistorySqlMapper.class)
    public abstract SortedSet<OverdueEvent> getOverdueHistoryFor(@Bind(binder = OverdueableBinder.class)Overdueable overdueable) ;
    
    @Override
    @SqlQuery
    @Mapper(OverdueHistorySqlMapper.class)
    public abstract SortedSet<OverdueEvent> getOverdueHistoryForIdAndType(@Bind(binder = UUIDBinder.class) UUID overdueableId, @Bind(binder = OverdueableTypeBinder.class)  Type type);


    public class OverdueHistorySqlMapper extends MapperBase implements ResultSetMapper<OverdueEvent> {

        @Override
        public OverdueEvent map(int index, ResultSet r, StatementContext ctx)
                throws SQLException {
            
            DateTime timestamp;
            UUID overdueableId;
            String stateName;
            Type type;
            try {
                timestamp = new DateTime(r.getDate("created_date"));
                overdueableId = UUID.fromString(r.getString("id"));
                stateName = r.getString("state") == null ? OverdueAccessApi.CLEAR_STATE_NAME : r.getString("state");
                type = Type.get(r.getString("type"));
            } catch (CatalogApiException e) {
                throw new SQLException(e);
            }
            return new OverdueEvent(overdueableId, stateName, type, timestamp);
        }    
    }
    
    public static class OverdueStateSqlMapper extends MapperBase implements ResultSetMapper<String> {

        @Override
        public String map(int index, ResultSet r, StatementContext ctx)
                throws SQLException {
            return r.getString("state") == null ? OverdueAccessApi.CLEAR_STATE_NAME : r.getString("state");
        }
    }
    
    public static class UUIDBinder extends BinderBase implements Binder<Bind, UUID> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, UUID id) {
            stmt.bind("id", id.toString());
        }
    }


    
    public static class OverdueableBinder extends BinderBase implements Binder<Bind, Overdueable> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Overdueable overdueable) {
            stmt.bind("id", overdueable.getId().toString());
        }
    }
   
    public class OverdueableTypeBinder extends BinderBase implements Binder<Bind, Overdueable.Type>{

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Type type) {
            stmt.bind("type", type.name());
        }

    }


}
