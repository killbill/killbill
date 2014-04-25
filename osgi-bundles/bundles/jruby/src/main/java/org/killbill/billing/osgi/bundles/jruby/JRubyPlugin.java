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

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.http.HttpServlet;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;
import org.killbill.billing.osgi.api.config.PluginRubyConfig;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Bridge between the OSGI bundle and the ruby plugin
public abstract class JRubyPlugin {

    private static final Logger log = LoggerFactory.getLogger(JRubyPlugin.class);

    // Killbill gem base classes
    private static final String KILLBILL_PLUGIN_BASE = "Killbill::Plugin::PluginBase";
    private static final String KILLBILL_PLUGIN_NOTIFICATION = "Killbill::Plugin::Notification";
    private static final String KILLBILL_PLUGIN_PAYMENT = "Killbill::Plugin::Payment";
    private static final String KILLBILL_PLUGIN_CURRENCY = "Killbill::Plugin::Currency";

    // Magic ruby variables
    private static final String KILLBILL_SERVICES = "java_apis";
    private static final String KILLBILL_PLUGIN_CLASS_NAME = "plugin_class_name";

    // Methods implemented by Killbill::Plugin::JPlugin
    private static final String START_PLUGIN_RUBY_METHOD_NAME = "start_plugin";
    private static final String STOP_PLUGIN_RUBY_METHOD_NAME = "stop_plugin";
    private static final String RACK_HANDLER_RUBY_METHOD_NAME = "rack_handler";

    private final Object pluginMonitor = new Object();

    protected final LogService logger;
    protected final BundleContext bundleContext;
    protected final String pluginGemName;
    protected final String rubyRequire;
    protected final String pluginMainClass;
    protected final String pluginLibdir;

    protected ScriptingContainer container;
    protected RubyObject pluginInstance;

    private ServiceRegistration httpServletServiceRegistration = null;
    private String cachedRequireLine = null;

    public JRubyPlugin(final PluginRubyConfig config, final BundleContext bundleContext, final LogService logger) {
        this.logger = logger;
        this.bundleContext = bundleContext;
        this.pluginGemName = config.getPluginName();
        this.rubyRequire = config.getRubyRequire();
        this.pluginMainClass = config.getRubyMainClass();
        this.pluginLibdir = config.getRubyLoadDir();
    }

    public void instantiatePlugin(final Map<String, Object> killbillApis, final String pluginMain) {
        container = setupScriptingContainer();

        checkValidPlugin();

        // Register all killbill APIs
        container.put(KILLBILL_SERVICES, killbillApis);
        container.put(KILLBILL_PLUGIN_CLASS_NAME, pluginMainClass);

        // Note that the KILLBILL_SERVICES variable will be available once only!
        // Don't put any code here!

        // Start the plugin
        pluginInstance = (RubyObject) container.runScriptlet(pluginMain + ".new(" + KILLBILL_PLUGIN_CLASS_NAME + "," + KILLBILL_SERVICES + ")");
    }

    public synchronized void startPlugin(final BundleContext context) {
        checkPluginIsStopped();
        pluginInstance.callMethod(START_PLUGIN_RUBY_METHOD_NAME);
        checkPluginIsRunning();
        registerHttpServlet();
    }

    public synchronized void stopPlugin(final BundleContext context) {
        checkPluginIsRunning();
        unregisterHttpServlet();
        pluginInstance.callMethod(STOP_PLUGIN_RUBY_METHOD_NAME);
        checkPluginIsStopped();
    }

    public void unInstantiatePlugin() {
        // Cleanup the container
        container.terminate();
    }

    private void registerHttpServlet() {
        // Register the rack handler
        final IRubyObject rackHandler = pluginInstance.callMethod(RACK_HANDLER_RUBY_METHOD_NAME);
        if (!rackHandler.isNil()) {
            logger.log(LogService.LOG_INFO, String.format("Using %s as rack handler", rackHandler.getMetaClass()));

            final JRubyHttpServlet jRubyHttpServlet = new JRubyHttpServlet(rackHandler);
            final Hashtable<String, String> properties = new Hashtable<String, String>();
            properties.put("killbill.pluginName", pluginGemName);
            httpServletServiceRegistration = bundleContext.registerService(HttpServlet.class.getName(), jRubyHttpServlet, properties);
        }
    }

    private void unregisterHttpServlet() {
        if (httpServletServiceRegistration != null) {
            httpServletServiceRegistration.unregister();
        }
    }

    private void checkPluginIsRunning() {
        if (pluginInstance == null || !(Boolean) pluginInstance.callMethod("is_active").toJava(Boolean.class)) {
            throw new IllegalStateException(String.format("Plugin %s didn't start properly", pluginMainClass));
        }
    }

