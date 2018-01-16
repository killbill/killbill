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

package org.killbill.billing.util.security.shiro.dao;

import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.entity.dao.Audited;
import org.killbill.commons.jdbi.template.KillBillSqlDaoStringTemplate;
import org.killbill.commons.jdbi.binder.SmartBindBean;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@KillBillSqlDaoStringTemplate
public interface RolesPermissionsSqlDao extends Transactional<RolesPermissionsSqlDao> {

    @SqlQuery
    public RolesPermissionsModelDao getByRecordId(@Bind("recordId") final Long recordId);

    @SqlQuery
    public List<RolesPermissionsModelDao> getByRoleName(@Bind("roleName") final String roleName);

    @SqlUpdate
    public void create(@SmartBindBean final RolesPermissionsModelDao rolesPermissions);

    @SqlUpdate
    @Audited(ChangeType.UPDATE)
    public void unactiveEvent(@Bind("recordId") final Long recordId,
                              @Bind("createdDate")  final DateTime createdDate,
                              @Bind("createdBy")  final String createdBy);


}
