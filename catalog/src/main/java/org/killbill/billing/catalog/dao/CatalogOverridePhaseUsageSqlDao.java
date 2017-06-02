/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog.dao;

import java.util.Collection;
import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@KillBillSqlDaoStringTemplate
public interface CatalogOverridePhaseUsageSqlDao extends Transactional<CatalogOverridePhaseUsageSqlDao>, CloseMe {

    @SqlUpdate
    public void create(@SmartBindBean final CatalogOverridePhaseUsageModelDao entity,
                       @SmartBindBean final InternalCallContext context);

    @SqlQuery
    public CatalogOverridePhaseUsageModelDao getByRecordId(@Bind("recordId") final Long recordId,
                                                          @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public List<Long> getTargetPhaseDefinition(@PhaseUsageKeysCollectionBinder final Collection<String> concatUsageNumAndUsageDefRecordId,
                                               @Bind("targetCount") final Integer targetCount,
                                               @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getLastInsertId();
}
