/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.beatrix.glue;

import org.killbill.billing.beatrix.DefaultBeatrixService;
import org.killbill.billing.beatrix.bus.api.BeatrixService;
import org.killbill.billing.beatrix.extbus.BeatrixListener;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.KillBillModule;

public class BeatrixModule extends KillBillModule {

    public BeatrixModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        installExternalBus();
    }

    protected void installExternalBus() {
        bind(BeatrixService.class).to(DefaultBeatrixService.class);
        bind(DefaultBeatrixService.class).asEagerSingleton();

        bind(BeatrixListener.class).asEagerSingleton();
    }
}
