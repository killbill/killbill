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

package com.ning.billing.invoice.notification;

import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.eventbus.Bus;
import com.ning.billing.util.eventbus.MemoryEventBus;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService;

public class TestNextBillingDateNotifier {

	private Clock clock;
	private NextBillingDateNotifier notifier;
	
	@BeforeClass(groups={"setup"})
	public void setup() throws ServiceException {
		//TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
			protected void configure() {
				 bind(Clock.class).to(DefaultClock.class).asEagerSingleton();
				 bind(NextBillingDateNotifier.class).to(DefaultNextBillingDateNotifier.class).asEagerSingleton();
				 bind(Bus.class).to(MemoryEventBus.class).asEagerSingleton();
				 bind(NotificationQueueService.class).to(DefaultNotificationQueueService.class).asEagerSingleton();
				 final InvoiceConfig config = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
			        bind(InvoiceConfig.class).toInstance(config);
			}  	
        });

        notifier = g.getInstance(NextBillingDateNotifier.class);
        clock = g.getInstance(Clock.class);
       
	}
	
	@Test(enabled=false, groups="fast")
	public void test() {
		
	}
}
