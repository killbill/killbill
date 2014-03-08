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

package org.killbill.billing.server.updatechecker;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.server.config.UpdateCheckConfig;

import com.dmurph.tracking.AnalyticsConfigData;
import com.dmurph.tracking.JGoogleAnalyticsTracker;
import com.dmurph.tracking.JGoogleAnalyticsTracker.GoogleAnalyticsVersion;

public class Tracker {

    private static final Logger log = LoggerFactory.getLogger(Tracker.class);
    private static final String TRACKING_CODE = "UA-44821278-1";

    // Information about this version of Kill Bill
    final ProductInfo productInfo;
    // Information about this JVM
    private final ClientInfo clientInfo;
    private final JGoogleAnalyticsTracker tracker;

    public Tracker(final ProductInfo productInfo, final ServletContext context) {
        this.productInfo = productInfo;
        this.clientInfo = new ClientInfo(context);

        final AnalyticsConfigData analyticsConfigData = new AnalyticsConfigData(TRACKING_CODE);
        this.tracker = new JGoogleAnalyticsTracker(analyticsConfigData, GoogleAnalyticsVersion.V_4_7_2);
    }

    public void track() {
        trackProperty("product", "name", productInfo.getName());
        trackProperty("product", "version", productInfo.getVersion());
        trackProperty("product", "builtBy", productInfo.getBuiltBy());
        trackProperty("product", "buildJdk", productInfo.getBuildJdk());
        trackProperty("product", "buildTime", productInfo.getBuildTime());
        trackProperty("product", "enterprise", String.valueOf(productInfo.isEnterprise()));

        trackProperty("client", "servletMajorVersion", clientInfo.getServletMajorVersion());
        trackProperty("client", "servletMinorVersion", clientInfo.getServletMinorVersion());
        trackProperty("client", "servletEffectiveMajorVersion", clientInfo.getServletEffectiveMajorVersion());
        trackProperty("client", "servletEffectiveMinorVersion", clientInfo.getServletEffectiveMinorVersion());
        trackProperty("client", "serverInfo", clientInfo.getServerInfo());
        trackProperty("client", "clientId", clientInfo.getClientId());
        trackProperty("client", "javaVersion", clientInfo.getJavaVersion());
        trackProperty("client", "javaVendor", clientInfo.getJavaVendor());
        trackProperty("client", "javaVendorURL", clientInfo.getJavaVendorURL());
        trackProperty("client", "javaVMSpecificationVersion", clientInfo.getJavaVMSpecificationVersion());
        trackProperty("client", "javaVMSpecificationVendor", clientInfo.getJavaVMSpecificationVendor());
        trackProperty("client", "javaVMSpecificationName", clientInfo.getJavaVMSpecificationName());
        trackProperty("client", "javaVMVersion", clientInfo.getJavaVMVersion());
        trackProperty("client", "javaVMVendor", clientInfo.getJavaVMVendor());
        trackProperty("client", "javaVMName", clientInfo.getJavaVMName());
        trackProperty("client", "javaSpecificationVersion", clientInfo.getJavaSpecificationVersion());
        trackProperty("client", "javaSpecificationVendor", clientInfo.getJavaSpecificationVendor());
        trackProperty("client", "javaSpecificationName", clientInfo.getJavaSpecificationName());
        trackProperty("client", "javaClassVersion", clientInfo.getJavaClassVersion());
        trackProperty("client", "javaCompiler", clientInfo.getJavaCompiler());
        trackProperty("client", "platform", clientInfo.getPlatform());
        trackProperty("client", "osName", clientInfo.getOSName());
        trackProperty("client", "osArch", clientInfo.getOSArch());
        trackProperty("client", "osVersion", clientInfo.getOSVersion());
    }

    private void trackProperty(final String category, final String key, final String value) {
        // Workaround for https://code.google.com/p/analytics-issues/issues/detail?id=219
        String sanitizedValue = value;
        sanitizedValue = sanitizedValue.replace('(', '-');
        sanitizedValue = sanitizedValue.replace(')', '-');

        log.debug("Tracking {}: {}={}", category, key, sanitizedValue);
        tracker.trackEvent(category, key, sanitizedValue);
    }
}
