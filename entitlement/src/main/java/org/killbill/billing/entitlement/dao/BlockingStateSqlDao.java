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

package org.killbill.billing.entitlement.dao;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

@KillBillSqlDaoStringTemplate
public interface BlockingStateSqlDao extends EntitySqlDao<BlockingStateModelDao, BlockingState> {

    @SqlQuery
    public abstract BlockingStateModelDao getBlockingStateForService(@Bind("blockableId") UUID blockableId,
                                                                     @Bind("service") String serviceName,
                                                                     @Bind("effectiveDate") Date effectiveDate,
                                                                     @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public abstract List<BlockingStateModelDao> getBlockingState(@Bind("blockableId") UUID blockableId,
                                                                 @Bind("type") BlockingStateType blockingStateType,
                                                                 @Bind("effectiveDate") Date effectiveDate,
                                                                 @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public abstract List<BlockingStateModelDao> getBlockingAllUpToForAccount(@Bind("effectiveDate") Date effectiveDate,
                                                                             @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public abstract List<BlockingStateModelDao> getBlockingHistoryForService(@Bind("blockableId") UUID blockableId,
                                                                             @Bind("service") String serviceName,
                                                                             @SmartBindBean final InternalTenantContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void unactiveEvent(@Bind("id") String id,
                              @SmartBindBean final InternalCallContext context);
}
