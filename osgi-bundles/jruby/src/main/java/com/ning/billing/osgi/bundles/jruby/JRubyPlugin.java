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

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.http.HttpServlet;

import org.jruby.Ruby;
import org.jruby.RubyObject;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.ScriptingContainer;
import org.jruby.runtime.builtin.IRubyObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

import com.ning.billing.osgi.api.config.PluginRubyConfig;

// Bridge between the OSGI bundle and the ruby plugin
public abstract class JRubyPlugin {

    // Killbill gem base classes
    private static final String KILLBILL_PLUGIN_BASE = "Killbill::Plugin::PluginBase";
    private static final String KILLBILL_PLUGIN_NOTIFICATION = "Killbill::Plugin::Notification";
    private static final String KILLBILL_PLUGIN_PAYMENT = "Killbill::Plugin::Payment";

    // Magic ruby variables
    private static final String KILLBILL_SERVICES = "java_apis";
    private static final String ACTIVE = "@active";

    protected final Logger logger;
    protected final BundleContext bundleContext;
    protected final String pluginGemName;
    protected final String rubyRequire;
    protected final String pluginMainClass;
    protected final ScriptingContainer container;
    protected final String pluginLibdir;

    protected RubyObject pluginInstance;

    private ServiceRegistration httpServletServiceRegistration = null;
    private String cachedRequireLine = null;

    public JRubyPlugin(final PluginRubyConfig config, final ScriptingContainer container,
                       final BundleContext bundleContext, final Logger logger) {
        this.logger = logger;
        this.bundleContext = bundleContext;
        this.pluginGemName = config.getPluginName();
        this.rubyRequire = config.getRubyRequire();
        this.pluginMainClass = config.getRubyMainClass();
        this.container = container;
        this.pluginLibdir = config.getRubyLoadDir();

        // Path to the gem
        if (pluginLibdir != null) {
            container.setLoadPaths(Arrays.asList(pluginLibdir));
        }
    }

    public String getPluginMainClass() {
        return pluginMainClass;
    }

    public String getPluginLibdir() {
        return pluginLibdir;
    }

    public void instantiatePlugin(final Map<String, Object> killbillApis) {
        checkValidPlugin();

        // Register all killbill APIs
        container.put(KILLBILL_SERVICES, killbillApis);

        // Note that the KILLBILL_SERVICES variable will be available once only!
        // Don't put any code here!

        // Start the plugin
        pluginInstance = (RubyObject) container.runScriptlet(pluginMainClass + ".new(" + KILLBILL_SERVICES + ")");
    }

    public void startPlugin(final BundleContext context) {
        checkPluginIsStopped();
        pluginInstance.callMethod("start_plugin");
        checkPluginIsRunning();
        registerHttpServlet();
    }

    public void stopPlugin(final BundleContext context) {
        checkPluginIsRunning();
        unregisterHttpServlet();
        pluginInstance.callMethod("stop_plugin");
        checkPluginIsStopped();
    }

    private void registerHttpServlet() {
        // Register the rack handler
        final IRubyObject rackHandler = pluginInstance.callMethod("rack_handler");
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

    protected void checkPluginIsRunning() {
        if (pluginInstance == null || !pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't start properly", pluginMainClass));
        }
    }

    protected void checkPluginIsStopped() {
        if (pluginInstance == null || pluginInstance.getInstanceVariable(ACTIVE).isTrue()) {
            throw new IllegalStateException(String.format("Plugin %s didn't stop properly", pluginMainClass));
        }
    }

    protected void checkValidPlugin() {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_BASE));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void checkValidNotificationPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_NOTIFICATION));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void checkValidPaymentPlugin() throws IllegalArgumentException {
        try {
            container.runScriptlet(checkInstanceOfPlugin(KILLBILL_PLUGIN_PAYMENT));
        } catch (EvalFailedException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected String checkInstanceOfPlugin(final String baseClass) {
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
                   .append("warn \"WARN: unable to require ").append(pluginGemName).append("\"\n")
                   .append("end\n");
            // Load the extra require file, if specified
            if (rubyRequire != null) {
                builder.append("begin\n")
                       .append("require '").append(rubyRequire).append("'\n")
                       .append("rescue LoadError\n")
                       .append("warn \"WARN: unable to require ").append(rubyRequire).append("\"\n")
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

    protected Ruby getRuntime() {
        return pluginInstance.getMetaClass().getRuntime();
    }
}
