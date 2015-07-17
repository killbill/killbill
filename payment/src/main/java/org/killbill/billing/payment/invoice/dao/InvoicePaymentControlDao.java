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

package org.killbill.billing.payment.invoice.dao;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;

public class InvoicePaymentControlDao {

    private final IDBI dbi;

    @Inject
    public InvoicePaymentControlDao(final IDBI dbi) {
        this.dbi = dbi;
    }

    public void insertAutoPayOff(final PluginAutoPayOffModelDao data) {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                final String paymentId = data.getPaymentId() != null ? data.getPaymentId().toString() : null;
                final String paymentMethodId = data.getPaymentMethodId() != null ? data.getPaymentMethodId().toString() : null;
                handle.execute("insert into _invoice_payment_control_plugin_auto_pay_off " +
                               "(attempt_id, payment_external_key, transaction_external_key, account_id, plugin_name, payment_id, payment_method_id, amount, currency, created_by, created_date) values " +
                               "(?,?,?,?,?,?,?,?,?,?,?)",
                               data.getAttemptId().toString(), data.getPaymentExternalKey(), data.getTransactionExternalKey(), data.getAccountId(), data.getPluginName(), paymentId, paymentMethodId,
                               data.getAmount(), data.getCurrency(), data.getCreatedBy(), data.getCreatedDate()
                              );
                return null;
            }
        });
    }

    public List<PluginAutoPayOffModelDao> getAutoPayOffEntry(final UUID accountId) {
        return dbi.withHandle(new HandleCallback<List<PluginAutoPayOffModelDao>>() {
            @Override
            public List<PluginAutoPayOffModelDao> withHandle(final Handle handle) throws Exception {
                final List<Map<String, Object>> queryResult = handle.select("select * from _invoice_payment_control_plugin_auto_pay_off where account_id = ? and is_active", accountId.toString());
                final List<PluginAutoPayOffModelDao> result = new ArrayList<PluginAutoPayOffModelDao>(queryResult.size());
                for (final Map<String, Object> row : queryResult) {

                    final PluginAutoPayOffModelDao entry = new PluginAutoPayOffModelDao(Long.valueOf(row.get("record_id").toString()),
                                                                                        UUID.fromString((String) row.get("attempt_id")),
                                                                                        (String) row.get("payment_external_key"),
                                                                                        (String) row.get("transaction_external_key"),
                                                                                        UUID.fromString((String) row.get("account_id")),
                                                                                        (String) row.get("plugin_name"),
                                                                                        row.get("payment_id") != null ? UUID.fromString((String) row.get("payment_id")) : null,
                                                                                        UUID.fromString((String) row.get("payment_method_id")),
                                                                                        (BigDecimal) row.get("amount"),
                                                                                        Currency.valueOf((String) row.get("currency")),
                                                                                        (String) row.get("created_by"),
                                                                                        getDateTime(row.get("created_date")));
                    result.add(entry);

                }
                return result;
            }
        });
    }

    public void removeAutoPayOffEntry(final UUID accountId) {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("update _invoice_payment_control_plugin_auto_pay_off set is_active = false where account_id = ?", accountId.toString());
                return null;
            }
        });
    }

    protected DateTime getDateTime(final Object timestamp) throws SQLException {
        final Timestamp resultStamp = (Timestamp) timestamp;
        return new DateTime(resultStamp).toDateTime(DateTimeZone.UTC);
    }
}
