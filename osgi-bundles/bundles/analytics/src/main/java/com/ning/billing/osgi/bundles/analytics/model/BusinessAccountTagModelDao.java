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

package com.ning.billing.osgi.bundles.analytics.model;

import java.util.UUID;

import org.joda.time.DateTime;

public class BusinessAccountTagModelDao extends BusinessTagModelDao {

    private static final String ACCOUNT_TAGS_TABLE_NAME = "bac_tags";

    public BusinessAccountTagModelDao(final String name,
                                      final DateTime createdDate,
                                      final String createdBy,
                                      final String createdReasonCode,
                                      final String createdComments,
                                      final UUID accountId,
                                      final String accountName,
                                      final String accountExternalKey,
                                      final Long accountRecordId,
                                      final Long tenantRecordId) {
        super(name,
              createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId);
    }

    @Override
    public String getTableName() {
        return ACCOUNT_TAGS_TABLE_NAME;
    }
}
