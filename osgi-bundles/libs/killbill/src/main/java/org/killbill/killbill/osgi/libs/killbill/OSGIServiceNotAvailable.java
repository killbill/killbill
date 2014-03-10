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

package org.killbill.killbill.osgi.libs.killbill;

public class OSGIServiceNotAvailable extends RuntimeException  {

    private static final String FORMAT_SERVICE_NOT_AVAILABLE = "OSGI service %s is not available";

    public OSGIServiceNotAvailable(String serviceName) {
        super(toFormat(serviceName));
    }

    public OSGIServiceNotAvailable(String serviceName, Throwable cause) {
        super(toFormat(serviceName), cause);
    }

    public OSGIServiceNotAvailable(Throwable cause) {
        super(cause);
    }

    private static String toFormat(String serviceName) {
        return String.format(FORMAT_SERVICE_NOT_AVAILABLE, serviceName);
    }
}
