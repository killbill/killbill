/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.entity.dao;

import org.skife.jdbi.v2.IDBI;

public class DBRouter<C> extends DBRouterUntyped {

    private final C onDemand;
    private final C roOnDemand;

    public DBRouter(final IDBI dbi, final IDBI roDbi, final Class<C> sqlObjectType) {
        super(dbi, roDbi);
        this.onDemand = dbi.onDemand(sqlObjectType);
        this.roOnDemand = roDbi.onDemand(sqlObjectType);
    }

    public C onDemand(final boolean requestedRO) {
        if (shouldUseRODBI(requestedRO)) {
            return roOnDemand;
        } else {
            return onDemand;
        }
    }
}
