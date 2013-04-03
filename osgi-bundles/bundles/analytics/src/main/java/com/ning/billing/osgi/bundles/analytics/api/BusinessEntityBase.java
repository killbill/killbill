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

package com.ning.billing.osgi.bundles.analytics.api;

import java.util.UUID;

import org.joda.time.DateTime;

public abstract class BusinessEntityBase {

    protected final DateTime createdDate;
    protected final String createdBy;
    protected final String createdReasonCode;
    protected final String createdComments;
    protected final UUID accountId;
    protected final String accountName;
    protected final String accountExternalKey;

    public BusinessEntityBase(final DateTime createdDate, final String createdBy, final String createdReasonCode,
                              final String createdComments, final UUID accountId, final String accountName,
                              final String accountExternalKey) {
        this.createdDate = createdDate;
        this.createdBy = createdBy;
        this.createdReasonCode = createdReasonCode;
        this.createdComments = createdComments;
        this.accountId = accountId;
        this.accountName = accountName;
        this.accountExternalKey = accountExternalKey;
    }
}
