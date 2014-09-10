/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.api.DefaultAuditUserApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.audit.dao.DefaultAuditDao;

public class AuditModule extends KillBillModule {

    public AuditModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installDaos() {
        bind(AuditDao.class).to(DefaultAuditDao.class).asEagerSingleton();
    }

    protected void installUserApi() {
        bind(AuditUserApi.class).to(DefaultAuditUserApi.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installDaos();
        installUserApi();
    }
}
