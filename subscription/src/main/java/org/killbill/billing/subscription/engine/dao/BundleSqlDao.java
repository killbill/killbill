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

import java.util.Date;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@KillBillSqlDaoStringTemplate
public interface BundleSqlDao extends EntitySqlDao<SubscriptionBundleModelDao, SubscriptionBaseBundle> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateBundleExternalKey(@Bind("id") String id,
                                        @Bind("externalKey") String externalKey,
                                        @SmartBindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void renameBundleExternalKey(@Bind("externalKey") String externalKey,
                                        @Define("prefix") final String prefix,
                                        @SmartBindBean final InternalCallContext context);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void updateBundleLastSysTime(@Bind("id") String id,
                                        @Bind("lastSysUpdateDate") Date lastSysUpdate,
                                        @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public SubscriptionBundleModelDao getBundlesFromAccountAndKey(@Bind("accountId") String accountId,
                                                                  @Bind("externalKey") String externalKey,
                                                                  @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionBundleModelDao> getBundleFromAccount(@Bind("accountId") String accountId,
                                                                 @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<SubscriptionBundleModelDao> getBundlesForLikeKey(@Bind("externalKey") String externalKey,
                                                                 @SmartBindBean final InternalTenantContext context);
}
