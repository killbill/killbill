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

package org.killbill.billing.entitlement.api.svcs;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.entitlement.AccountEntitlements;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;

public class DefaultAccountEntitlements implements AccountEntitlements {

    private final AccountEventsStreams accountEventsStreams;
    private final Map<UUID, Collection<Entitlement>> entitlements;

    public DefaultAccountEntitlements(final AccountEventsStreams accountEventsStreams, final Map<UUID, Collection<Entitlement>> entitlements) {
        this.accountEventsStreams = accountEventsStreams;
        this.entitlements = entitlements;
    }

    @Override
    public ImmutableAccountData getAccount() {
        return accountEventsStreams.getAccount();
    }

    @Override
    public Map<UUID, SubscriptionBaseBundle> getBundles() {
        return accountEventsStreams.getBundles();
    }

    @Override
    public Map<UUID, Collection<Entitlement>> getEntitlements() {
        return entitlements;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultAccountEntitlements{");
        sb.append("accountEventsStreams=").append(accountEventsStreams);
        sb.append(", entitlements=").append(entitlements);
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

        final DefaultAccountEntitlements that = (DefaultAccountEntitlements) o;

        if (accountEventsStreams != null ? !accountEventsStreams.equals(that.accountEventsStreams) : that.accountEventsStreams != null) {
            return false;
        }
        if (entitlements != null ? !entitlements.equals(that.entitlements) : that.entitlements != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = accountEventsStreams != null ? accountEventsStreams.hashCode() : 0;
        result = 31 * result + (entitlements != null ? entitlements.hashCode() : 0);
        return result;
    }
}
