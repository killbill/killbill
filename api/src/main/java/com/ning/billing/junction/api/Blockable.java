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

package com.ning.billing.junction.api;

import java.util.UUID;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.entity.Entity;

public interface Blockable extends Entity {

    public enum Type {
        ACCOUNT,
        SUBSCRIPTION_BUNDLE,
        SUBSCRIPTION;

        public static Type get(final Blockable o) {
            if (o instanceof Account) {
                return ACCOUNT;
            } else if (o instanceof SubscriptionBundle) {
                return SUBSCRIPTION_BUNDLE;
            } else if (o instanceof Subscription) {
                return SUBSCRIPTION;
            }
            throw new IllegalStateException("Unsupported type of blockable " + o);
        }

        public static Type get(final String type) throws BlockingApiException {
            if (type.equalsIgnoreCase(ACCOUNT.name())) {
                return ACCOUNT;
            } else if (type.equalsIgnoreCase(SUBSCRIPTION_BUNDLE.name())) {
                return SUBSCRIPTION_BUNDLE;
            } else if (type.equalsIgnoreCase(SUBSCRIPTION.name())) {
                return SUBSCRIPTION;
            }
            throw new IllegalStateException("Unsupported type of blockable " + type);
        }

        public static ObjectType getObjectType(final Blockable o) {
            final Type type = get(o);
            return getObjectType(type);
        }

        public static ObjectType getObjectType(final Type type) {
            switch (type) {
                case ACCOUNT:
                    return ObjectType.ACCOUNT;
                case SUBSCRIPTION_BUNDLE:
                    return ObjectType.BUNDLE;
                case SUBSCRIPTION:
                    return ObjectType.SUBSCRIPTION;
                default:
                    throw new IllegalStateException("Unsupported type of blockable " + type);
            }
        }
    }

    @Override
    public UUID getId();

    public BlockingState getBlockingState();
}
