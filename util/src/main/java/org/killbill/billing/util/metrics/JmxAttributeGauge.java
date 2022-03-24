/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.util.metrics;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.killbill.commons.metrics.api.Gauge;

/**
 * A {@link Gauge} implementation which queries an {@link MBeanServerConnection} for an attribute of an object.
 * <p>
 * Initially forked from Dropwizard Metrics (Apache License 2.0).
 * Copyright (c) 2010-2013 Coda Hale, Yammer.com, 2014-2021 Dropwizard Team
 */
public class JmxAttributeGauge implements Gauge<Object> {

    private final MBeanServerConnection mBeanServerConn;
    private final ObjectName objectName;
    private final String attributeName;

    /**
     * Creates a new JmxAttributeGauge.
     *
     * @param objectName    the name of the object
     * @param attributeName the name of the object's attribute
     */
    public JmxAttributeGauge(final ObjectName objectName, final String attributeName) {
        this(ManagementFactory.getPlatformMBeanServer(), objectName, attributeName);
    }

    /**
     * Creates a new JmxAttributeGauge.
     *
     * @param mBeanServerConn the {@link MBeanServerConnection}
     * @param objectName      the name of the object
     * @param attributeName   the name of the object's attribute
     */
    public JmxAttributeGauge(final MBeanServerConnection mBeanServerConn, final ObjectName objectName, final String attributeName) {
        this.mBeanServerConn = mBeanServerConn;
        this.objectName = objectName;
        this.attributeName = attributeName;
    }

    @Override
    public Object getValue() {
        try {
            return mBeanServerConn.getAttribute(getObjectName(), attributeName);
        } catch (final IOException | JMException e) {
            return null;
        }
    }

    private ObjectName getObjectName() throws IOException {
        if (objectName.isPattern()) {
            final Set<ObjectName> foundNames = mBeanServerConn.queryNames(objectName, null);
            if (foundNames.size() == 1) {
                return foundNames.iterator().next();
            }
        }
        return objectName;
    }
}