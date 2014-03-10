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

import java.net.InetAddress;
import java.util.Properties;

import javax.servlet.ServletContext;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;

/**
 * Gather client-side information
 * <p/>
 * We try not to gather any personally identifiable information, only
 * specifications about the installation (OS, JVM). This helps us
 * focus our development efforts.
 */
public class ClientInfo {

    private static final String UNKNOWN = "UNKNOWN";

    private static int CLIENT_ID;

    static {
        try {
            CLIENT_ID = InetAddress.getLocalHost().hashCode();
        } catch (Throwable t) {
            CLIENT_ID = 0;
        }
    }

    private final ServletContext servletContext;
    private final Properties props;

    public ClientInfo(final ServletContext servletContext) {
        this.servletContext = servletContext;
        this.props = System.getProperties();
    }

    public String getServletMajorVersion() {
        return getSanitizedString(String.valueOf(servletContext.getMajorVersion()));
    }

    public String getServletMinorVersion() {
        return getSanitizedString(String.valueOf(servletContext.getMinorVersion()));
    }

    public String getServletEffectiveMajorVersion() {
        return getSanitizedString(String.valueOf(servletContext.getEffectiveMajorVersion()));
    }

    public String getServletEffectiveMinorVersion() {
        return getSanitizedString(String.valueOf(servletContext.getEffectiveMinorVersion()));
    }

    public String getServerInfo() {
        return getSanitizedString(servletContext.getServerInfo());
    }

    public String getClientId() {
        return String.valueOf(CLIENT_ID);
    }

    public String getJavaVersion() {
        return getProperty(StandardSystemProperty.JAVA_VERSION);
    }

    public String getJavaVendor() {
        return getProperty(StandardSystemProperty.JAVA_VENDOR);
    }

    public String getJavaVendorURL() {
        return getProperty(StandardSystemProperty.JAVA_VENDOR_URL);
    }

    public String getJavaVMSpecificationVersion() {
        return getProperty(StandardSystemProperty.JAVA_VM_SPECIFICATION_VERSION);
    }

    public String getJavaVMSpecificationVendor() {
        return getProperty(StandardSystemProperty.JAVA_VM_SPECIFICATION_VENDOR);
    }

    public String getJavaVMSpecificationName() {
        return getProperty(StandardSystemProperty.JAVA_VM_SPECIFICATION_NAME);
    }

    public String getJavaVMVersion() {
        return getProperty(StandardSystemProperty.JAVA_VM_VERSION);
    }

    public String getJavaVMVendor() {
        return getProperty(StandardSystemProperty.JAVA_VM_VENDOR);
    }

    public String getJavaVMName() {
        return getProperty(StandardSystemProperty.JAVA_VM_NAME);
    }

    public String getJavaSpecificationVersion() {
        return getProperty(StandardSystemProperty.JAVA_SPECIFICATION_VERSION);
    }

    public String getJavaSpecificationVendor() {
        return getProperty(StandardSystemProperty.JAVA_SPECIFICATION_VENDOR);
    }

    public String getJavaSpecificationName() {
        return getProperty(StandardSystemProperty.JAVA_SPECIFICATION_NAME);
    }

    public String getJavaClassVersion() {
        return getProperty(StandardSystemProperty.JAVA_CLASS_VERSION);
    }

    public String getJavaCompiler() {
        return getProperty(StandardSystemProperty.JAVA_COMPILER);
    }

    public String getPlatform() {
        return getProperty(StandardSystemProperty.OS_ARCH);
    }

    public String getOSName() {
        return getProperty(StandardSystemProperty.OS_NAME);
    }

    public String getOSArch() {
        return getProperty(StandardSystemProperty.OS_ARCH);
    }

    public String getOSVersion() {
        return getProperty(StandardSystemProperty.OS_VERSION);
    }

    private String getProperty(final StandardSystemProperty standardKey) {
        return getSanitizedString(props.getProperty(standardKey.key(), UNKNOWN));
    }

    private String getSanitizedString(final String string) {
        return Strings.isNullOrEmpty(string) ? UNKNOWN : string.trim();
    }
}
