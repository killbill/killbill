/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.server.listeners;


import javax.servlet.ServletContextEvent;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Injector;
import com.ning.jetty.core.listeners.SetupServer;

public class KillbillGuiceListener extends SetupServer {


    private static Injector injectorInstance;

    public static final Logger logger = LoggerFactory.getLogger(KillbillGuiceListener.class);


    @Override
    public void contextInitialized(final ServletContextEvent event) {

        logger.info("GuiceListener : contextInitialized");

        final String moduleFactoryClassName = event.getServletContext().getInitParameter("guiceModuleFactoryClass");

        if (StringUtils.isEmpty(moduleFactoryClassName)) {
            throw new IllegalStateException("Missing parameter 'guiceModuleFactoryClass' for IrsGuiceListener!");
        }
        try {
            final Class<?> moduleFactoryClass = Class.forName(moduleFactoryClassName);
            if (!GuiceModuleFactory.class.isAssignableFrom(moduleFactoryClass)) {
                throw new IllegalStateException(String.format("%s exists but is not a guice module factory!", moduleFactoryClassName));
            }

            GuiceModuleFactory factory = GuiceModuleFactory.class.cast(moduleFactoryClass.newInstance());
            logger.info("Instantiated " + moduleFactoryClassName + " as the factory for the main guice module.");

            guiceModule = factory.createModule();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        super.contextInitialized(event);

        injectorInstance = this.injector(event);

    }

    public static Injector getInjectorInstance() {
        return injectorInstance;
    }
}
