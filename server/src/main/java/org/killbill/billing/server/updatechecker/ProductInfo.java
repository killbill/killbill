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

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;

/**
 * Kill Bill specific information
 * <p/>
 * At build time, we generated a magic file (version.properties) which should be on the classpath.
 */
public class ProductInfo {

    private static final Logger log = LoggerFactory.getLogger(ProductInfo.class);

    private static final String KILLBILL_SERVER_VERSION_RESOURCE = "/org.killbill/billing/server/version.properties";

    private static final String UNKNOWN = "UNKNOWN";

    private static final String PRODUCT_NAME = "product-name";
    private static final String VERSION = "version";
    private static final String BUILT_BY = "built-by";
    private static final String BUILD_JDK = "build-jdk";
    private static final String BUILD_TIME = "build-time";
    private static final String ENTERPRISE = "enterprise";

    private final Properties props = new Properties();

    public ProductInfo() {
        try {
            parseProductInfo(KILLBILL_SERVER_VERSION_RESOURCE);
        } catch (IOException e) {
            log.debug("Unable to detect current product info", e);
        }
    }

    private void parseProductInfo(final String resource) throws IOException {
        final URL resourceURL = Resources.getResource(resource);
        final InputSupplier<InputStreamReader> inputSupplier = Resources.newReaderSupplier(resourceURL, Charset.forName("UTF-8"));
        props.load(inputSupplier.getInput());
    }

    public String getName() {
        return getProperty(PRODUCT_NAME);
    }

    public String getVersion() {
        return getProperty(VERSION);
    }

    public String getBuiltBy() {
        return getProperty(BUILT_BY);
    }

    public String getBuildJdk() {
        return getProperty(BUILD_JDK);
    }

    public String getBuildTime() {
        return getProperty(BUILD_TIME);
    }

    public boolean isEnterprise() {
        return Boolean.parseBoolean(props.getProperty(ENTERPRISE));
    }

    private String getProperty(final String key) {
        return getSanitizedString(props.getProperty(key, UNKNOWN));
    }

    private String getSanitizedString(final String string) {
        return Strings.isNullOrEmpty(string) ? UNKNOWN : string.trim();
    }

    @Override
    public String toString() {
        final String fullProductName = String.format("%s (%s)", getName(), isEnterprise() ? "enterprise" : "community");
        return String.format("%s version %s was built on %s, with jdk %s by %s",
                             fullProductName, getVersion(), getBuildTime(), getBuildJdk(), getBuiltBy());
    }
}
