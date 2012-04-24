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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public interface Blockable {

    public enum Type {
        ACCOUNT,
        SUBSCRIPTION_BUNDLE,
        SUBSCRIPTION;
        
        public static Type get(Blockable o) throws BlockingApiException{
            if (o instanceof Account){
                return ACCOUNT;
            } else if (o instanceof SubscriptionBundle){
                return SUBSCRIPTION_BUNDLE;
            } else if (o instanceof Subscription){
                return SUBSCRIPTION;
            }
            throw new BlockingApiException(ErrorCode.BLOCK_TYPE_NOT_SUPPORTED , o.getClass().getName());
        }
        
        public static Type get(String type) throws BlockingApiException {
            if (type.equalsIgnoreCase(ACCOUNT.name())) {
                return ACCOUNT;
            } else if (type.equalsIgnoreCase(SUBSCRIPTION_BUNDLE.name())) {
                return SUBSCRIPTION_BUNDLE;
            } else if (type.equalsIgnoreCase(SUBSCRIPTION.name())) {
                return SUBSCRIPTION;
            }
            throw new BlockingApiException(ErrorCode.BLOCK_TYPE_NOT_SUPPORTED , type);
        }

    }

    public UUID getId();
}
