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

package org.killbill.billing.util.dao;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import org.antlr.stringtemplate.StringTemplateGroup;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class TestStringTemplateInheritance extends UtilTestSuiteNoDB {

    InputStream entityStream;
    InputStream kombuchaStream;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        entityStream = this.getClass().getResourceAsStream("/org/killbill/billing/util/entity/dao/EntitySqlDao.sql.stg");
        kombuchaStream = this.getClass().getResourceAsStream("/org/killbill/billing/util/dao/Kombucha.sql.stg");
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
        Assert.assertEquals(kombucha.getInstanceOf("isIsTimeForKombucha").toString(), "select hour(current_timestamp(0)) = 17 as is_time;");

        // Verify inherited templates
        assertPattern(kombucha.getInstanceOf("getById").toString(), "select\r?\n" +
                                                                    "  t.record_id\r?\n" +
                                                                    ", t.id\r?\n" +
                                                                    ", t.tea\r?\n" +
                                                                    ", t.mushroom\r?\n" +
                                                                    ", t.sugar\r?\n" +
                                                                    ", t.account_record_id\r?\n" +
                                                                    ", t.tenant_record_id\r?\n" +
                                                                    "from kombucha t\r?\n" +
                                                                    "where t.id = :id\r?\n" +
                                                                    "and t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                    ";");
        assertPattern(kombucha.getInstanceOf("getByRecordId").toString(), "select\r?\n" +
                                                                          "  t.record_id\r?\n" +
                                                                          ", t.id\r?\n" +
                                                                          ", t.tea\r?\n" +
                                                                          ", t.mushroom\r?\n" +
                                                                          ", t.sugar\r?\n" +
                                                                          ", t.account_record_id\r?\n" +
                                                                          ", t.tenant_record_id\r?\n" +
                                                                          "from kombucha t\r?\n" +
                                                                          "where t.record_id = :recordId\r?\n" +
                                                                          "and t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                          ";");
        assertPattern(kombucha.getInstanceOf("getRecordId").toString(), "select\r?\n" +
                                                                        "  t.record_id\r?\n" +
                                                                        "from kombucha t\r?\n" +
                                                                        "where t.id = :id\r?\n" +
                                                                        "and t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                        ";");
        assertPattern(kombucha.getInstanceOf("getHistoryRecordId").toString(), "select\r?\n" +
                                                                               "  max\\(t.record_id\\)\r?\n" +
                                                                               "from kombucha_history t\r?\n" +
                                                                               "where t.target_record_id = :targetRecordId\r?\n" +
                                                                               "and t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                               ";");
        assertPattern(kombucha.getInstanceOf("getAll").toString(), "select\r?\n" +
                                                                   "  t.record_id\r?\n" +
                                                                   ", t.id\r?\n" +
                                                                   ", t.tea\r?\n" +
                                                                   ", t.mushroom\r?\n" +
                                                                   ", t.sugar\r?\n" +
                                                                   ", t.account_record_id\r?\n" +
                                                                   ", t.tenant_record_id\r?\n" +
                                                                   "from kombucha t\r?\n" +
                                                                   "where t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                   "order by t.record_id ASC\r?\n" +
                                                                   ";");
        assertPattern(kombucha.getInstanceOf("get", ImmutableMap.<String, String>of("orderBy", "record_id", "offset", "3", "rowCount", "12", "ordering", "ASC")).toString(), "select\r?\n" +
                                                                                                                                                                             "  t.record_id\r?\n" +
                                                                                                                                                                             ", t.id\r?\n" +
                                                                                                                                                                             ", t.tea\r?\n" +
                                                                                                                                                                             ", t.mushroom\r?\n" +
                                                                                                                                                                             ", t.sugar\r?\n" +
                                                                                                                                                                             ", t.account_record_id\r?\n" +
                                                                                                                                                                             ", t.tenant_record_id\r?\n" +
                                                                                                                                                                             "from kombucha t\r?\n" +
                                                                                                                                                                             "join \\(\r?\n" +
                                                                                                                                                                             "  select record_id\r?\n" +
                                                                                                                                                                             "  from kombucha\r?\n" +
                                                                                                                                                                             "  where tenant_record_id = :tenantRecordId\r?\n" +
                                                                                                                                                                             "  order by record_id ASC\r?\n" +
                                                                                                                                                                             "  limit :rowCount offset :offset\r?\n" +
                                                                                                                                                                             "\\) optimization on optimization.record_id = t.record_id\r?\n" +
                                                                                                                                                                             "order by t.record_id ASC\r?\n" +
                                                                                                                                                                             ";");
        assertPattern(kombucha.getInstanceOf("test").toString(), "select\r?\n" +
                                                                 "  t.record_id\r?\n" +
                                                                 ", t.id\r?\n" +
                                                                 ", t.tea\r?\n" +
                                                                 ", t.mushroom\r?\n" +
                                                                 ", t.sugar\r?\n" +
                                                                 ", t.account_record_id\r?\n" +
                                                                 ", t.tenant_record_id\r?\n" +
                                                                 "from kombucha t\r?\n" +
                                                                 "where t.tenant_record_id = :tenantRecordId\r?\n" +
                                                                 "limit 1\r?\n" +
                                                                 ";");
        assertPattern(kombucha.getInstanceOf("addHistoryFromTransaction").toString(), "insert into kombucha_history \\(\r?\n" +
                                                                                      "  id\r?\n" +
                                                                                      ", target_record_id\r?\n" +
                                                                                      ", change_type\r?\n" +
                                                                                      ", tea\r?\n" +
                                                                                      ", mushroom\r?\n" +
                                                                                      ", sugar\r?\n" +
                                                                                      ", account_record_id\r?\n" +
                                                                                      ", tenant_record_id\r?\n" +
                                                                                      "\\)\r?\n" +
                                                                                      "values \\(\r?\n" +
                                                                                      "  :id\r?\n" +
                                                                                      ", :targetRecordId\r?\n" +
                                                                                      ", :changeType\r?\n" +
                                                                                      ",   :tea\r?\n" +
                                                                                      ", :mushroom\r?\n" +
                                                                                      ", :sugar\r?\n" +
                                                                                      ", :accountRecordId\r?\n" +
                                                                                      ", :tenantRecordId\r?\n" +
                                                                                      "\\)\r?\n" +
                                                                                      ";");

        assertPattern(kombucha.getInstanceOf("insertAuditFromTransaction").toString(), "insert into audit_log \\(\r?\n" +
                                                                                       "id\r?\n" +
                                                                                       ", table_name\r?\n" +
                                                                                       ", target_record_id\r?\n" +
                                                                                       ", change_type\r?\n" +
                                                                                       ", created_by\r?\n" +
                                                                                       ", reason_code\r?\n" +
                                                                                       ", comments\r?\n" +
                                                                                       ", user_token\r?\n" +
                                                                                       ", created_date\r?\n" +
                                                                                       ", account_record_id\r?\n" +
                                                                                       ", tenant_record_id\r?\n" +
                                                                                       "\\)\r?\n" +
                                                                                       "values \\(\r?\n" +
                                                                                       "  :id\r?\n" +
                                                                                       ", :tableName\r?\n" +
                                                                                       ", :targetRecordId\r?\n" +
                                                                                       ", :changeType\r?\n" +
                                                                                       ", :createdBy\r?\n" +
                                                                                       ", :reasonCode\r?\n" +
                                                                                       ", :comments\r?\n" +
                                                                                       ", :userToken\r?\n" +
                                                                                       ", :createdDate\r?\n" +
                                                                                       ", :accountRecordId\r?\n" +
                                                                                       ", :tenantRecordId\r?\n" +
                                                                                       "\\)\r?\n" +
                                                                                       ";");
    }

    private void assertPattern(final String actual, final String expected) {
        Assert.assertTrue(Pattern.compile(expected).matcher(actual).find(), String.format("Expected to see:\n%s\nin:\n%s", expected, actual));
    }
}
