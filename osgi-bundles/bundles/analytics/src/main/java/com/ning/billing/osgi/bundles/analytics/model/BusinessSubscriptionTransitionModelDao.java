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

package com.ning.billing.osgi.bundles.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.events.SubscriptionInternalEvent;

/**
 * Describe a state change between two BusinessSubscription
 */
public class BusinessSubscriptionTransitionModelDao extends BusinessModelDaoBase {

    private final Long subscriptionEventRecordId;
    private final Long totalOrdering;
    private final UUID bundleId;
    private final String bundleExternalKey;
    private final UUID subscriptionId;
    private final DateTime requestedTimestamp;
    private final BusinessSubscriptionEvent event;
    private final BusinessSubscription previousSubscription;
    private final BusinessSubscription nextSubscription;

    public BusinessSubscriptionTransitionModelDao(final Long subscriptionEventRecordId,
                                                  final Long totalOrdering,
                                                  final UUID bundleId,
                                                  final String bundleExternalKey,
                                                  final UUID subscriptionId,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
                                                  final DateTime createdDate,
                                                  final String createdBy,
                                                  final String createdReasonCode,
                                                  final String createdComments,
                                                  final UUID accountId,
                                                  final String accountName,
                                                  final String accountExternalKey,
                                                  final Long accountRecordId,
                                                  final Long tenantRecordId) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
        this.subscriptionEventRecordId = subscriptionEventRecordId;
        this.totalOrdering = totalOrdering;
        this.bundleId = bundleId;
        this.bundleExternalKey = bundleExternalKey;
        this.subscriptionId = subscriptionId;
        this.requestedTimestamp = requestedTimestamp;
        this.event = event;
        this.previousSubscription = previousSubscription;
        this.nextSubscription = nextSubscription;
    }

    public BusinessSubscriptionTransitionModelDao(final Account account,
                                                  final SubscriptionBundle bundle,
                                                  final SubscriptionInternalEvent subscriptionEvent,
                                                  final DateTime requestedTimestamp,
                                                  final BusinessSubscriptionEvent event,
                                                  final BusinessSubscription previousSubscription,
                                                  final BusinessSubscription nextSubscription,
                                                  final String createdBy,
                                                  final String createdReasonCode,
                                                  final String createdComments) {
        this(null /* TODO */,
             subscriptionEvent.getTotalOrdering(),
             bundle.getId(),
             bundle.getExternalKey(),
             subscriptionEvent.getSubscriptionId(),
             requestedTimestamp,
             event,
             previousSubscription,
             nextSubscription,
             null /* TODO */,
             createdBy,
             createdReasonCode,
             createdComments,
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             // TODO
             null,
             null);
    }
}
