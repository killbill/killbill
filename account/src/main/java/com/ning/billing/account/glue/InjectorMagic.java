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

package com.ning.billing.account.glue;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.account.dao.IAccountDao;
import com.ning.billing.account.dao.IFieldStoreDao;
import com.ning.billing.account.dao.ITagStoreDao;

public class InjectorMagic {
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

    public static Injector get() {
        if (instance == null) {
            throw new RuntimeException("Trying to retrieve injector too early");
        }
        return instance.getInjector();
    }

    private Injector getInjector() {
        return injector;
    }

    public static IFieldStoreDao getFieldStoreDao() {
        return InjectorMagic.get().getInstance(IFieldStoreDao.class);
    }

    public static IAccountDao getAccountDao() {
        return InjectorMagic.get().getInstance(IAccountDao.class);
    }

    public static ITagStoreDao getTagStoreDao() {
        return InjectorMagic.get().getInstance(ITagStoreDao.class);
    }

//    public static IEventBus getEventBus() {
//        return InjectorMagic.get().getInstance(IEventBus.class);
//    }
}
