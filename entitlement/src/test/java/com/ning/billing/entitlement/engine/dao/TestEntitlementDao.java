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

package com.ning.billing.entitlement.engine.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.TestUserApiBase;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.glue.EngineModuleMemoryMock;

public class TestEntitlementDao extends TestUserApiBase {


    private void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignore) {
        }
    }
    @Test(enabled=false)
    public void testEventDao() {

        UUID me = UUID.randomUUID();

        String dbiString = "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true";
        DBI dbi =  new DBI(dbiString, "root", "root");
        IEventSqlDao dao = dbi.onDemand(IEventSqlDao.class);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = "standard";

        DateTime now = new DateTime();
        IPlan plan = catalog.getPlan(productName, term, planSetName);
        IEvent event = new ApiEventCreate(UUID.randomUUID(), now, now, plan,planSetName, now, now, 1);
        dao.insertEvent(event);

        sleep();

        List<IEvent> events =  dao.getReadyEvents(new DateTime().toDate(), 1);
        //for (IEvent cur : events) {

        //}
// @Bind("owner") String owner, @Bind("next_available") Date nextAvailable, @Bind("id") String eventId, @Bind("now") Date now);
        dao.claimEvent(me.toString(), now.plusDays(1).toDate(), events.get(0).getId().toString(), now.toDate());

        dao.clearEvent(events.get(0).getId().toString(), me.toString());

        System.out.println("youpi");
    }

    @Test(enabled=false)
    public void testSubscriptionDao() {
        String dbiString = "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true";
        DBI dbi =  new DBI(dbiString, "root", "root");
        ISubscriptionSqlDao dao = dbi.onDemand(ISubscriptionSqlDao.class);

        DateTime now = new DateTime();
        Subscription sub = new Subscription(UUID.randomUUID(), UUID.randomUUID(), ProductCategory.BASE, now, now, now, now, 1);
        dao.insertSubscription(sub);

    }

    @Test(enabled=true)
    public void testMixin() {

        String dbiString = "jdbc:mysql://127.0.0.1:3306/killbill?createDatabaseIfNotExist=true";
        DBI dbi =  new DBI(dbiString, "root", "root");
        ISubscriptionSqlDao dao = dbi.onDemand(ISubscriptionSqlDao.class);

        DateTime now = new DateTime();
        final Subscription sub = new Subscription(UUID.randomUUID(), UUID.randomUUID(), ProductCategory.BASE, now, now, now, now, 1);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = "standard";

        IPlan plan = catalog.getPlan(productName, term, planSetName);
        final IEvent event = new ApiEventCreate(UUID.randomUUID(), now, now, plan, planSetName, now, now, 1);

        dao.inTransaction(new Transaction<Void, ISubscriptionSqlDao>() {

            @Override
            public Void inTransaction(ISubscriptionSqlDao subscriptionDao,
                    TransactionStatus status) throws Exception {

                subscriptionDao.insertSubscription(sub);
                IEventSqlDao eventDao = subscriptionDao.become(IEventSqlDao.class);
                eventDao.insertEvent(event);
                return null;
            }
        });


    }
    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new EngineModuleMemoryMock());
    }

}
