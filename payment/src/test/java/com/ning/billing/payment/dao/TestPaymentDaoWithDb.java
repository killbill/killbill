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

package com.ning.billing.payment.dao;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;

@Test(enabled = true, groups = { "slow"})
public class TestPaymentDaoWithDb extends TestPaymentDao {
    
    private MysqlTestingHelper helper;
    private IDBI dbi;
    
    @BeforeClass(groups = { "slow"})
    public void startMysql() throws IOException {
        final String paymentddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));

        setupDb();
        
        helper.startMysql();
        helper.initDb(paymentddl);
        helper.initDb(utilddl);
    }
    
    private void setupDb() {
        helper = new MysqlTestingHelper();
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
    }

    @AfterClass(groups = { "slow"})
    public void stopMysql() {
        helper.stopMysql();
    }

    @BeforeMethod(groups = { "slow"})
    public void setUp() throws IOException {
        paymentDao = new AuditedPaymentDao(dbi);
    }
    
    @Test(groups={"slow"})
    public void testGetPaymentForInvoice() throws AccountApiException {
        super.testGetPaymentForInvoice();
    }
}
