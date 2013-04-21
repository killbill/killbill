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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class NamedXYTimeSeries {

    private final String name;
    private final List<XY> values;

    @JsonCreator
    public NamedXYTimeSeries(@JsonProperty("name") final String name, @JsonProperty("values") final List<XY> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public List<XY> getValues() {
        return values;
    }
}
