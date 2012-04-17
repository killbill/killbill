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

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.overdue.config.api.Overdueable;
import com.ning.billing.overdue.config.api.Overdueable.Type;

public class OverdueEvent implements Comparable<OverdueEvent>{
    private final UUID overdueableId;
    private final String stateName;
    private final Overdueable.Type type;
    private final DateTime timestamp;
    
    public OverdueEvent(UUID overdueableId, String stateName, Type type,
            DateTime timestamp) {
        super();
        this.overdueableId = overdueableId;
        this.stateName = stateName;
        this.type = type;
        this.timestamp = timestamp;
    }
    
    public UUID getOverdueableId() {
        return overdueableId;
    }
    public String getStateName() {
        return stateName;
    }
    public Overdueable.Type getType() {
        return type;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public int compareTo(OverdueEvent arg0) {
        return timestamp.compareTo(arg0.getTimestamp());
    }
}
