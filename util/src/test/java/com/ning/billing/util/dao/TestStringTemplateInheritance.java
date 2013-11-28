/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.dao;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.antlr.stringtemplate.StringTemplateGroup;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.util.UtilTestSuiteNoDB;

import com.google.common.collect.ImmutableMap;

public class TestStringTemplateInheritance extends UtilTestSuiteNoDB {

    InputStream entityStream;
    InputStream kombuchaStream;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        entityStream = this.getClass().getResourceAsStream("/com/ning/billing/util/entity/dao/EntitySqlDao.sql.stg");
        kombuchaStream = this.getClass().getResourceAsStream("/com/ning/billing/util/dao/Kombucha.sql.stg");
    }

    @Override
    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        super.afterMethod();
        if (entityStream != null) {
            entityStream.close();
        }
        if (kombuchaStream != null) {
            kombuchaStream.close();
        }
    }

    @Test(groups = "fast")
    public void testCheckQueries() throws Exception {
        // From http://www.antlr.org/wiki/display/ST/ST+condensed+--+Templates+and+groups#STcondensed--Templatesandgroups-Withsupergroupfile:
        //     there is no mechanism for automatically loading a mentioned super-group file
        new StringTemplateGroup(new InputStreamReader(entityStream));

        final StringTemplateGroup kombucha = new StringTemplateGroup(new InputStreamReader(kombuchaStream));

        // Verify non inherited template
        Assert.assertEquals(kombucha.getInstanceOf("isIsTimeForKombucha").toString(), "select hour(current_timestamp()) = 17 as is_time;");

        // Verify inherited templates
        Assert.assertEquals(kombucha.getInstanceOf("getById").toString(), "select\n" +
                                                                          "  t.record_id\n" +
                                                                          ", t.id\n" +
                                                                          ", t.tea\n" +
                                                                          ", t.mushroom\n" +
                                                                          ", t.sugar\n" +
                                                                          ", t.account_record_id\n" +
                                                                          ", t.tenant_record_id\n" +
                                                                          "from kombucha t\n" +
                                                                          "where t.id = :id\n" +
                                                                          "and t.tenant_record_id = :tenantRecordId\n" +
                                                                          ";");
        Assert.assertEquals(kombucha.getInstanceOf("getByRecordId").toString(), "select\n" +
                                                                                "  t.record_id\n" +
                                                                                ", t.id\n" +
                                                                                ", t.tea\n" +
                                                                                ", t.mushroom\n" +
                                                                                ", t.sugar\n" +
                                                                                ", t.account_record_id\n" +
                                                                                ", t.tenant_record_id\n" +
                                                                                "from kombucha t\n" +
                                                                                "where t.record_id = :recordId\n" +
                                                                                "and t.tenant_record_id = :tenantRecordId\n" +
                                                                                ";");
        Assert.assertEquals(kombucha.getInstanceOf("getRecordId").toString(), "select\n" +
                                                                              "  t.record_id\n" +
                                                                              "from kombucha t\n" +
                                                                              "where t.id = :id\n" +
                                                                              "and t.tenant_record_id = :tenantRecordId\n" +
                                                                              ";");
        Assert.assertEquals(kombucha.getInstanceOf("getHistoryRecordId").toString(), "select\n" +
                                                                                     "  max(t.record_id)\n" +
                                                                                     "from kombucha_history t\n" +
                                                                                     "where t.target_record_id = :targetRecordId\n" +
                                                                                     "and t.tenant_record_id = :tenantRecordId\n" +
                                                                                     ";");
        Assert.assertEquals(kombucha.getInstanceOf("getAll").toString(), "select\n" +
                                                                         "  t.record_id\n" +
                                                                         ", t.id\n" +
                                                                         ", t.tea\n" +
                                                                         ", t.mushroom\n" +
                                                                         ", t.sugar\n" +
                                                                         ", t.account_record_id\n" +
                                                                         ", t.tenant_record_id\n" +
                                                                         "from kombucha t\n" +
                                                                         "where t.tenant_record_id = :tenantRecordId\n" +
                                                                         "order by t.record_id ASC\n" +
                                                                         ";");
        Assert.assertEquals(kombucha.getInstanceOf("get", ImmutableMap.<String, String>of("orderBy", "recordId", "offset", "3", "rowCount", "12")).toString(), "select SQL_CALC_FOUND_ROWS\n" +
                                                                                                                                                               "  t.record_id\n" +
                                                                                                                                                               ", t.id\n" +
                                                                                                                                                               ", t.tea\n" +
                                                                                                                                                               ", t.mushroom\n" +
                                                                                                                                                               ", t.sugar\n" +
                                                                                                                                                               ", t.account_record_id\n" +
                                                                                                                                                               ", t.tenant_record_id\n" +
                                                                                                                                                               "from kombucha t\n" +
                                                                                                                                                               "where t.tenant_record_id = :tenantRecordId\n" +
                                                                                                                                                               "order by :orderBy\n" +
                                                                                                                                                               "limit :offset, :rowCount\n" +
                                                                                                                                                               ";");
        Assert.assertEquals(kombucha.getInstanceOf("test").toString(), "select\n" +
                                                                       "  t.record_id\n" +
                                                                       ", t.id\n" +
                                                                       ", t.tea\n" +
                                                                       ", t.mushroom\n" +
                                                                       ", t.sugar\n" +
                                                                       ", t.account_record_id\n" +
                                                                       ", t.tenant_record_id\n" +
                                                                       "from kombucha t\n" +
                                                                       "where t.tenant_record_id = :tenantRecordId\n" +
                                                                       "limit 1\n" +
                                                                       ";");
        Assert.assertEquals(kombucha.getInstanceOf("addHistoryFromTransaction").toString(), "insert into kombucha_history (\n" +
                                                                                            "  id\n" +
                                                                                            ", target_record_id\n" +
                                                                                            ", change_type\n" +
                                                                                            ", tea\n" +
                                                                                            ", mushroom\n" +
                                                                                            ", sugar\n" +
                                                                                            ", account_record_id\n" +
                                                                                            ", tenant_record_id\n" +
                                                                                            ")\n" +
                                                                                            "values (\n" +
                                                                                            "  :id\n" +
                                                                                            ", :targetRecordId\n" +
                                                                                            ", :changeType\n" +
                                                                                            ",   :tea\n" +
                                                                                            ", :mushroom\n" +
                                                                                            ", :sugar\n" +
                                                                                            ", :accountRecordId\n" +
                                                                                            ", :tenantRecordId\n" +
                                                                                            ")\n" +
                                                                                            ";");

        Assert.assertEquals(kombucha.getInstanceOf("insertAuditFromTransaction").toString(), "insert into audit_log (\n" +
                                                                                             "id\n" +
                                                                                             ", table_name\n" +
                                                                                             ", target_record_id\n" +
                                                                                             ", change_type\n" +
                                                                                             ", created_by\n" +
                                                                                             ", reason_code\n" +
                                                                                             ", comments\n" +
                                                                                             ", user_token\n" +
                                                                                             ", created_date\n" +
                                                                                             ", account_record_id\n" +
                                                                                             ", tenant_record_id\n" +
                                                                                             ")\n" +
                                                                                             "values (\n" +
                                                                                             "  :id\n" +
                                                                                             ", :tableName\n" +
                                                                                             ", :targetRecordId\n" +
                                                                                             ", :changeType\n" +
                                                                                             ", :createdBy\n" +
                                                                                             ", :reasonCode\n" +
                                                                                             ", :comments\n" +
                                                                                             ", :userToken\n" +
                                                                                             ", :createdDate\n" +
                                                                                             ", :accountRecordId\n" +
                                                                                             ", :tenantRecordId\n" +
                                                                                             ")\n" +
                                                                                             ";");
    }
}
