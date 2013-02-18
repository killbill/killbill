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

package com.ning.billing.osgi.bundles.test.dao;


import java.math.BigDecimal;
import java.util.UUID;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

public class TestDao {

    private final IDBI dbi;

    public TestDao(final IDBI dbi) {
        this.dbi = dbi;
    }

    public void createTable() {

        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                conn.execute("DROP TABLE IF EXISTS test_bundle;");
                conn.execute("CREATE TABLE test_bundle (" +
                "record_id int(11) unsigned NOT NULL AUTO_INCREMENT, " +
                "is_started bool DEFAULT false, " +
                "is_logged bool DEFAULT false, " +
                "external_key varchar(128) NULL, " +
                "payment_id char(36) NULL," +
                "payment_method_id char(36) NULL," +
                "payment_amount decimal(10,4) NULL," +
                "PRIMARY KEY(record_id)" +
                ");");
                return null;
            }
        });
    }

    public void insertStarted() {
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                conn.execute("INSERT INTO test_bundle (is_started) VALUES (1);");
                return null;
            }
        });
    }

    public void insertAccountExternalKey(final String externalKey) {
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                conn.execute("UPDATE test_bundle SET external_key = '" + externalKey + "' WHERE record_id = 1;");
                return null;
            }
        });
    }

    public void insertProcessedPayment(final UUID paymentId, final UUID paymentMethodId, final BigDecimal paymentAmount) {
        dbi.inTransaction(new TransactionCallback<Object>() {
            @Override
            public Object inTransaction(final Handle conn, final TransactionStatus status) throws Exception {
                conn.execute("UPDATE test_bundle SET payment_id = '" + paymentId.toString() +
                             "', payment_method_id = '" + paymentMethodId.toString() +
                             "', payment_amount = " + paymentAmount +
                             " WHERE record_id = 1;");
                return null;
            }
        });
    }
}
