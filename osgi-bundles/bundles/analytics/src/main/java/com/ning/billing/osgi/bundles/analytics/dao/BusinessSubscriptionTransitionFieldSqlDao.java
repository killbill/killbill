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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.List;

import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;

import com.ning.billing.osgi.bundles.analytics.model.BusinessSubscriptionTransitionFieldModelDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.InternalTenantContextBinder;

@ExternalizedSqlViaStringTemplate3()
@RegisterMapper(BusinessSubscriptionTransitionFieldMapper.class)
public interface BusinessSubscriptionTransitionFieldSqlDao {

    @SqlQuery
    List<BusinessSubscriptionTransitionFieldModelDao> getFieldsForBusinessSubscriptionTransitionByKey(@Bind("external_key") final String externalKey,
                                                                                                      @InternalTenantContextBinder final InternalTenantContext context);

    @SqlUpdate
    int addField(@Bind("account_key") final String accountKey,
                 @Bind("bundle_id") final String bundleId,
                 @Bind("external_key") final String externalKey,
                 @Bind("name") final String name,
                 @Bind("value") final String value,
                 @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    int removeField(@Bind("bundle_id") final String bundleId,
                    @Bind("name") final String name,
                    @InternalTenantContextBinder final InternalCallContext context);

    @SqlUpdate
    void test(@InternalTenantContextBinder final InternalTenantContext context);
}
