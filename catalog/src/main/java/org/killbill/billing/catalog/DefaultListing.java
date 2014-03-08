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

package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.Listing;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceList;

public class DefaultListing implements Listing {
    private final Plan plan;
    private final PriceList priceList;

    public DefaultListing(final Plan plan, final PriceList priceList) {
        super();
        this.plan = plan;
        this.priceList = priceList;
    }

    @Override
    public Plan getPlan() {
        return plan;
    }

    @Override
    public PriceList getPriceList() {
        return priceList;
    }

}
