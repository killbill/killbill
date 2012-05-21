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

package com.ning.billing.overdue.applicator;

import java.util.SortedSet;
import java.util.UUID;

import org.apache.commons.lang.NotImplementedException;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.mock.glue.MockJunctionModule;

public class ApplicatorMockJunctionModule extends MockJunctionModule {
    
    public static class ApplicatorBlockingApi implements BlockingApi {
            private BlockingState blockingState;

            public BlockingState getBlockingState() {
                return blockingState;
            }

            @Override
            public BlockingState getBlockingStateFor(Blockable overdueable) {
                throw new NotImplementedException();
            }

            @Override
            public BlockingState getBlockingStateFor(UUID overdueableId) {
                throw new NotImplementedException();
            }

            @Override
            public SortedSet<BlockingState> getBlockingHistory(Blockable overdueable) {
                throw new NotImplementedException();
            }

            @Override
            public SortedSet<BlockingState> getBlockingHistory(UUID overdueableId) {
                throw new NotImplementedException();
           }

            @Override
            public <T extends Blockable> void setBlockingState(BlockingState state) {
                blockingState = state;
            }
            
        }
    
    @Override
    public void installBlockingApi() {
        bind(BlockingApi.class).toInstance(new ApplicatorBlockingApi() );
        
    }
    
}