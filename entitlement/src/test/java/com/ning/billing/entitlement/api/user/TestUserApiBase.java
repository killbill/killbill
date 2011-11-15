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

package com.ning.billing.entitlement.api.user;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

import com.google.inject.Injector;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.CatalogService;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.catalog.api.IDuration;
import com.ning.billing.catalog.api.TimeUnit;
import com.ning.billing.config.IEntitlementConfig;
import com.ning.billing.entitlement.api.ApiTestListener;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.api.IEntitlementService;
import com.ning.billing.entitlement.api.billing.IEntitlementBillingApi;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.dao.IEntitlementDao;
import com.ning.billing.entitlement.engine.dao.IEntitlementDaoMock;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.phase.IPhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.IApiEvent;
import com.ning.billing.entitlement.glue.InjectorMagic;
import com.ning.billing.lifecycle.IService.ServiceException;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.clock.IClock;
import com.ning.billing.util.eventbus.EventBusService;
import com.ning.billing.util.eventbus.IEventBusService;

public abstract class TestUserApiBase {

    protected static final Logger log = LoggerFactory.getLogger(TestUserApiBase.class);

    protected static final long DAY_IN_MS = (24 * 3600 * 1000);

    protected IEntitlementService entitlementService;
    protected IEntitlementUserApi entitlementApi;
    protected IEntitlementBillingApi billingApi;
    protected ICatalogService catalogService;
    protected IEntitlementConfig config;
    protected IEntitlementDao dao;
    protected ClockMock clock;
    protected IEventBusService busService;

    protected IAccount account;
    protected ICatalog catalog;
    protected ApiTestListener testListener;
    protected ISubscriptionBundle bundle;

    public static void loadSystemPropertiesFromClasspath( final String resource )
    {
        final URL url = TestUserApiBase.class.getResource(resource);
        assertNotNull(url);

        try {
            System.getProperties().load( url.openStream() );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass(groups={"setup"})
    public void tearDown() {
        try {
            InjectorMagic.instance = null;
            busService.getEventBus().register(testListener);
            ((EventBusService) busService).stopBus();
        } catch (Exception e) {
            log.warn("Failed to tearDown test properly ", e);
        }

    }

    @BeforeClass(groups={"setup"})
    public void setup() {

        loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = getInjector();

        entitlementService = g.getInstance(IEntitlementService.class);
        catalogService = g.getInstance(ICatalogService.class);
        busService = g.getInstance(IEventBusService.class);
        config = g.getInstance(IEntitlementConfig.class);
        dao = g.getInstance(IEntitlementDao.class);
        clock = (ClockMock) g.getInstance(IClock.class);
        try {

            ((CatalogService) catalogService).loadCatalog();
            ((EventBusService) busService).startBus();
            ((Engine) entitlementService).initialize();
            init();
        } catch (EntitlementUserApiException e) {
            Assert.fail(e.getMessage());
        } catch (ServiceException e) {
            Assert.fail(e.getMessage());
        }
    }

    protected abstract Injector getInjector();

    private void init() throws EntitlementUserApiException {
        account = getAccount();
        assertNotNull(account);

        catalog = catalogService.getCatalog();
        assertNotNull(catalog);


        testListener = new ApiTestListener(busService.getEventBus());
        entitlementApi = entitlementService.getUserApi();
        billingApi = entitlementService.getBillingApi();

    }

    @BeforeMethod(groups={"setup"})
    public void setupTest() {

        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");

        testListener.reset();

        clock.resetDeltaFromReality();
        ((IEntitlementDaoMock) dao).reset();
        try {
            busService.getEventBus().register(testListener);
            bundle = entitlementApi.createBundleForAccount(account, "myDefaultBundle");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        assertNotNull(bundle);

        ((Engine)entitlementService).start();
    }

    @AfterMethod(groups={"setup"})
    public void cleanupTest() {


        ((Engine)entitlementService).stop();
        log.warn("DONE WITH TEST\n");
    }

    // Glue magic to invoke the real test
    protected void invokeRealMethod(Object invoker)  {

        try {
            String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
            String realMethodName= methodName + "Real";

            Class<?> thisClass = invoker.getClass();
            Class<?> superClass = thisClass.getSuperclass();
            Method [] methods = superClass.getDeclaredMethods();
            for (Method cur : methods) {
                if (cur.getName().equals(realMethodName)) {
                    cur.invoke(invoker);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

    protected Subscription createSubscription(String productName, BillingPeriod term, String planSet) throws EntitlementUserApiException {
        testListener.pushExpectedEvent(NextEvent.CREATE);
        Subscription subscription = (Subscription) entitlementApi.createSubscription(bundle.getId(), productName, term, planSet, clock.getUTCNow());
        assertNotNull(subscription);
        assertTrue(testListener.isCompleted(5000));
        return subscription;
    }

    protected void checkNextPhaseChange(Subscription subscription, int expPendingEvents, DateTime expPhaseChange) {

        List<IEvent> events = dao.getPendingEventsForSubscription(subscription.getId());
        assertNotNull(events);
        printEvents(events);
        assertEquals(events.size(), expPendingEvents);
        if (events.size() > 0 && expPhaseChange != null) {
            boolean foundPhase = false;
            boolean foundChange = false;

            for (IEvent cur : events) {
                if (cur instanceof IPhaseEvent) {
                    assertEquals(foundPhase, false);
                    foundPhase = true;
                    assertEquals(cur.getEffectiveDate(), expPhaseChange);
                } else if (cur instanceof IApiEvent) {
                    IApiEvent uEvent = (IApiEvent) cur;
                    assertEquals(ApiEventType.CHANGE, uEvent.getEventType());
                    assertEquals(foundChange, false);
                    foundChange = true;
                } else {
                    assertFalse(true);
                }
            }
        }
    }


    protected void assertDateWithin(DateTime in, DateTime lower, DateTime upper) {
        assertTrue(in.isEqual(lower) || in.isAfter(lower));
        assertTrue(in.isEqual(upper) || in.isBefore(upper));
    }

    protected IDuration getDurationDay(final int days) {
        IDuration result = new IDuration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.DAYS;
            }
            @Override
            public int getLength() {
                return days;
            }
        };
        return result;
    }

    protected IDuration getDurationMonth(final int months) {
        IDuration result = new IDuration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.MONTHS;
            }
            @Override
            public int getLength() {
                return months;
            }
        };
        return result;
    }


    protected IDuration getDurationYear(final int years) {
        IDuration result = new IDuration() {
            @Override
            public TimeUnit getUnit() {
                return TimeUnit.YEARS;
            }
            @Override
            public int getLength() {
                return years;
            }
        };
        return result;
    }

    protected IAccount getAccount() {
        IAccount account = new IAccount() {
            @Override
            public String getName() {
                return "accountName";
            }
            @Override
            public String getEmail() {
                return "accountName@yahoo.com";
            }
            @Override
            public String getPhone() {
                return "4152876341";
            }
            @Override
            public String getKey() {
                return "k123456";
            }
            @Override
            public int getBillCycleDay() {
                return 1;
            }
            @Override
            public Currency getCurrency() {
                return Currency.USD;
            }
            @Override
            public void setPrivate(String name, String value) {
            }
            @Override
            public String getPrivate(String name) {
                return null;
            }
            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }
        };
        return account;
    }


    protected void printEvents(List<IEvent> events) {
        for (IEvent cur : events) {
            log.debug("Inspect event " + cur);
        }
    }

    protected void printSubscriptionTransitions(List<ISubscriptionTransition> transitions) {
        for (ISubscriptionTransition cur : transitions) {
            log.debug("Transition " + cur);
        }
    }

}
