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

package com.ning.billing.osgi.bundles.analytics.json;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class XY {

    private final String x;
    private final Float y;

    private final DateTime xDate;

    @JsonCreator
    public XY(@JsonProperty("x") final String x, @JsonProperty("y") final Float y) {
        this.x = x;
        this.y = y;
        this.xDate = new DateTime(x, DateTimeZone.UTC);
    }

    public XY(final String x, final Integer y) {
        this(x, new Float(y.doubleValue()));
    }

    public XY(final DateTime xDate, final Float y) {
        this.x = xDate.toString();
        this.y = y;
        this.xDate = xDate;
    }

    public XY(final LocalDate xDate, final Float y) {
        this(xDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), y);
    }

    public String getX() {
        return x;
    }

    @JsonIgnore
    public DateTime getxDate() {
        return xDate;
    }

    public Float getY() {
        return y;
    }
}
