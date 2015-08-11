/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

package org.killbill.billing.payment.provider;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;

import com.google.common.collect.ImmutableList;

public class DefaultNoOpHostedPaymentPageFormDescriptor implements HostedPaymentPageFormDescriptor {

    private final UUID kbAccountId;

    public DefaultNoOpHostedPaymentPageFormDescriptor(final UUID kbAccountId) {
        this.kbAccountId = kbAccountId;
    }

    @Override
    public UUID getKbAccountId() {
        return kbAccountId;
    }

    @Override
    public String getFormMethod() {
        return null;
    }

    @Override
    public String getFormUrl() {
        return null;
    }

    @Override
    public List<PluginProperty> getFormFields() {
        return ImmutableList.<PluginProperty>of();
    }

    @Override
    public List<PluginProperty> getProperties() {
        return ImmutableList.<PluginProperty>of();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultNoOpHostedPaymentPageFormDescriptor{");
        sb.append("kbAccountId=").append(kbAccountId);
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

        final DefaultNoOpHostedPaymentPageFormDescriptor that = (DefaultNoOpHostedPaymentPageFormDescriptor) o;

        return !(kbAccountId != null ? !kbAccountId.equals(that.kbAccountId) : that.kbAccountId != null);
    }

    @Override
    public int hashCode() {
        return kbAccountId != null ? kbAccountId.hashCode() : 0;
    }
}
