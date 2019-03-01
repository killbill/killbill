/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.util.customfield.dao;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.customfield.CustomField;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.killbill.commons.jdbi.statement.SmartFetchSize;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Define;

@KillBillSqlDaoStringTemplate
public interface CustomFieldSqlDao extends EntitySqlDao<CustomFieldModelDao, CustomField> {

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    void updateValue(@Bind("id") String customFieldId,
                     @Bind("fieldValue") String fieldValue,
                     @SmartBindBean InternalCallContext context);


    @SqlUpdate
    @Audited(ChangeType.DELETE)
    void markTagAsDeleted(@Bind("id") String customFieldId,
                          @SmartBindBean InternalCallContext context);

    @SqlQuery
    List<CustomFieldModelDao> getCustomFieldsForObject(@Bind("objectId") UUID objectId,
                                                       @Bind("objectType") ObjectType objectType,
                                                       @SmartBindBean InternalTenantContext internalTenantContext);

    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<CustomFieldModelDao> searchByObjectTypeAndFieldName(@Bind("fieldName") String fieldName,
                                                                        @Bind("objectType") ObjectType objectType,
                                                                        @Bind("offset") final Long offset,
                                                                        @Bind("rowCount") final Long rowCount,
                                                                        @Define("ordering") final String ordering,
                                                                        @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchCountByObjectTypeAndFieldName(@Bind("fieldName") String fieldName,
                                                       @Bind("objectType") ObjectType objectType,
                                                       @SmartBindBean final InternalTenantContext context);


    @SqlQuery
    @SmartFetchSize(shouldStream = true)
    public Iterator<CustomFieldModelDao> searchByObjectTypeAndFieldNameValue(@Bind("fieldName") String fieldName,
                                                                             @Bind("fieldValue") final String fieldValue,
                                                                             @Bind("objectType") ObjectType objectType,
                                                                             @Bind("offset") final Long offset,
                                                                             @Bind("rowCount") final Long rowCount,
                                                                             @Define("ordering") final String ordering,
                                                                             @SmartBindBean final InternalTenantContext context);

    @SqlQuery
    public Long getSearchCountByObjectTypeAndFieldNameValue(@Bind("fieldName") String fieldName,
                                                            @Bind("fieldValue") final String fieldValue,
                                                            @Bind("objectType") ObjectType objectType,
                                                            @SmartBindBean final InternalTenantContext context);


}
