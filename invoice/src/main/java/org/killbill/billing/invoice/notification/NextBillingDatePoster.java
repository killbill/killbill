/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.notification;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

public interface NextBillingDatePoster {

    void insertNextBillingNotificationFromTransaction(EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, UUID accountId,
                                                      Iterable<UUID> subscriptionId, DateTime futureNotificationTime, InternalCallContext internalCallContext);

    void insertNextBillingDryRunNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final UUID accountId,
                                                            final Iterable<UUID> subscriptionId, final DateTime futureNotificationTime, final DateTime targetDate, final InternalCallContext internalCallContext);
}
