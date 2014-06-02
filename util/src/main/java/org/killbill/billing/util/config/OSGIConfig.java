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

package org.killbill.billing.util.config;

import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.Description;

public interface OSGIConfig extends KillbillConfig {

    @Config("org.killbill.osgi.bundle.property.name")
    @Default("killbill.properties")
    @Description("Name of the properties file for OSGI plugins")
    public String getOSGIKillbillPropertyName();

    @Config("org.killbill.osgi.root.dir")
    @Default("/var/tmp/felix")
    @Description("Bundles cache area for the OSGI framework")
    public String getOSGIBundleRootDir();

    @Config("org.killbill.osgi.bundle.cache.name")
    @Default("osgi-cache")
    @Description("Bundles cache name")
    public String getOSGIBundleCacheName();

    @Config("org.killbill.osgi.bundle.install.dir")
    @Default("/var/tmp/bundles")
    @Description("Bundles install directory")
    public String getRootInstallationDir();


    @Config("org.killbill.osgi.system.bundle.export.packages")
    @Default("org.killbill.billing.account.api," +
             "org.killbill.billing.analytics.api.sanity," +
             "org.killbill.billing.analytics.api.user," +
             "org.killbill.billing.beatrix.bus.api," + /* TODO PIERRE Remove it after plugins classes have been regenerated */
             "org.killbill.billing.catalog.api," +
             "org.killbill.billing.invoice.api," +
             "org.killbill.billing.entitlement.api," +
             "org.killbill.billing," +
             "org.killbill.billing.notification.api," +
             "org.killbill.billing.notification.plugin.api," +
             "org.killbill.billing.notification.plugin," +
             "org.killbill.billing.osgi.api," +
             "org.killbill.billing.osgi.api.config," +
             "org.killbill.billing.overdue," +
             "org.killbill.billing.payment.api," +
             "org.killbill.billing.payment.plugin.api," +
             "org.killbill.billing.tenant.api," +
             "org.killbill.billing.usage.api," +
             "org.killbill.billing.util.api," +
             "org.killbill.billing.util.audit," +
             "org.killbill.billing.util.callcontext," +
             "org.killbill.billing.util.customfield," +
             "org.killbill.billing.util.email," +
             "org.killbill.billing.util.entity," +
             "org.killbill.billing.util.tag," +
             "org.killbill.billing.util.template," +
             "org.killbill.billing.util.template.translation," +
             "org.killbill.billing.currency.plugin.api," +
             "org.killbill.billing.currency.api," +
             "org.killbill.billing.security.api," +

             // Add export for all the com.sun.xml.internal.ws required to have apache-cxf working properly within a plugin environment.
             "com.sun.xml.internal.ws," +
             "com.sun.xml.internal.ws.addressing," +
             "com.sun.xml.internal.ws.addressing.model," +
             "com.sun.xml.internal.ws.addressing.policy," +
             "com.sun.xml.internal.ws.addressing.v200408," +
             "com.sun.xml.internal.ws.api," +
             "com.sun.xml.internal.ws.api.addressing," +
             "com.sun.xml.internal.ws.api.client," +
             "com.sun.xml.internal.ws.api.config.management," +
             "com.sun.xml.internal.ws.api.config.management.policy," +
             "com.sun.xml.internal.ws.api.fastinfoset," +
             "com.sun.xml.internal.ws.api.ha," +
             "com.sun.xml.internal.ws.api.handler," +
             "com.sun.xml.internal.ws.api.message," +
             "com.sun.xml.internal.ws.api.message.stream," +
             "com.sun.xml.internal.ws.api.model," +
             "com.sun.xml.internal.ws.api.model.soap," +
             "com.sun.xml.internal.ws.api.model.wsdl," +
             "com.sun.xml.internal.ws.api.pipe," +
             "com.sun.xml.internal.ws.api.pipe.helper," +
             "com.sun.xml.internal.ws.api.policy," +
             "com.sun.xml.internal.ws.api.server," +
             "com.sun.xml.internal.ws.api.streaming," +
             "com.sun.xml.internal.ws.api.wsdl.parser," +
             "com.sun.xml.internal.ws.api.wsdl.writer," +
             "com.sun.xml.internal.ws.binding," +
             "com.sun.xml.internal.ws.client," +
             "com.sun.xml.internal.ws.client.dispatch," +
             "com.sun.xml.internal.ws.client.sei," +
             "com.sun.xml.internal.ws.config.management.policy," +
             "com.sun.xml.internal.ws.developer," +
             "com.sun.xml.internal.ws.encoding," +
             "com.sun.xml.internal.ws.encoding.fastinfoset," +
             "com.sun.xml.internal.ws.encoding.policy," +
             "com.sun.xml.internal.ws.encoding.soap," +
             "com.sun.xml.internal.ws.encoding.soap.streaming," +
             "com.sun.xml.internal.ws.encoding.xml," +
             "com.sun.xml.internal.ws.fault," +
             "com.sun.xml.internal.ws.handler," +
             "com.sun.xml.internal.ws.message," +
             "com.sun.xml.internal.ws.message.jaxb," +
             "com.sun.xml.internal.ws.message.saaj," +
             "com.sun.xml.internal.ws.message.source," +
             "com.sun.xml.internal.ws.message.stream," +
             "com.sun.xml.internal.ws.model," +
             "com.sun.xml.internal.ws.model.soap," +
             "com.sun.xml.internal.ws.model.wsdl," +
             "com.sun.xml.internal.ws.org.objectweb.asm," +
             "com.sun.xml.internal.ws.policy," +
             "com.sun.xml.internal.ws.policy.jaxws," +
             "com.sun.xml.internal.ws.policy.jaxws.spi," +
             "com.sun.xml.internal.ws.policy.privateutil," +
             "com.sun.xml.internal.ws.policy.sourcemodel," +
             "com.sun.xml.internal.ws.policy.sourcemodel.attach," +
             "com.sun.xml.internal.ws.policy.sourcemodel.wspolicy," +
             "com.sun.xml.internal.ws.policy.spi," +
             "com.sun.xml.internal.ws.policy.subject," +
             "com.sun.xml.internal.ws.protocol.soap," +
             "com.sun.xml.internal.ws.protocol.xml," +
             "com.sun.xml.internal.ws.resources," +
             "com.sun.xml.internal.ws.server," +
             "com.sun.xml.internal.ws.server.provider," +
             "com.sun.xml.internal.ws.server.sei," +
             "com.sun.xml.internal.ws.spi," +
             "com.sun.xml.internal.ws.streaming," +
             "com.sun.xml.internal.ws.transport," +
             "com.sun.xml.internal.ws.transport.http," +
             "com.sun.xml.internal.ws.transport.http.client," +
             "com.sun.xml.internal.ws.transport.http.server," +
             "com.sun.xml.internal.ws.util," +
             "com.sun.xml.internal.ws.util.exception," +
             "com.sun.xml.internal.ws.util.localization," +
             "com.sun.xml.internal.ws.util.pipe," +
             "com.sun.xml.internal.ws.util.xml," +
             "com.sun.xml.internal.ws.wsdl," +
             "com.sun.xml.internal.ws.wsdl.parser," +
             "com.sun.xml.internal.ws.wsdl.writer," +
             "com.sun.xml.internal.ws.wsdl.writer.document," +
             "com.sun.xml.internal.ws.wsdl.writer.document.http," +
             "com.sun.xml.internal.ws.wsdl.writer.document.soap," +
             "com.sun.xml.internal.ws.wsdl.writer.document.soap12," +
             "com.sun.xml.internal.ws.wsdl.writer.document.xsd," +

             // sax parser
             "javax.annotation," +
             "javax.jws.soap," +
             "org.xml.sax.ext;org.xml.sax.helpers;org.xml.sax," +

             // javax.servlet and javax.servlet.http are not exported by default - we
             // need the bundles to see them for them to be able to register their servlets.
             // Note: bundles should mark javax.servlet:servlet-api as provided
             "sun.misc," +
             "sun.misc.unsafe," +
             "javax.crypto," +
             "javax.crypto.spec," +
             "javax.management," +
             "javax.servlet;version=3.0," +
             "javax.servlet.http;version=3.0," +

             // Since we are using joda in our APIs we need to export it
             "org.joda.time;org.joda.time.format;version=2.3," +
             "org.osgi.service.log;version=1.3," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (osgi.wiring.package=org.osgi.service.http)
             "org.osgi.service.http;version=1.2.0," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (&(osgi.wiring.package=org.osgi.service.deploymentadmin)(version>=1.1.0)(!(version>=2.0.0)))
             "org.osgi.service.deploymentadmin;version=1.1.0," +
             // Let the world know the System bundle exposes (via org.osgi.compendium) the requirement (&(osgi.wiring.package=org.osgi.service.event)(version>=1.2.0)(!(version>=2.0.0)))
             "org.osgi.service.event;version=1.2.0," +
             // Let the world know the System bundle exposes the requirement (&(osgi.wiring.package=org.slf4j)(version>=1.7.0)(!(version>=2.0.0)))
             "org.slf4j;version=1.7.2")
    @Description("Packages to export from the system bundle")
    public String getSystemBundleExportPackages();
}
