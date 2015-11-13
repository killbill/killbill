/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.glue;

import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.info.DefaultKillbillInfoApi;
import org.killbill.billing.util.info.DefaultKillbillInfoService;
import org.killbill.billing.util.info.KillbillInfoApi;
import org.killbill.billing.util.info.KillbillInfoService;
import org.killbill.billing.util.info.NodeInfoMapper;
import org.killbill.billing.util.info.dao.DefaultNodeInfoDao;
import org.killbill.billing.util.info.dao.NodeInfoDao;

public class InfoModule extends KillBillModule {

    public InfoModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installDaos() {
        bind(NodeInfoDao.class).to(DefaultNodeInfoDao.class).asEagerSingleton();
    }

    protected void installUserApi() {
        bind(KillbillInfoApi.class).to(DefaultKillbillInfoApi.class).asEagerSingleton();
        bind(KillbillInfoService.class).to(DefaultKillbillInfoService.class).asEagerSingleton();
        bind(NodeInfoMapper.class).asEagerSingleton();
    }


    @Override
    protected void configure() {
        installDaos();
        installUserApi();
    }
}
