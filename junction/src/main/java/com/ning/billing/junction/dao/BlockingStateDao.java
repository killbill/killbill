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

import java.util.SortedSet;
import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.clock.Clock;

public interface BlockingStateDao {

    //Read
    public BlockingState getBlockingStateFor(Blockable blockable);

    public BlockingState getBlockingStateFor(UUID blockableId);

    public SortedSet<BlockingState> getBlockingHistoryFor(Blockable blockable);

    public SortedSet<BlockingState> getBlockingHistoryFor(UUID blockableId);

    //Write
    <T extends Blockable> void  setBlockingState(BlockingState state, Clock clock);

} 