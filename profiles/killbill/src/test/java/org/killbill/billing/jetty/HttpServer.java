/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.jetty;

import java.lang.management.ManagementFactory;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.servlet.DispatcherType;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.killbill.commons.skeleton.listeners.JULServletContextListener;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.inject.servlet.GuiceFilter;

/**
 * Embed Jetty
 */
public class HttpServer {

    private final Server server;

    public HttpServer() {
        this.server = new Server();
        server.setSendServerVersion(false);
    }

    public HttpServer(final String jettyXml) throws Exception {
        this();
        configure(jettyXml);
    }

    public void configure(final String jettyXml) throws Exception {
        final XmlConfiguration configuration = new XmlConfiguration(Resources.getResource(jettyXml));
        configuration.configure(server);
    }

    public void configure(final HttpServerConfig config, final Iterable<EventListener> eventListeners, final Map<FilterHolder, String> filterHolders) {
        server.setStopAtShutdown(true);

        // Setup JMX
        configureJMX(ManagementFactory.getPlatformMBeanServer());

        // Configure main connector
        configureMainConnector(config.isJettyStatsOn(), config.getServerHost(), config.getServerPort());

        // Configure SSL, if enabled
        if (config.isSSLEnabled()) {
            configureSslConnector(config.isJettyStatsOn(), config.getServerSslPort(), config.getSSLkeystoreLocation(), config.getSSLkeystorePassword());
        }

        // Configure the thread pool
        configureThreadPool(config);

        // Configure handlers
        final HandlerCollection handlers = new HandlerCollection();
        final ServletContextHandler servletContextHandler = createServletContextHandler(config.getResourceBase(), eventListeners, filterHolders);
        handlers.addHandler(servletContextHandler);
        final RequestLogHandler logHandler = createLogHandler(config);
        handlers.addHandler(logHandler);
        final HandlerList rootHandlers = new HandlerList();
        rootHandlers.addHandler(handlers);
        server.setHandler(rootHandlers);
    }

    @PostConstruct
    public void start() throws Exception {
        server.start();
        Preconditions.checkState(server.isRunning(), "server is not running");
    }

    @PreDestroy
    public void stop() throws Exception {
        server.stop();
    }

    private void configureJMX(final MBeanServer mbeanServer) {
        final MBeanContainer mbContainer = new MBeanContainer(mbeanServer);
        mbContainer.addBean(Log.getLogger(HttpServer.class));
        server.addBean(mbContainer);
    }

    private void configureMainConnector(final boolean isStatsOn, final String localIp, final int localPort) {
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setName("http");
        connector.setStatsOn(isStatsOn);
        connector.setHost(localIp);
        connector.setPort(localPort);
        server.addConnector(connector);
    }

    private void configureSslConnector(final boolean isStatsOn, final int localSslPort, final String sslKeyStorePath, final String sslKeyStorePassword) {
        final SslSelectChannelConnector sslConnector = new SslSelectChannelConnector();
        sslConnector.setName("https");
        sslConnector.setStatsOn(isStatsOn);
        sslConnector.setPort(localSslPort);
        final SslContextFactory sslContextFactory = sslConnector.getSslContextFactory();
        sslContextFactory.setKeyStorePath(sslKeyStorePath);
        sslContextFactory.setKeyStorePassword(sslKeyStorePassword);
        server.addConnector(sslConnector);
    }

    private void configureThreadPool(final HttpServerConfig config) {
        final QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads());
        threadPool.setMinThreads(config.getMinThreads());
        threadPool.setName("http-worker");
        server.setThreadPool(threadPool);
    }

    private ServletContextHandler createServletContextHandler(final String resourceBase, final Iterable<EventListener> eventListeners, final Map<FilterHolder, String> filterHolders) {
        final ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        if (resourceBase != null) {
            // Required if you want a webapp directory. See ContextHandler#getResource and http://docs.codehaus.org/display/JETTY/Embedding+Jetty
            final String webapp = this.getClass().getClassLoader().getResource(resourceBase).toExternalForm();
            context.setResourceBase(webapp);
        }

        // Jersey insists on using java.util.logging (JUL)
        final EventListener listener = new JULServletContextListener();
        context.addEventListener(listener);

        for (final EventListener eventListener : eventListeners) {
            context.addEventListener(eventListener);
        }

        for (final FilterHolder filterHolder : filterHolders.keySet()) {
            context.addFilter(filterHolder, filterHolders.get(filterHolder), EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
        }

        // Make sure Guice filter all requests
        final FilterHolder filterHolder = new FilterHolder(GuiceFilter.class);
        context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));

        // Backend servlet for Guice - never used
        final ServletHolder sh = new ServletHolder(DefaultServlet.class);
        context.addServlet(sh, "/*");

        return context;
    }

    private RequestLogHandler createLogHandler(final HttpServerConfig config) {
        final RequestLogHandler logHandler = new RequestLogHandler();

        final RequestLog requestLog = new NCSARequestLog(config.getLogPath());
        logHandler.setRequestLog(requestLog);

        return logHandler;
    }
}
