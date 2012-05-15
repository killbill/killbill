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

package com.ning.billing.overdue.service;

import java.net.URI;

import com.google.inject.Inject;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.util.config.XMLLoader;

public class DefaultOverdueService implements ExtendedOverdueService {
    public static final String OVERDUE_SERVICE_NAME = "overdue-service";
    private OverdueUserApi userApi;
    private OverdueConfig overdueConfig;
    private OverdueProperties properties;

    private boolean isInitialized;

    @Inject
    public DefaultOverdueService(OverdueUserApi userApi, OverdueProperties properties){
        this.userApi = userApi;
        this.properties = properties;
    }
    
    @Override
    public String getName() {
        return OVERDUE_SERVICE_NAME;
    }

    @Override
    public OverdueUserApi getUserApi() {
        return userApi;
    }

    @Override
   public OverdueConfig getOverdueConfig() {
        return overdueConfig;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public synchronized void loadConfig() throws ServiceException {
        if (!isInitialized) {
            try {
                System.out.println("Overdue config URI" + properties.getConfigURI());
                URI u = new URI(properties.getConfigURI());
                if(u != null) {
                    overdueConfig = XMLLoader.getObjectFromUri(u, OverdueConfig.class);
                }

                isInitialized = true;
            } catch (Exception e) {
                throw new ServiceException(e);
            }
        }
    }

}
