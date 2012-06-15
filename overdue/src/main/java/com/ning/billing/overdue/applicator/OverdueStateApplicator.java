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

import org.joda.time.DateTime;
import org.joda.time.Period;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.junction.api.DefaultBlockingState;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.OverdueError;
import com.ning.billing.util.clock.Clock;

public class OverdueStateApplicator<T extends Blockable> {

    private final BlockingApi blockingApi;
    private final Clock clock;
    private final OverdueCheckPoster poster;


    @Inject
    public OverdueStateApplicator(final BlockingApi accessApi, final Clock clock, final OverdueCheckPoster poster) {
        this.blockingApi = accessApi;
        this.clock = clock;
        this.poster = poster;
    }

    public void apply(final T overdueable, final String previousOverdueStateName, final OverdueState<T> nextOverdueState) throws OverdueError {
        if (previousOverdueStateName.equals(nextOverdueState.getName())) {
            return; // nothing to do
        }

        storeNewState(overdueable, nextOverdueState);
        try {
            final Period reevaluationInterval = nextOverdueState.getReevaluationInterval();
            if (!nextOverdueState.isClearState()) {
                createFutureNotification(overdueable, clock.getUTCNow().plus(reevaluationInterval));
            }
        } catch (OverdueApiException e) {
            if (e.getCode() != ErrorCode.OVERDUE_NO_REEVALUATION_INTERVAL.getCode()) {
                throw new OverdueError(e);
            }
        }

        if (nextOverdueState.isClearState()) {
            clear(overdueable);
        }
    }


    protected void storeNewState(final T blockable, final OverdueState<T> nextOverdueState) throws OverdueError {
        try {
            blockingApi.setBlockingState(new DefaultBlockingState(blockable.getId(), nextOverdueState.getName(), Blockable.Type.get(blockable),
                                                                  OverdueService.OVERDUE_SERVICE_NAME, blockChanges(nextOverdueState), blockEntitlement(nextOverdueState), blockBilling(nextOverdueState)));
        } catch (Exception e) {
            throw new OverdueError(e, ErrorCode.OVERDUE_CAT_ERROR_ENCOUNTERED, blockable.getId(), blockable.getClass().getName());
        }
    }

    private boolean blockChanges(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.blockChanges();
    }

    private boolean blockBilling(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    private boolean blockEntitlement(final OverdueState<T> nextOverdueState) {
        return nextOverdueState.disableEntitlementAndChangesBlocked();
    }

    protected void createFutureNotification(final T overdueable,
                                            final DateTime timeOfNextCheck) {
        poster.insertOverdueCheckNotification(overdueable, timeOfNextCheck);

    }

    protected void clear(final T blockable) {
        //Need to clear the overrride table here too (when we add it)
        poster.clearNotificationsFor(blockable);
    }

}
