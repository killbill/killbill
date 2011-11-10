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

package com.ning.billing.beatrix.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.lifecycle.IService;
import com.ning.billing.lifecycle.Lifecycled;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.lifecycle.LyfecycleHandlerType.LyfecycleLevel;


public class TestLifecycle {

    private final static Logger log = LoggerFactory.getLogger(TestLifecycle.class);

    private Service1 s1;
    private Service2 s2;

    private Lifecycle lifecycle;

    public static class Service1 implements IService {

        @LyfecycleHandlerType(LyfecycleLevel.INIT_BUS)
        public void initBus() {
            log.info("Service1 : got INIT_BUS");
        }

        @LyfecycleHandlerType(LyfecycleLevel.START_SERVICE)
        public void startService() {
            log.info("Service1 : got START_SERVICE");
        }

        @Override
        public String getName() {
            return null;
        }
    }

    @Lifecycled
    public static class Service2 implements IService {

        @LyfecycleHandlerType(LyfecycleLevel.LOAD_CATALOG)
        public void loadCatalog() {
            log.info("Service1 : got LOAD_CATALOG");
        }

        @Override
        public String getName() {
            return null;
        }

    }



    @BeforeClass(groups={"fast"})
    public void setup() {
        final Injector g = Guice.createInjector(Stage.DEVELOPMENT, new TestLifecycleModule());
        s1 = g.getInstance(Service1.class);
        s2 = g.getInstance(Service2.class);
        lifecycle = g.getInstance(Lifecycle.class);
    }

    @Test
    public void testLifecycle() {
        lifecycle.fireStartupSequence();
    }


    public static class TestLifecycleModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(Lifecycle.class).asEagerSingleton();
            bind(Service1.class).asEagerSingleton();
            bind(Service2.class).asEagerSingleton();
        }

    }
}

