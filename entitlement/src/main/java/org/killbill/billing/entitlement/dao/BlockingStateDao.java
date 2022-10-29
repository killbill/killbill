/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.entitlement.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.VersionedCatalog;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.dao.EntityDao;

public interface BlockingStateDao extends EntityDao<BlockingStateModelDao, BlockingState, EntitlementApiException> {

    /**
     * Returns the current state for that specific service
     *
     * @param blockableId       id of the blockable object
     * @param blockingStateType blockable object type
     * @param serviceName       name of the service
     * @param context           call context
     * @return current blocking state for that blockable object and service
     */
    public BlockingState getBlockingStateForService(UUID blockableId, BlockingStateType blockingStateType, String serviceName, InternalTenantContext context);

    /**
     * Returns the current state across all the services
     *
     * @param blockableId       id of the blockable object
     * @param blockingStateType blockable object type
     * @param context           call context
     * @return list of current blocking states for that blockable object
     */
    public List<BlockingState> getBlockingState(UUID blockableId, BlockingStateType blockingStateType, DateTime upToDate, InternalTenantContext context);

    /**
     * Return all events (past and future) across all services for a given callcontext (account_record_id)
     *
     * @param catalog full catalog
     * @param context call context
     * @return list of all blocking states for that account
     */
    public List<BlockingState> getBlockingAllForAccountRecordId(VersionedCatalog catalog, InternalTenantContext context);

    /**
     * Return all events for tuple {service, blockable_id} having at least one block_billing
     *
     * @param catalog full catalog
     * @param context call context
     * @return list of active blocking states for that account
     */
    public List<BlockingState> getBlockingActiveForAccount(VersionedCatalog catalog, @Nullable final LocalDate cutoffDt, InternalTenantContext context);


    /**
     * Return all events (past and future) across all services for a given set of blockableIds
     *
     * @param blockableIds ids of the blockable object
     * @param includeDeletedEvents flag that indicates whether deleted blocking events should be returned
     * @param context call context
     * @return list of all blocking states for that account
     */
    public List<BlockingState> getByBlockingIds(Iterable<UUID> blockableIds, boolean includeDeletedEvents, InternalTenantContext context);



    /**
     * Set new blocking states
     *
     * @param states  blocking states to set (mapped to the associated bundle id if the blocking state type is SUBSCRIPTION)
     * @param context call context
     */
    public void setBlockingStatesAndPostBlockingTransitionEvent(Map<BlockingState, Optional<UUID>> states, InternalCallContext context);

    /**
     * Unactive the blocking state
     *
     * @param blockableId blockable id to unactivate
     * @param context     call context
     */
    public void unactiveBlockingState(UUID blockableId, final InternalCallContext context);

    /**
     * @param blockableId id of the blockable object
     * @param auditLevel  audit level
     * @param context     call context
     * @return the list of audit with history for this blockableId
     */
    List<AuditLogWithHistory> getBlockingStateAuditLogsWithHistoryForId(UUID blockableId, AuditLevel auditLevel, InternalTenantContext context);

}
