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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import com.ning.billing.account.api.Account;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.customfield.CustomField;

public class BusinessAccountFieldModelDao extends BusinessFieldModelDao {

    public BusinessAccountFieldModelDao(final Account account,
                                        final Long accountRecordId,
                                        final CustomField customField,
                                        final Long customFieldRecordId,
                                        final AuditLog creationAuditLog,
                                        final Long tenantRecordId) {
        super(account,
              accountRecordId,
              customField,
              customFieldRecordId,
              creationAuditLog,
              tenantRecordId);
    }

    @Override
    public String getTableName() {
        return ACCOUNT_FIELDS_TABLE_NAME;
    }
}
