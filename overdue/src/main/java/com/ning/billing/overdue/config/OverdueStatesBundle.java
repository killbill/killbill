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

package com.ning.billing.overdue.config;

import javax.xml.bind.annotation.XmlElement;

import com.ning.billing.subscription.api.user.SubscriptionBundle;

public class OverdueStatesBundle extends DefaultOverdueStateSet<SubscriptionBundle> {

    @SuppressWarnings("unchecked")
    @XmlElement(required = true, name = "state")
    private DefaultOverdueState<SubscriptionBundle>[] bundleOverdueStates = new DefaultOverdueState[0];

    @Override
    protected DefaultOverdueState<SubscriptionBundle>[] getStates() {
        return bundleOverdueStates;
    }

    protected OverdueStatesBundle setBundleOverdueStates(final DefaultOverdueState<SubscriptionBundle>[] bundleOverdueStates) {
        this.bundleOverdueStates = bundleOverdueStates;
        return this;
    }
}
