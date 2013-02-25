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

package com.ning.billing.util.bus;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

import com.ning.billing.util.config.PersistentQueueConfig;

public interface PersistentBusConfig extends PersistentQueueConfig {

    @Override
    @Config("killbill.billing.util.persistent.bus.sleep")
    @Default("3000")
    @Description("Time in milliseconds to sleep between runs")
    public long getSleepTimeMs();

    @Override
    @Config("killbill.billing.util.persistent.bus.off")
    @Default("false")
    @Description("Whether to turn off the persistent bus")
    public boolean isProcessingOff();

    @Config("killbill.billing.util.persistent.bus.nbThreads")
    @Default("3")
    @Description("Number of threads to use")
    public int getNbThreads();
}
