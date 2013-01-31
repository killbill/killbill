/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.util.config;

import org.skife.config.Config;
import org.skife.config.Default;

public interface OSGIConfig extends KillbillConfig {

    @Config("killbill.osgi.root.dir")
    @Default("/var/tmp/felix")
    public String getOSGIBundleRootDir();

    @Config("killbill.osgi.bundle.cache.name")
    @Default("osgi-cache")
    public String getOSGIBundleCacheName();


    @Config("killbill.osgi.bundle.install.dir")
    @Default("/var/tmp/bundles")
    public String getRootInstallationDir();

    @Config("killbill.osgi.system.bundle.export.packages")
    @Default("com.ning.billing.account.api,com.ning.billing.beatrix.bus.api,com.ning.billing.payment.plugin.api,com.ning.billing.osgi.api.config,com.ning.billing.util.callcontext,com.google.common.eventbus")
    public String getSystemBundleExportPackages();

    // TODO FIXME OSGI
    @Config("killbill.osgi.jruby.bundle.path")
    @Default("file:/var/tmp/killbill-osgi-jruby-bundle-0.1.51-SNAPSHOT-jar-with-dependencies.jar")
    public String getJrubyBundlePath();

}
