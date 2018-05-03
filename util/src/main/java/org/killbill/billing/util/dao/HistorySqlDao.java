/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.dao;

import java.util.List;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

public interface HistorySqlDao<M extends EntityModelDao<E>, E extends Entity> {

    //  bypassMappingRegistryCache is to be used in the mapping registry to bypass cache. This is useful for generic classes since it will prevent to return previously cached class, that could result in wrong maps.
    //  https://github.com/killbill/killbill-commons/blob/work-for-release-0.19.x/jdbi/src/main/java/org/skife/jdbi/v2/MappingRegistry.java#L67
    @SqlQuery
    public List<EntityHistoryModelDao<M, E>> getHistoryForTargetRecordId(@Define("bypassMappingRegistryCache") final boolean bypassMappingRegistryCache,
                                                                         @Bind("targetRecordId") final long targetRecordId,
                                                                         @SmartBindBean InternalTenantContext context);
    @SqlUpdate
    @GetGeneratedKeys
    public Long addHistoryFromTransaction(@EntityHistoryBinder EntityHistoryModelDao<M, E> history,
                                          @SmartBindBean InternalCallContext context);
}
