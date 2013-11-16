/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.overdue.glue;

import java.util.List;
import java.util.UUID;

import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.entitlement.api.BlockingState;
import com.ning.billing.entitlement.api.BlockingStateType;
import com.ning.billing.junction.BlockingInternalApi;
import com.ning.billing.junction.DefaultBlockingState;

import com.google.inject.AbstractModule;

public class ApplicatorMockJunctionModule extends AbstractModule {

    @Override
    protected void configure() {
        installBlockingApi();
    }

    public static class ApplicatorBlockingApi implements BlockingInternalApi {

        private BlockingState blockingState;

        public BlockingState getBlockingState() {
            return blockingState;
        }

        @Override
        public BlockingState getBlockingStateForService(final UUID blockableId, final BlockingStateType blockingStateType, final String serviceName, final InternalTenantContext context) {
            if (blockingState != null && blockingState.getBlockedId().equals(blockableId)) {
                return blockingState;
            } else {
                return DefaultBlockingState.getClearState(blockingStateType, serviceName, new ClockMock());
            }
        }

        @Override
        public List<BlockingState> getBlockingAll(final UUID blockableId, final BlockingStateType blockingStateType, final InternalTenantContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBlockingState(final BlockingState state, final InternalCallContext context) {
            blockingState = state;
        }
    }

    public void installBlockingApi() {
        bind(BlockingInternalApi.class).toInstance(new ApplicatorBlockingApi());
    }
}
