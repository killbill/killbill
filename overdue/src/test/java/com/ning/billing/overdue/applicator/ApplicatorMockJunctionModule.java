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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.Blockable.Type;
import com.ning.billing.mock.glue.MockJunctionModule;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.junction.BlockingApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

public class ApplicatorMockJunctionModule extends MockJunctionModule {

    public static class ApplicatorBlockingApi implements BlockingApi {

        private BlockingState blockingState;

        public BlockingState getBlockingState() {
            return blockingState;
        }

        @Override
        public BlockingState getBlockingStateFor(final Blockable overdueable, InternalTenantContext context) {
            return new BlockingState() {

                @Override
                public String getStateName() {
                    return DefaultBlockingState.CLEAR_STATE_NAME;
                }

                @Override
                public Type getType() {

                    return null;
                }

                @Override
                public DateTime getTimestamp() {
                    return null;
                }

                @Override
                public boolean isBlockChange() {
                    return false;
                }

                @Override
                public boolean isBlockEntitlement() {
                    return false;
                }

                @Override
                public boolean isBlockBilling() {
                    return false;
                }

                @Override
                public int compareTo(final BlockingState arg0) {
                    return 0;
                }

                @Override
                public String getDescription() {
                    return null;
                }

            };
        }

        @Override
        public BlockingState getBlockingStateFor(final UUID overdueableId, final InternalTenantContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BlockingState> getBlockingHistory(final Blockable overdueable, final InternalTenantContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BlockingState> getBlockingHistory(final UUID overdueableId, final InternalTenantContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends Blockable> void setBlockingState(final BlockingState state, final InternalCallContext context) {
            blockingState = state;
        }

    }

    @Override
    public void installBlockingApi() {
        bind(BlockingApi.class).toInstance(new ApplicatorBlockingApi());
    }
}
