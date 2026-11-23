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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockingStateService {

    private static final Logger log = LoggerFactory.getLogger(BlockingStateService.class);

    final Map<UUID, BlockingState> perObjectTypeFirstBlockingState = new HashMap<UUID, BlockingState>();

    // Unblocks seen while no block is open for the same object, keyed by blocked id.
    // States arrive sorted by effective date, so an unblock can only precede its block
    // when both share the same effective date (ordered by creation date); remembering
    // it makes the outcome independent of insertion order. See
    // https://github.com/killbill/killbill/issues/2328
    final Map<UUID, BlockingState> perObjectTypePendingUnblock = new HashMap<UUID, BlockingState>();

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
            final BlockingState pendingUnblock = perObjectTypePendingUnblock.remove(currentBlockingState.getBlockedId());
            if (pendingUnblock != null && !pendingUnblock.getEffectiveDate().isBefore(currentBlockingState.getEffectiveDate())) {
                // The unblock was seen first: pair it with this block instead of leaving
                // the block open forever. For the same effective date the pair cancels
                // (addDisabledDuration drops sub-day durations), matching the outcome
                // when the block is seen first.
                addDisabledDuration(currentBlockingState, pendingUnblock.getEffectiveDate());
            } else {
                if (pendingUnblock != null) {
                    log.debug("Ignoring stale unblock for blockedId {}: unblock at {} predates block at {}",
                              currentBlockingState.getBlockedId(), pendingUnblock.getEffectiveDate(), currentBlockingState.getEffectiveDate());
                }
                perObjectTypeFirstBlockingState.put(currentBlockingState.getBlockedId(), currentBlockingState);
            }
        } else if (!currentBlockingState.isBlockBilling() && firstBlockingState != null) {
            addDisabledDuration(firstBlockingState, currentBlockingState.getEffectiveDate());
            perObjectTypeFirstBlockingState.put(currentBlockingState.getBlockedId(), null);
        } else if (!currentBlockingState.isBlockBilling()) {
            // Unblock with no open block: remember it so a block with the same effective
            // date arriving later pairs with it instead of being left open with no end.
            // A second unblock for the same object keeps the earliest one.
            if (perObjectTypePendingUnblock.putIfAbsent(currentBlockingState.getBlockedId(), currentBlockingState) == null) {
                log.debug("Remembering unmatched unblock for blockedId {} effective {}",
                          currentBlockingState.getBlockedId(), currentBlockingState.getEffectiveDate());
            }
        }
    }

    private void addDisabledDuration(final BlockingState firstBlockingState, @Nullable final DateTime disableDurationEndDate) {

        if (disableDurationEndDate == null || Days.daysBetween(firstBlockingState.getEffectiveDate(), disableDurationEndDate).getDays() >= 1) {
            // Don't disable for periods less than a day (see https://github.com/killbill/killbill/issues/267)
            result.add(new DisabledDuration(firstBlockingState.getEffectiveDate(), disableDurationEndDate));
        }
    }
}
