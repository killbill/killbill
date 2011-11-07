package com.ning.billing.beatrix.lifecycle;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.beatrix.lifecycle.LyfecycleHandlerType.LyfecycleLevel;
import com.ning.billing.entitlement.IEntitlementService;


public class TestLifecycle {

    private final static Logger log = LoggerFactory.getLogger(TestLifecycle.class);

    @Lifecycled
    public static class Service1 implements LifecycleService {

        @LyfecycleHandlerType(LyfecycleLevel.INIT_BUS)
        public void initBus() {
            log.info("Service1 : got INIT_BUS");
        }

        @LyfecycleHandlerType(LyfecycleLevel.START_SERVICE)
        public void startService() {
            log.info("Service1 : got START_SERVICE");
        }
    }

    @Lifecycled
    public static class Service2 implements LifecycleService {

        @LyfecycleHandlerType(LyfecycleLevel.LOAD_CATALOG)
        public void initBus() {
            log.info("Service1 : got INIT_BUS");
        }
    }


    private Service1 s1;
    private Service2 s2;

    private Lifecycle lifecycle;

    @BeforeClass(groups={"fast"})
    public void setup() {
        final Injector g = Guice.createInjector(Stage.DEVELOPMENT, new TestLifecycleModule());
        s1 = g.getInstance(Service1.class);
        s2 = g.getInstance(Service2.class);
        lifecycle = g.getInstance(Lifecycle.class);
        lifecycle.init();
    }

    @Test
    public void testLifecycle() {

        lifecycle.fireStages();
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

