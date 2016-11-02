/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.InvoiceParentChild;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlQuery;

@EntitySqlDaoStringTemplate
public interface InvoiceParentChildrenSqlDao extends EntitySqlDao<InvoiceParentChildModelDao, InvoiceParentChild> {

    @SqlQuery
    List<InvoiceParentChildModelDao> getChildInvoicesByParentInvoiceId(@Bind("parentInvoiceId") final String parentInvoiceId,
                                                                       @BindBean final InternalTenantContext context);

}

