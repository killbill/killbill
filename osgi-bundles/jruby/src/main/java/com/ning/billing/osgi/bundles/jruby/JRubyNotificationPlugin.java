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

package com.ning.billing.osgi.bundles.jruby;

import javax.annotation.Nullable;

import org.jruby.embed.ScriptingContainer;
import org.jruby.javasupport.JavaEmbedUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.osgi.api.config.PluginRubyConfig;

import com.google.common.eventbus.Subscribe;

public class JRubyNotificationPlugin extends JRubyPlugin {

    public JRubyNotificationPlugin(final PluginRubyConfig config, final ScriptingContainer container, @Nullable final LogService logger) {
        super(config, container, logger);
    }

    @Override
    public void startPlugin(final BundleContext context) {
        super.startPlugin(context);

        @SuppressWarnings("unchecked")
        final ServiceReference<ExternalBus> externalBusReference = (ServiceReference<ExternalBus>) context.getServiceReference(ExternalBus.class.getName());
        try {
            final ExternalBus externalBus = context.getService(externalBusReference);
            externalBus.register(this);
        } catch (Exception e) {
            log(LogService.LOG_WARNING, "Error registering notification plugin service", e);
        } finally {
            if (externalBusReference != null) {
                context.ungetService(externalBusReference);
            }
        }
    }

    @Subscribe
    public void onEvent(final ExtBusEvent event) {
        checkValidNotificationPlugin();
        checkPluginIsRunning();

        pluginInstance.callMethod("on_event", JavaEmbedUtils.javaToRuby(getRuntime(), event));
    }
}
