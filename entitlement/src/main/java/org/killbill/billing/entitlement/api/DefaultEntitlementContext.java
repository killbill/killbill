/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.List;
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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DefaultEntitlementContext implements EntitlementContext {

    private final OperationType operationType;
    private final UUID accountId;
    private final UUID destinationAccountId;
    private final List<DefaultBaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers;
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
             pluginResult != null ? lastUnlessEmptyOrFirst(prev.getBaseEntitlementWithAddOnsSpecifiers(), pluginResult.getAdjustedBaseEntitlementWithAddOnsSpecifiers()) : prev.getBaseEntitlementWithAddOnsSpecifiers(),
             pluginResult != null && pluginResult.getAdjustedBillingActionPolicy() != null ? pluginResult.getAdjustedBillingActionPolicy() : prev.getBillingActionPolicy(),
             pluginResult != null ? lastUnlessEmptyOrFirst(prev.getPluginProperties(), pluginResult.getAdjustedPluginProperties()) : prev.getPluginProperties(),
             prev);
    }

    public DefaultEntitlementContext(final OperationType operationType,
                                     final UUID accountId,
                                     final UUID destinationAccountId,
                                     @Nullable final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers,
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
                                     @Nullable final Iterable<BaseEntitlementWithAddOnsSpecifier> baseEntitlementWithAddOnsSpecifiers,
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
        if (baseEntitlementWithAddOnsSpecifiers == null) {
            this.baseEntitlementWithAddOnsSpecifiers = ImmutableList.<DefaultBaseEntitlementWithAddOnsSpecifier>of();
        } else {
            this.baseEntitlementWithAddOnsSpecifiers = ImmutableList.<DefaultBaseEntitlementWithAddOnsSpecifier>copyOf(Iterables.<BaseEntitlementWithAddOnsSpecifier, DefaultBaseEntitlementWithAddOnsSpecifier>transform(baseEntitlementWithAddOnsSpecifiers,
                                                                                                                                                                                                                          new Function<BaseEntitlementWithAddOnsSpecifier, DefaultBaseEntitlementWithAddOnsSpecifier>() {
                                                                                                                                                                                                                              @Override
                                                                                                                                                                                                                              public DefaultBaseEntitlementWithAddOnsSpecifier apply(final BaseEntitlementWithAddOnsSpecifier input) {
                                                                                                                                                                                                                                  return new DefaultBaseEntitlementWithAddOnsSpecifier(input);
                                                                                                                                                                                                                              }
                                                                                                                                                                                                                          }));
        }
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

    private static <T> Iterable<T> lastUnlessEmptyOrFirst(final Iterable<T> prevValues, final Iterable<T> newValues) {
        // Be lenient if a plugin returns an empty list (default behavior for Ruby plugins): at this point,
        // we know the isAborted flag hasn't been set, so let's assume the user actually wants to use the previous list
        if (newValues != null && !Iterables.isEmpty(newValues)) {
            return newValues;
        } else {
            return prevValues;
        }
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
        //noinspection unchecked
        return (Iterable) baseEntitlementWithAddOnsSpecifiers;
    }

    public DefaultBaseEntitlementWithAddOnsSpecifier getBaseEntitlementWithAddOnsSpecifiers(final int idx) {
        return baseEntitlementWithAddOnsSpecifiers.get(idx);
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultEntitlementContext{");
        sb.append("operationType=").append(operationType);
        sb.append(", accountId=").append(accountId);
        sb.append(", destinationAccountId=").append(destinationAccountId);
        sb.append(", baseEntitlementWithAddOnsSpecifiers=").append(baseEntitlementWithAddOnsSpecifiers);
        sb.append(", billingActionPolicy=").append(billingActionPolicy);
        sb.append(", pluginProperties=").append(pluginProperties);
        sb.append(", userToken=").append(userToken);
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", callOrigin=").append(callOrigin);
        sb.append(", userType=").append(userType);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comments='").append(comments).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", tenantId=").append(tenantId);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultEntitlementContext that = (DefaultEntitlementContext) o;

        if (operationType != that.operationType) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (destinationAccountId != null ? !destinationAccountId.equals(that.destinationAccountId) : that.destinationAccountId != null) {
            return false;
        }
        if (baseEntitlementWithAddOnsSpecifiers != null ? !baseEntitlementWithAddOnsSpecifiers.equals(that.baseEntitlementWithAddOnsSpecifiers) : that.baseEntitlementWithAddOnsSpecifiers != null) {
            return false;
        }
        if (billingActionPolicy != that.billingActionPolicy) {
            return false;
        }
        if (pluginProperties != null ? !pluginProperties.equals(that.pluginProperties) : that.pluginProperties != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }
        if (userName != null ? !userName.equals(that.userName) : that.userName != null) {
            return false;
        }
        if (callOrigin != that.callOrigin) {
            return false;
        }
        if (userType != that.userType) {
            return false;
        }
        if (reasonCode != null ? !reasonCode.equals(that.reasonCode) : that.reasonCode != null) {
            return false;
        }
        if (comments != null ? !comments.equals(that.comments) : that.comments != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }
        return tenantId != null ? tenantId.equals(that.tenantId) : that.tenantId == null;
    }

    @Override
    public int hashCode() {
        int result = operationType != null ? operationType.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (destinationAccountId != null ? destinationAccountId.hashCode() : 0);
        result = 31 * result + (baseEntitlementWithAddOnsSpecifiers != null ? baseEntitlementWithAddOnsSpecifiers.hashCode() : 0);
        result = 31 * result + (billingActionPolicy != null ? billingActionPolicy.hashCode() : 0);
        result = 31 * result + (pluginProperties != null ? pluginProperties.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (userName != null ? userName.hashCode() : 0);
        result = 31 * result + (callOrigin != null ? callOrigin.hashCode() : 0);
        result = 31 * result + (userType != null ? userType.hashCode() : 0);
        result = 31 * result + (reasonCode != null ? reasonCode.hashCode() : 0);
        result = 31 * result + (comments != null ? comments.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        return result;
    }
}
