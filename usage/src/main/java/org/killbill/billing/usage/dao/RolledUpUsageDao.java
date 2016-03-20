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

package org.killbill.billing.usage.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;

public interface RolledUpUsageDao {

    void record(Iterable<RolledUpUsageModelDao> usages, InternalCallContext context);

    Boolean recordsWithTrackingIdExist(UUID subscriptionId, String trackingId, InternalTenantContext context);

    List<RolledUpUsageModelDao> getUsageForSubscription(UUID subscriptionId, LocalDate startDate, LocalDate endDate, String unitType, InternalTenantContext context);

    List<RolledUpUsageModelDao> getAllUsageForSubscription(UUID subscriptionId, LocalDate startDate, LocalDate endDate, InternalTenantContext context);

    List<RolledUpUsageModelDao> getRawUsageForAccount(LocalDate startDate, LocalDate endDate, InternalTenantContext context);
}
