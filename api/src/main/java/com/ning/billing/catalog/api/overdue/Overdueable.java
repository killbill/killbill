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

package com.ning.billing.catalog.api.overdue;

import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

public interface Overdueable {

    public enum Type {
        //Not currently supported
        // ACCOUNT,
        SUBSCRIPTION_BUNDLE;
        
        public static Type get(Overdueable o) throws CatalogApiException{
            if (o instanceof SubscriptionBundle){
                return SUBSCRIPTION_BUNDLE;
            }
            throw new CatalogApiException(ErrorCode.CAT_NO_OVERDUEABLE_TYPE , o.getClass().getName());
        }
        
        public static Type get(String type) throws CatalogApiException {
            if (type.equalsIgnoreCase(SUBSCRIPTION_BUNDLE.name())) {
                return SUBSCRIPTION_BUNDLE;
            }
            throw new CatalogApiException(ErrorCode.CAT_NO_OVERDUEABLE_TYPE , type);
        }

    }

    public UUID getId();
}
