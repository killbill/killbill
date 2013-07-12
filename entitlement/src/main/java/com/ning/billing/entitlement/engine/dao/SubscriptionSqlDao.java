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

package com.ning.billing.entitlement.engine.dao;

import java.util.Date;
import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

import com.ning.billing.entitlement.engine.dao.model.SubscriptionModelDao;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.Audited;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoStringTemplate;

@EntitySqlDaoStringTemplate
public interface SubscriptionSqlDao extends EntitySqlDao<SubscriptionModelDao, Subscription> {

    @SqlQuery
    public List<SubscriptionModelDao> getSubscriptionsFromBundleId(@Bind("bundleId") String bundleId,
                                                                   @BindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateChargedThroughDate(@Bind("id") String id, @Bind("chargedThroughDate") Date chargedThroughDate,
                                         @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateActiveVersion(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                             @BindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateForRepair(@Bind("id") String id, @Bind("activeVersion") long activeVersion,
                                @Bind("startDate") Date startDate,
                                @Bind("bundleStartDate") Date bundleStartDate,
                                @BindBean final InternalCallContext context);
}
