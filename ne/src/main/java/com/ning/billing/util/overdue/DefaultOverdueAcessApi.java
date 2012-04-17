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

package com.ning.billing.util.overdue;

import java.util.SortedSet;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.ne.override.dao.OverrideStateDao;
import com.ning.billing.overdue.OverdueAccessApi;
import com.ning.billing.overdue.OverdueEvent;
import com.ning.billing.overdue.config.api.OverdueState;
import com.ning.billing.overdue.config.api.Overdueable;
import com.ning.billing.overdue.config.api.Overdueable.Type;
import com.ning.billing.util.clock.Clock;

public class DefaultOverdueAcessApi implements OverdueAccessApi {
    private OverrideStateDao dao;
    private Clock clock;

    @Inject
    public DefaultOverdueAcessApi(OverrideStateDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }
    
    @Override
    public String getOverdueStateNameFor(Overdueable overdueable) {
        return dao.getOverdueStateFor(overdueable);
    }

    @Override
    public String getOverdueStateNameFor(UUID overdueableId, Type type) {
        return dao.getOverdueStateForIdAndType(overdueableId, type);
    }

    @Override
    public SortedSet<OverdueEvent> getOverdueHistory(Overdueable overdueable) {
        return dao.getOverdueHistoryFor(overdueable); 
    }

    @Override
    public SortedSet<OverdueEvent> getOverdueHistory(UUID overdueableId,
            Type type) {
        return dao.getOverdueHistoryForIdAndType(overdueableId, type);
    }

    @Override
    public <T extends Overdueable> void setOverrideState(T overdueable, OverdueState<T> newOverdueState, Type type) {
       dao.setOverdueState(overdueable, newOverdueState, type, clock);
        
    }

}
