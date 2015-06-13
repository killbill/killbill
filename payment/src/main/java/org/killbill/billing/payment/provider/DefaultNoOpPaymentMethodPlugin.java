/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import javax.annotation.Nullable;

import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.UUIDs;

import com.google.common.collect.ImmutableList;

public class DefaultNoOpPaymentMethodPlugin implements PaymentMethodPlugin {

    private final UUID kbPaymentMethodId;
    private final String externalId;
    private final boolean isDefault;
    private List<PluginProperty> props;

    public DefaultNoOpPaymentMethodPlugin(final UUID kbPaymentMethodId, final PaymentMethodPlugin src) {
        this.kbPaymentMethodId = kbPaymentMethodId;
        this.externalId = UUIDs.randomUUID().toString();
        this.isDefault = src.isDefaultPaymentMethod();
        this.props = src.getProperties();
    }

    public DefaultNoOpPaymentMethodPlugin(final String externalId,
                                          final boolean isDefault,
                                          final Iterable<PluginProperty> props) {
        this(null, externalId, isDefault, props);
    }

    public DefaultNoOpPaymentMethodPlugin(@Nullable final UUID kbPaymentMethodId,
                                          final String externalId,
                                          final boolean isDefault,
                                          @Nullable final Iterable<PluginProperty> props) {
        this.kbPaymentMethodId = kbPaymentMethodId;
        this.externalId = externalId;
        this.isDefault = isDefault;
        this.props = props == null ? ImmutableList.<PluginProperty>of() : ImmutableList.<PluginProperty>copyOf(props);
    }

    @Override
    public UUID getKbPaymentMethodId() {
        return kbPaymentMethodId;
    }

    @Override
    public String getExternalPaymentMethodId() {
        return externalId;
    }

    @Override
    public boolean isDefaultPaymentMethod() {
        return isDefault;
    }

    @Override
    public List<PluginProperty> getProperties() {
        return props;
    }

    public void setProps(final List<PluginProperty> props) {
        this.props = props;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultNoOpPaymentMethodPlugin");
        sb.append("{externalId='").append(externalId).append('\'');
        sb.append(", isDefault=").append(isDefault);
        sb.append(", props=").append(props);
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

        final DefaultNoOpPaymentMethodPlugin that = (DefaultNoOpPaymentMethodPlugin) o;

        if (isDefault != that.isDefault) {
            return false;
        }
        if (externalId != null ? !externalId.equals(that.externalId) : that.externalId != null) {
            return false;
        }
        if (props != null ? !props.equals(that.props) : that.props != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = externalId != null ? externalId.hashCode() : 0;
        result = 31 * result + (isDefault ? 1 : 0);
        result = 31 * result + (props != null ? props.hashCode() : 0);
        return result;
    }
}
