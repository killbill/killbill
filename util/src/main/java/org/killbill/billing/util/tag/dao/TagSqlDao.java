/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.util.tag.dao;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.tag.Tag;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@KillBillSqlDaoStringTemplate
public interface TagSqlDao extends EntitySqlDao<TagModelDao, Tag> {

    @SqlUpdate
    @Audited(ChangeType.DELETE)
    void markTagAsDeleted(@Bind("id") String tagId,
                          @SmartBindBean InternalCallContext context);

    @SqlQuery
    List<TagModelDao> getTagsForObject(@Bind("objectId") UUID objectId,
                                       @Bind("objectType") ObjectType objectType,
                                       @SmartBindBean InternalTenantContext internalTenantContext);

    @SqlQuery
    List<TagModelDao> getTagsForObjectIncludedDeleted(@Bind("objectId") UUID objectId,
                                                      @Bind("objectType") ObjectType objectType,
                                                      @SmartBindBean InternalTenantContext internalTenantContext);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<TagModelDao> search(@Bind("searchKey") final String searchKey,
                                        @Bind("likeSearchKey") final String likeSearchKey,
                                        @Bind("offset") final Long offset,
                                        @Bind("rowCount") final Long rowCount,
                                        @Define("ordering") final String ordering,
                                        @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchCount(@Bind("searchKey") final String searchKey,
                               @Bind("likeSearchKey") final String likeSearchKey,
                               @SmartBindBean final InternalTenantContext context);
}
