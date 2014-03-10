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

package org.killbill.billing.subscription.api.transfer;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.callcontext.CallContext;

/**
 * The interface {@code SubscriptionBaseTransferApi} is used to transfer a bundle from one account to another account.
 */
public interface SubscriptionBaseTransferApi {

    /**
     * @param sourceAccountId   the unique id for the account on which the bundle will be transferred from
     * @param destAccountId     the unique id for the account on which the bundle will be transferred to
     * @param bundleKey         the externalKey for the bundle
     * @param requestedDate     the date at which this transfer should occur
     * @param transferAddOn     whether or not we should also transfer ADD_ON subscriptions existing on that {@code SubscriptionBaseBundle}
     * @param cancelImmediately whether cancellation on the sourceAccount occurs immediately
     * @param context           the user callcontext
     * @return the newly created {@code SubscriptionBaseBundle}
     * @throws SubscriptionBaseTransferApiException
     *          if the system could not transfer the {@code SubscriptionBaseBundle}
     */
    public SubscriptionBaseBundle transferBundle(final UUID sourceAccountId, final UUID destAccountId, final String bundleKey, final DateTime requestedDate,
                                             final boolean transferAddOn, final boolean cancelImmediately, final CallContext context)
            throws SubscriptionBaseTransferApiException;
}
