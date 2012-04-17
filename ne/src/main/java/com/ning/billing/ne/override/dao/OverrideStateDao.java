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

package com.ning.billing.ne.override.dao;

import java.util.SortedSet;
import java.util.UUID;

import com.ning.billing.overdue.config.api.OverdueState;
import com.ning.billing.overdue.config.api.Overdueable;
import com.ning.billing.overdue.config.api.Overdueable.Type;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.overdue.OverdueEvent;

public interface OverrideStateDao {

    //Read
    public String getOverdueStateFor(Overdueable overdueable);

    public String getOverdueStateForIdAndType(UUID overdueableId, Type type);

    public SortedSet<OverdueEvent> getOverdueHistoryFor(Overdueable overdueable);

    public SortedSet<OverdueEvent> getOverdueHistoryForIdAndType(UUID overdueableId, Type type);

    //Write
    <T extends Overdueable> void  setOverdueState(T overdueable, OverdueState<T> newOverdueState, Overdueable.Type type, Clock clock);

} 