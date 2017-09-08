/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.entitlement.plugin.api.PriorEntitlementResult;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;

import com.google.common.collect.Iterables;

public class DefaultEntitlementContext implements EntitlementContext {

    private final OperationType operationType;
    private final UUID accountId;
    private final UUID destinationAccountId;
    private final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers;
    private final BillingActionPolicy billingActionPolicy;
    private final Iterable<PluginProperty> pluginProperties;
    private final UUID userToken;
    private final String userName;
    private final CallOrigin callOrigin;
    private final UserType userType;
    private final String reasonCode;
    private final String comments;
    private final DateTime createdDate;
    private final DateTime updatedDate;
    private final UUID tenantId;

    public DefaultEntitlementContext(final EntitlementContext prev,
                                     @Nullable final PriorEntitlementResult pluginResult) {
        this(prev.getOperationType(),
             prev.getAccountId(),
             prev.getDestinationAccountId(),
             pluginResult != null ? merge(prev.getBaseEntitlementWithAddOnsSpecifiers(), pluginResult.getAdjustedBaseEntitlementWithAddOnsSpecifiers()) : prev.getBaseEntitlementWithAddOnsSpecifiers(),
             pluginResult != null && pluginResult.getAdjustedBillingActionPolicy() != null ? pluginResult.getAdjustedBillingActionPolicy() : prev.getBillingActionPolicy(),
             pluginResult != null ? merge(prev.getPluginProperties(), pluginResult.getAdjustedPluginProperties()) : prev.getPluginProperties(),
             prev);
    }

    private static <T> Iterable<T> merge(final Iterable<T> prevValues, final Iterable<T> newValues) {
        // Be lenient if a plugin returns an empty list (default behavior for Ruby plugins): at this point,
        // we know the isAborted flag hasn't been set, so let's assume the user actually wants to use the previous list
        if (newValues != null && !Iterables.isEmpty(newValues)) {
            return newValues;
        } else {
            return prevValues;
        }
    }

    public DefaultEntitlementContext(final OperationType operationType,
                                     final UUID accountId,
                                     final UUID destinationAccountId,
                                     final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers,
                                     @Nullable final BillingActionPolicy actionPolicy,
                                     final Iterable<PluginProperty> pluginProperties,
                                     final CallContext callContext) {
        this(operationType, accountId, destinationAccountId, baseEntitlementWithAddOnsSpecifiers, actionPolicy, pluginProperties,
             callContext.getUserToken(), callContext.getUserName(), callContext.getCallOrigin(), callContext.getUserType(), callContext.getReasonCode(),
             callContext.getComments(), callContext.getCreatedDate(), callContext.getUpdatedDate(), callContext.getTenantId());
    }


    public DefaultEntitlementContext(final OperationType operationType,
                                     final UUID accountId,
                                     final UUID destinationAccountId,
                                     final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers,
                                     @Nullable final BillingActionPolicy actionPolicy,
                                     final Iterable<PluginProperty> pluginProperties,
                                     final UUID userToken,
                                     final String userName,
                                     final CallOrigin callOrigin,
                                     final UserType userType,
                                     final String reasonCode,
                                     final String comments,
                                     final DateTime createdDate,
                                     final DateTime updatedDate,
                                     final UUID tenantId) {
        this.operationType = operationType;
        this.accountId = accountId;
        this.destinationAccountId = destinationAccountId;
        this.baseEntitlementWithAddOnsSpecifiers = baseEntitlementWithAddOnsSpecifiers;
        this.billingActionPolicy = actionPolicy;
        this.pluginProperties = pluginProperties;
        this.userToken = userToken;
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
        this.reasonCode = reasonCode;
        this.comments = comments;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
        this.tenantId = tenantId;
    }

    @Override
    public OperationType getOperationType() {
        return operationType;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    @Override
    public Iterable<BaseEntitlementWithAddOnsSpecifier> getBaseEntitlementWithAddOnsSpecifiers() {
        return baseEntitlementWithAddOnsSpecifiers;
    }

    @Override
    public BillingActionPolicy getBillingActionPolicy() {
        return billingActionPolicy;
    }

    @Override
    public Iterable<PluginProperty> getPluginProperties() {
        return pluginProperties;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    @Override
    public UserType getUserType() {
        return userType;
    }

    @Override
    public String getReasonCode() {
        return reasonCode;
    }

    @Override
    public String getComments() {
        return comments;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public UUID getTenantId() {
        return tenantId;
    }
}
