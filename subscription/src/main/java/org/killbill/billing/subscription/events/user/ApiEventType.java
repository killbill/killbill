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

package org.killbill.billing.subscription.events.user;

import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;


public enum ApiEventType {
    CREATE {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.CREATE;
        }
    },
    TRANSFER {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.TRANSFER;
        }
    },
    CHANGE {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.CHANGE;
        }
    },
    CANCEL {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.CANCEL;
        }
    },
    UNDO_CHANGE {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.UNDO_CHANGE;
        }
    },
    UNCANCEL {
        @Override
        public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
            return SubscriptionBaseTransitionType.UNCANCEL;
        }
    };

    // Used to map from internal events to User visible events (both user and phase)
    public abstract SubscriptionBaseTransitionType getSubscriptionTransitionType();
}
