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

package com.ning.billing.entitlement.events.user;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;


public enum ApiEventType {
    MIGRATE_ENTITLEMENT {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.MIGRATE_ENTITLEMENT;
        }
    },
    CREATE {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.CREATE;
        }
    },
    MIGRATE_BILLING {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.MIGRATE_BILLING;
        }
    },
    TRANSFER {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.TRANSFER;
        }
    },
    CHANGE {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.CHANGE;
        }
    },
    RE_CREATE {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.RE_CREATE;
        }
    },
    CANCEL {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.CANCEL;
        }
    },
    UNCANCEL {
        @Override
        public SubscriptionTransitionType getSubscriptionTransitionType() {
            return SubscriptionTransitionType.UNCANCEL;
        }
    };

    // Used to map from internal events to User visible events (both user and phase)
    public abstract SubscriptionTransitionType getSubscriptionTransitionType();
}
