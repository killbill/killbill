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

package com.ning.billing.junction.api;

import java.util.UUID;

import org.joda.time.DateTime;

public class BlockingState implements Comparable<BlockingState>{
    private final UUID blockingId;
    private final Blockable.Type type;
    private final String stateName;
    private final String service;
    private final boolean blockChange;
    private final boolean blockEntitlement;
    private final boolean blockBilling;
    private final DateTime timestamp;
    
    public BlockingState(UUID blockingId, 
            String stateName, 
            Blockable.Type type, 
            String service,
            boolean blockChange,
            boolean blockEntitlement,
            boolean blockBilling
            ) {
        this(   blockingId, 
                 stateName, 
                 type, 
                 service,
                 blockChange,
                 blockEntitlement,
                 blockBilling,
                 null);
    }    
    
    public BlockingState(UUID blockingId, 
            String stateName, 
            Blockable.Type type, 
            String service,
            boolean blockChange,
            boolean blockEntitlement,
            boolean blockBilling,
            DateTime timestamp
            ) {
        super();
        this.blockingId = blockingId;
        this.stateName = stateName;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
        this.type = type;
        this.timestamp = timestamp;
    }
    
    public UUID getBlockedId() {
        return blockingId;
    }
    public String getStateName() {
        return stateName;
    }
    public Blockable.Type getType() {
        return type;
    }
    public DateTime getTimestamp() {
        return timestamp;
    }

    public String getService() {
        return service;
    }

    public boolean isBlockChange() {
        return blockChange;
    }

    public boolean isBlockEntitlement() {
        return blockEntitlement;
    }

    public boolean isBlockBilling() {
        return blockBilling;
    }

    @Override
    public int compareTo(BlockingState arg0) {
        return timestamp.compareTo(arg0.getTimestamp());
    }
}
