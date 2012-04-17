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

import com.ning.billing.overdue.config.api.OverdueState;
import com.ning.billing.overdue.config.api.Overdueable;
import com.ning.billing.util.clock.Clock;

public interface OverdueAccessApi {
    public static final String CLEAR_STATE_NAME = "__KILLBILL__CLEAR__OVERDUE_STATE__";

    public String getOverdueStateNameFor(Overdueable overdueable);

    public String getOverdueStateNameFor(UUID overdueableId, Overdueable.Type type);
    
    public SortedSet<OverdueEvent> getOverdueHistory(Overdueable overdueable);

    public SortedSet<OverdueEvent> getOverdueHistory(UUID overdueableId, Overdueable.Type type);
    
    public <T extends Overdueable> void  setOverrideState(T overdueable, OverdueState<T> newOverdueState, Overdueable.Type type, Clock clock);

}
