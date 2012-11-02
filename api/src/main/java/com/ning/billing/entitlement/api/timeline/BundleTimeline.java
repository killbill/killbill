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

package com.ning.billing.entitlement.api.timeline;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.entity.Entity;

/**
 *  The interface {@code BundleTimeline} shows a view of all the entitlement events for a specific
 *  {@code SubscriptionBundle}.
 *
 */
public interface BundleTimeline extends Entity {

    /**
     *
     * @return a unique viewId to identify whether two calls who display the same view or a different view
     */
    String getViewId();

    /**
     *
     * @return the unique id for the {@SubscriptionBundle}
     */
    UUID getId();

    /**
     *
     * @return the external Key for the {@SubscriptionBundle}
     */
    String getExternalKey();

    /**
     *
     * @return the list of {@code SubscriptionTimeline}
     */
    List<SubscriptionTimeline> getSubscriptions();
}
