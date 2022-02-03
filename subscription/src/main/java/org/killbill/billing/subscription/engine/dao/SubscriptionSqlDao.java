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

package org.killbill.billing.subscription.engine.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionModelDao;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.unstable.BindIn;

@KillBillSqlDaoStringTemplate
public interface SubscriptionSqlDao extends EntitySqlDao<SubscriptionModelDao, SubscriptionBase> {

    @SqlQuery
    public SubscriptionModelDao getSubscriptionByExternalKey(@Bind("externalKey") String externalKey,
                                                             @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionModelDao> getSubscriptionsFromBundleId(@Bind("bundleId") String bundleId,
                                                                   @SmartBindBean final InternalTenantContext context);


    @SqlQuery
    public List<SubscriptionModelDao> getActiveByAccountRecordId(@Bind("cutoffDt") Date cutoffDt,
                                                                 @SmartBindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateChargedThroughDate(@Bind("id") String id,
                                         @Bind("chargedThroughDate") Date chargedThroughDate,
                                         @SmartBindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateChargedThroughDates(@BindIn("ids") final Collection<UUID> ids,
                                         @Bind("chargedThroughDate") Date chargedThroughDate,
                                         @SmartBindBean final InternalCallContext context);

}
