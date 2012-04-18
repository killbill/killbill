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

package com.ning.billing.junction.api.blocking;

import java.util.SortedSet;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.util.clock.Clock;

public class DefaultBlockingApi implements BlockingApi {
    private BlockingStateDao dao;
    private Clock clock;

    @Inject
    public DefaultBlockingApi(BlockingStateDao dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }
    
    @Override
    public String getBlockingStateNameFor(Blockable overdueable) {
        return dao.getBlockingStateFor(overdueable);
    }

    @Override
    public String getBlockingStateNameFor(UUID overdueableId, Type type) {
        return dao.getBlockingStateForIdAndType(overdueableId, type);
    }

    @Override
    public SortedSet<BlockingState> getBlockingHistory(Blockable overdueable) {
        return dao.getBlockingHistoryFor(overdueable); 
    }

    @Override
    public SortedSet<BlockingState> getBlockingHistory(UUID overdueableId,
            Type type) {
        return dao.getBlockingHistoryForIdAndType(overdueableId, type);
    }

    @Override
    public <T extends Blockable> void setBlockingState(BlockingState state) {
       dao.setBlockingState(state, clock);
        
    }

}
