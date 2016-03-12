/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public abstract class CatalogDateHelper {

    // From JDK to Joda (see http://www.joda.org/joda-time/userguide.html#JDK_Interoperability)
    public static DateTime toUTCDateTime(final Date date) {
        return new DateTime(date).toDateTime(DateTimeZone.UTC);
    }
}