    private void checkPluginIsStopped() {
        if (pluginInstance == null || (Boolean) pluginInstance.callMethod("is_active").toJava(Boolean.class)) {
            throw new IllegalStateException(String.format("Plugin %s didn't stop properly", pluginMainClass));
        }
    }

    private void checkValidPlugin() {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_BASE));
        } catch (final EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkValidNotificationPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_NOTIFICATION));
        } catch (final EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkValidPaymentPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_PAYMENT));
        } catch (final EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void checkValidCurrencyPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_CURRENCY));
        } catch (final EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String checkInstanceOfPlugin(final String baseClass) {
        final StringBuilder builder = new StringBuilder(getRequireLine());
        builder.append("raise ArgumentError.new('Invalid plugin: ")
               .append(pluginMainClass)
               .append(", is not a ")
               .append(baseClass)
               .append("') unless ")
               .append(pluginMainClass)
               .append(" <= ")
               .append(baseClass);
        return builder.toString();
    }

    private String getRequireLine() {
        if (cachedRequireLine == null) {
            final StringBuilder builder = new StringBuilder();
            builder.append("ENV[\"GEM_HOME\"] = \"").append(pluginLibdir).append("\"").append("\n");
            builder.append("ENV[\"GEM_PATH\"] = ENV[\"GEM_HOME\"]\n");
            // Always require the Killbill gem
            builder.append("gem 'killbill'\n");
            builder.append("require 'killbill'\n");
            // Assume the plugin is shipped as a Gem
            builder.append("begin\n")
                   .append("gem '").append(pluginGemName).append("'\n")
                   .append("rescue Gem::LoadError\n")
                   .append("warn \"WARN: unable to load gem ").append(pluginGemName).append("\"\n")
                   .append("end\n");
            builder.append("begin\n")
                   .append("require '").append(pluginGemName).append("'\n")
                    .append("rescue LoadError\n")
                            // Could be useful for debugging
                            //.append("warn \"WARN: unable to require ").append(pluginGemName).append("\"\n")
                    .append("end\n");
            // Load the extra require file, if specified
            if (rubyRequire != null) {
                builder.append("begin\n")
                       .append("require '").append(rubyRequire).append("'\n")
                       .append("rescue LoadError => e\n")
                       .append("warn \"WARN: unable to require ").append(rubyRequire).append(": \" + e.to_s\n")
                       .append("end\n");
            }
            // Require any file directly in the pluginLibdir directory (e.g. /var/tmp/bundles/ruby/foo/1.0/gems/*.rb).
            // Although it is likely that any Killbill plugin will be distributed as a gem, it is still useful to
            // be able to load individual scripts for prototyping/testing/...
            builder.append("Dir.glob(ENV[\"GEM_HOME\"] + \"/*.rb\").each {|x| require x rescue warn \"WARN: unable to load #{x}\"}\n");
            cachedRequireLine = builder.toString();
        }
        return cachedRequireLine;
    }

    private Ruby getRuntime() {
        return pluginInstance.getMetaClass().getRuntime();
    }

    private ScriptingContainer setupScriptingContainer() {
        // SINGLETHREAD model to avoid sharing state across scripting containers
        // All calls are synchronized anyways (don't trust gems to be thread safe)
        final ScriptingContainer scriptingContainer = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT, true);

        // Set the load paths instead of adding, to avoid looking at the filesystem
        scriptingContainer.setLoadPaths(Collections.<String>singletonList(pluginLibdir));

        return scriptingContainer;
    }

    public enum VALIDATION_PLUGIN_TYPE {
        NOTIFICATION,
        PAYMENT,
        CURRENCY,
        NONE
    }

    protected abstract class PluginCallback<T> {

        private final VALIDATION_PLUGIN_TYPE pluginType;

        public PluginCallback(final VALIDATION_PLUGIN_TYPE pluginType) {
            this.pluginType = pluginType;
        }

        public abstract T doCall(final Ruby runtime) throws PaymentPluginApiException;

        public VALIDATION_PLUGIN_TYPE getPluginType() {
            return pluginType;
        }
    }

    protected <T> T callWithRuntimeAndChecking(final PluginCallback<T> cb) throws PaymentPluginApiException {
        synchronized (pluginMonitor) {
            try {
                checkPluginIsRunning();

                switch (cb.getPluginType()) {
                    case NOTIFICATION:
                        checkValidNotificationPlugin();
                        break;
                    case PAYMENT:
                        checkValidPaymentPlugin();
                        break;
                    case CURRENCY:
                        checkValidCurrencyPlugin();
                        break;
                    default:
                        break;
                }

                final Ruby runtime = getRuntime();
                return cb.doCall(runtime);
            } catch (final RuntimeException e) {
                log.warn("RuntimeException in jruby plugin ", e);
                throw e;
            }
        }
    }
}
