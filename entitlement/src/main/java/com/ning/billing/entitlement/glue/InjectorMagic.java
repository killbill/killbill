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

package com.ning.billing.entitlement.glue;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.catalog.api.ICatalog;
import com.ning.billing.catalog.api.ICatalogService;
import com.ning.billing.entitlement.alignment.PlanAligner;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.util.clock.Clock;

//
// Allows to return Guice injected singleton in a non guice context
//
public class InjectorMagic {

    // public void testng only
    public static InjectorMagic instance;

    private final Injector injector;

    @Inject
    public InjectorMagic(Injector injector) {
        this.injector = injector;
        synchronized(InjectorMagic.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }


    public static Clock getClock() {
        return InjectorMagic.get().getInstance(Clock.class);
    }

    public static ICatalog getCatlog() {
        ICatalogService catalogService = InjectorMagic.get().getInstance(ICatalogService.class);
        return catalogService.getCatalog();
    }

    public static EntitlementDao getEntitlementDao() {
        return InjectorMagic.get().getInstance(EntitlementDao.class);
    }

    public static PlanAligner getPlanAligner() {
        return InjectorMagic.get().getInstance(PlanAligner.class);
    }


    public static Injector get() {
        if (instance == null) {
            throw new RuntimeException("Trying to retrieve injector too early");
        }
        return instance.getInjector();
    }

    private Injector getInjector() {
        return injector;
    }
}
