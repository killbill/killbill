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

package org.killbill.billing.events;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.api.BlockingStateType;

// Event for effective blocking state changes (not entitlement specific)
public interface BlockingTransitionInternalEvent extends BusInternalEvent {

    public UUID getBlockableId();

    public BlockingStateType getBlockingType();

    public String getStateName();

    public String getService();

    public DateTime getEffectiveDate();

    public Boolean isTransitionedToBlockedBilling();

    public Boolean isTransitionedToUnblockedBilling();

    public Boolean isTransitionedToBlockedEntitlement();

    public Boolean isTransitionedToUnblockedEntitlement();
}
