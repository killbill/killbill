/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.util.discovery;


import com.google.inject.Inject;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;

import org.killbill.billing.platform.api.KillbillService;
import org.killbill.billing.platform.api.LifecycleHandlerType;
import org.killbill.billing.platform.api.LifecycleHandlerType.LifecycleLevel;
import org.killbill.billing.platform.config.DefaultKillbillConfigSource;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultEurekaKillbillService implements EurekaKillbillService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEurekaKillbillService.class);

    private ApplicationInfoManager applicationInfoManager;

    public static final String EUREKA_SERVICE_NAME = "eureka-service";

    @Inject
    public DefaultEurekaKillbillService(ApplicationInfoManager applicationInfoManager) {
        this.applicationInfoManager = applicationInfoManager;
    }

    @Override
    public String getName() {
        return EUREKA_SERVICE_NAME;
    }

    @LifecycleHandlerType(LifecycleLevel.INIT_SERVICE)
    public synchronized void initialize() {
        logger.info("--------------> Setting Eureka Status to UP <----------------");
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        logger.info("--------------> Finished setting Eureka Status to UP <----------------");
    }

    @LifecycleHandlerType(LifecycleLevel.STOP_SERVICE)
    public synchronized void shutdown() {
        logger.info("--------------> Setting Eureka Status to DOWN <----------------");
        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
    }
}
