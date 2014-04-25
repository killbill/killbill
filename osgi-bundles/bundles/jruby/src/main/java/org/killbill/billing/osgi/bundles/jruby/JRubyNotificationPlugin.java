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

package org.killbill.billing.osgi.bundles.jruby;

import org.jruby.Ruby;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.NotificationPluginApi;
import org.killbill.billing.osgi.api.config.PluginRubyConfig;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class JRubyNotificationPlugin extends JRubyPlugin implements OSGIKillbillEventHandler {

    public JRubyNotificationPlugin(final PluginRubyConfig config, final BundleContext bundleContext, final LogService logger) {
        super(config, bundleContext, logger);
    }

    @Override
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {
        try {
            callWithRuntimeAndChecking(new PluginCallback(VALIDATION_PLUGIN_TYPE.NOTIFICATION) {
                @Override
                public Void doCall(final Ruby runtime) throws PaymentPluginApiException {
                    ((NotificationPluginApi) pluginInstance).onEvent(killbillEvent);
                    return null;
                }
            });
        } catch (final PaymentPluginApiException e) {
            throw new IllegalStateException("Unexpected PaymentApiException for notification plugin", e);
        }
    }
}
