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

package com.ning.billing.overdue.dao;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.catalog.api.overdue.OverdueState;
import com.ning.billing.catalog.api.overdue.Overdueable;
import com.ning.billing.catalog.api.overdue.Overdueable.Type;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.entity.BinderBase;

@ExternalizedSqlViaStringTemplate3()
public interface OverdueSqlDao extends OverdueDao, CloseMe, Transmogrifier {

    @Override
    @SqlUpdate
    public abstract <T extends Overdueable> void setOverdueState(
            @Bind(binder = OverdueableBinder.class) T overdueable, 
            @Bind(binder = OverdueStateBinder.class) OverdueState<T> overdueState,
            @Bind(binder = OverdueableTypeBinder.class) Overdueable.Type type,
            @Bind(binder = CurrentTimeBinder.class) Clock clock) ;

    public static class OverdueableBinder extends BinderBase implements Binder<Bind, Overdueable> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Overdueable overdueable) {
            stmt.bind("id", overdueable.getId().toString());
        }
    }
    
    public static class OverdueStateBinder<T extends Overdueable> extends BinderBase implements Binder<Bind, OverdueState<T>> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, OverdueState<T> overdueState) {
            stmt.bind("state", overdueState.getName());
        }
    }
    
    public class OverdueableTypeBinder extends BinderBase implements Binder<Bind, Overdueable.Type>{

        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Type type) {
            stmt.bind("type", type.name());
        }

    }

    public static class CurrentTimeBinder extends BinderBase implements Binder<Bind, Clock> {
        @Override
        public void bind(@SuppressWarnings("rawtypes") SQLStatement stmt, Bind bind, Clock clock) {
            stmt.bind("created_date", clock.getUTCNow().toDate());
        }
        
    }
}
