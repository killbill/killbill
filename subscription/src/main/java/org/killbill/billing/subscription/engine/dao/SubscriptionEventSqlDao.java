/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription.engine.dao;

import java.util.Date;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionEventModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@KillBillSqlDaoStringTemplate
public interface SubscriptionEventSqlDao extends EntitySqlDao<SubscriptionEventModelDao, SubscriptionBaseEvent> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void unactiveEvent(@Bind("id") String id,
                              @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public List<SubscriptionEventModelDao> getFutureActiveEventForSubscription(@Bind("subscriptionId") String subscriptionId,
                                                                               @Bind("now") Date now,
                                                                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionEventModelDao> getFutureOrPresentActiveEventForSubscription(@Bind("subscriptionId") String subscriptionId,
                                                                                        @Bind("now") Date now,
                                                                                        @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionEventModelDao> getActiveEventsForSubscription(@Bind("subscriptionId") String subscriptionId,
                                                                          @SmartBindBean final InternalTenantContext context);
}
