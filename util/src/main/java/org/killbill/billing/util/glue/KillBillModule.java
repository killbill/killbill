/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import org.killbill.billing.util.features.KillbillFeatures;
import org.skife.config.ConfigSource;

import com.google.inject.AbstractModule;

public abstract class KillBillModule extends AbstractModule {

    public static final String STATIC_CONFIG = "StaticConfig";

    protected final KillbillConfigSource configSource;
    protected final ConfigSource skifeConfigSource;
    protected final KillbillFeatures killbillFeatures;

    public KillBillModule(final KillbillConfigSource configSource) {
        this(configSource, new KillbillFeatures());
    }

    public KillBillModule(final KillbillConfigSource configSource, final KillbillFeatures killbillFeatures) {
        this.configSource = configSource;
        this.skifeConfigSource = new KillbillSkifeConfigSource(configSource);
        this.killbillFeatures = killbillFeatures;
    }

    private static final class KillbillSkifeConfigSource implements ConfigSource {

        private final KillbillConfigSource configSource;

        private KillbillSkifeConfigSource(final KillbillConfigSource configSource) {
            this.configSource = configSource;
        }

        @Override
        public String getString(final String propertyName) {
            return configSource.getString(propertyName);
        }
    }
}
