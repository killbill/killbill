/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;

public class MutableInternalCallContext extends InternalCallContext {

    private final Long initialAccountRecordId;
    private final Long initialTenantRecordId;
    private final DateTimeZone initialReferenceDateTimeZone;
    private final LocalTime initialReferenceTime;
    private final DateTime initialCreatedDate;
    private final DateTime initialUpdatedDate;

    private Long accountRecordId;
    private Long tenantRecordId;
    private DateTimeZone fixedOffsetTimeZone;
    private LocalTime referenceTime;
    private DateTime createdDate;
    private DateTime updatedDate;

    public MutableInternalCallContext(final Long tenantRecordId,
                                      @Nullable final Long accountRecordId,
                                      @Nullable final DateTimeZone fixedOffsetTimeZone,
                                      @Nullable final DateTime referenceTime,
                                      final UUID userToken,
                                      final String userName,
                                      final CallOrigin callOrigin,
                                      final UserType userType,
                                      final String reasonCode,
                                      final String comment,
                                      final DateTime createdDate,
                                      final DateTime updatedDate) {
        super(tenantRecordId, accountRecordId, fixedOffsetTimeZone, referenceTime, userToken, userName, callOrigin, userType, reasonCode, comment, createdDate, updatedDate);
        this.initialAccountRecordId = accountRecordId;
        this.initialTenantRecordId = tenantRecordId;
        this.initialReferenceDateTimeZone = fixedOffsetTimeZone;
        this.initialReferenceTime = super.getReferenceLocalTime();
        this.initialCreatedDate = createdDate;
        this.initialUpdatedDate = updatedDate;

        reset();
    }

    @Override
    public Long getAccountRecordId() {
        return accountRecordId;
    }

    public void setAccountRecordId(final Long accountRecordId) {
        this.accountRecordId = accountRecordId;
    }

    @Override
    public Long getTenantRecordId() {
        return tenantRecordId;
    }

    public void setTenantRecordId(final Long tenantRecordId) {
        this.tenantRecordId = tenantRecordId;
    }

    @Override
    public DateTimeZone getFixedOffsetTimeZone() {
        return fixedOffsetTimeZone;
    }

    public void setFixedOffsetTimeZone(final DateTimeZone fixedOffsetTimeZone) {
        this.fixedOffsetTimeZone = fixedOffsetTimeZone;
    }

    @Override
    public LocalTime getReferenceLocalTime() {
        return referenceTime;
    }

    public void setReferenceTime(final LocalTime referenceTime) {
        this.referenceTime = referenceTime;
    }

    public void setReferenceTime(final DateTime referenceDateTime) {
        this.referenceTime = computeReferenceTime(referenceDateTime);
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public void reset() {
        setAccountRecordId(initialAccountRecordId);
        setTenantRecordId(initialTenantRecordId);
        setFixedOffsetTimeZone(initialReferenceDateTimeZone);
        setReferenceTime(initialReferenceTime);
        setCreatedDate(initialCreatedDate);
        setUpdatedDate(initialUpdatedDate);
    }
}
