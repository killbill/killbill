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
    @Default("com.ning.billing.account.api," +
             "com.ning.billing.analytics.api.sanity," +
             "com.ning.billing.analytics.api.user," +
             "com.ning.billing.beatrix.bus.api," +
             "com.ning.billing.catalog.api," +
             "com.ning.billing.entitlement.api.migration," +
             "com.ning.billing.entitlement.api.timeline," +
             "com.ning.billing.entitlement.api.transfer," +
             "com.ning.billing.entitlement.api.user," +
             "com.ning.billing.invoice.api," +
             "com.ning.billing.osgi.api," +
             "com.ning.billing.osgi.api.config," +
             "com.ning.billing.overdue," +
             "com.ning.billing.payment.api," +
             "com.ning.billing.tenant.api," +
             "com.ning.billing.usage.api," +
             "com.ning.billing.util.api," +
             "com.ning.billing.util.callcontext," +
             "com.google.common.eventbus," +
             // javax.servlet and javax.servlet.http are not exported by default - we
             // need the bundles to see them for them to be able to register their servlets.
             // Note: bundles should mark javax.servlet:servlet-api as provided
             "javax.servlet;version=3.0," +
             "javax.servlet.http;version=3.0," +
             "org.osgi.service.log;version=1.3," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (osgi.wiring.package=org.osgi.service.http)
             "org.osgi.service.http," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (&(osgi.wiring.package=org.osgi.service.deploymentadmin)(version>=1.1.0)(!(version>=2.0.0)))
             "org.osgi.service.deploymentadmin;version=1.1.0," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (&(osgi.wiring.package=org.osgi.service.event)(version>=1.2.0)(!(version>=2.0.0)))
             "org.osgi.service.event;version=1.2.0," +
             // Let the world know the System bundle exposes the requirement (&(osgi.wiring.package=org.slf4j)(version>=1.7.0)(!(version>=2.0.0)))
             "org.slf4j;version=1.7.2")
    public String getSystemBundleExportPackages();

    // TODO FIXME OSGI
    @Config("killbill.osgi.jruby.bundle.path")
    @Default("file:/var/tmp/killbill-osgi-bundles-jruby.jar")
    public String getJrubyBundlePath();

}
