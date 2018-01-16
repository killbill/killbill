/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.junction.plumbing.billing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.killbill.billing.entitlement.api.BlockingState;

public class BlockingStateService {

    final Map<UUID, BlockingState> perObjectTypeFirstBlockingState = new HashMap<UUID, BlockingState>();

    private final List<DisabledDuration> result;

    public BlockingStateService() {
        this.result = new ArrayList<DisabledDuration>();
    }

    public List<DisabledDuration> build() {
        for (BlockingState cur : perObjectTypeFirstBlockingState.values()) {
            if (cur != null) {
                addDisabledDuration(cur, null);
            }
        }
        return result;
    }

    public void addBlockingState(final BlockingState currentBlockingState) {

        final BlockingState firstBlockingState = perObjectTypeFirstBlockingState.get(currentBlockingState.getBlockedId());
        if (currentBlockingState.isBlockBilling() && firstBlockingState == null) {
            perObjectTypeFirstBlockingState.put(currentBlockingState.getBlockedId(), currentBlockingState);
        } else if (!currentBlockingState.isBlockBilling() && firstBlockingState != null) {
            addDisabledDuration(firstBlockingState, currentBlockingState.getEffectiveDate());
            perObjectTypeFirstBlockingState.put(currentBlockingState.getBlockedId(), null);
        }
    }

    private void addDisabledDuration(final BlockingState firstBlockingState, @Nullable final DateTime disableDurationEndDate) {

        if (disableDurationEndDate == null || Days.daysBetween(firstBlockingState.getEffectiveDate(), disableDurationEndDate).getDays() >= 1) {
            // Don't disable for periods less than a day (see https://github.com/killbill/killbill/issues/267)
            result.add(new DisabledDuration(firstBlockingState.getEffectiveDate(), disableDurationEndDate));
        }
    }
}
