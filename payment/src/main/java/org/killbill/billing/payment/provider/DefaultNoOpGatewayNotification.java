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
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.plugin.api.GatewayNotification;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DefaultNoOpGatewayNotification implements GatewayNotification {

    @Override
    public UUID getKbPaymentId() {
        return null;
    }

    @Override
    public int getStatus() {
        return 200;
    }

    @Override
    public String getEntity() {
        return null;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return ImmutableMap.<String, List<String>>of();
    }

    @Override
    public List<PluginProperty> getProperties() {
        return ImmutableList.<PluginProperty>of();
    }
}
