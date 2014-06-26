/*
 * Copyright 2014 Groupon, Inc
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.payment.dao;

import java.util.List;

import org.killbill.billing.util.dao.LowerToCamelBeanMapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoStringTemplate;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.CloseMe;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;

@EntitySqlDaoStringTemplate
public interface PluginPropertySqlDao extends Transactional<PluginPropertySqlDao>, CloseMe {

    // STEPH does not follow similar pattern (! extends Entity,... this is not cachable/auditable on purpose)
    // Mapper needs to be defined and PaymentDao is a bit funky, to be reviewed
    public class PluginPropertySqlDaoMapper extends LowerToCamelBeanMapper<PluginPropertyModelDao> {
        public PluginPropertySqlDaoMapper() {
            super(PluginPropertyModelDao.class);
        }
    }

    @RegisterMapper(PluginPropertySqlDaoMapper.class)
    @SqlQuery
    List<PluginPropertyModelDao> getPluginProperties(@Bind("attemptId") final String attemptId);

    @SqlBatch(transactional = false)
    void batchCreateFromTransaction(@BindBean List<PluginPropertyModelDao> dataEntries);

    @SqlUpdate
    void create(@BindBean PluginPropertyModelDao entry);
}
