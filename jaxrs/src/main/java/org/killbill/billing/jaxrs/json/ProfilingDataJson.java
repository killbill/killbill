/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.killbill.billing.platform.profiling.ProfilingData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProfilingDataJson {

    private final Map<String, List<Long>> rawData;
    private final String createdUri;

    @JsonCreator
    public ProfilingDataJson(@JsonProperty("createdUri") final String createdUri,
            @JsonProperty("rawData") final Map<String, List<Long>> rawData) {
        this.createdUri = createdUri;
        this.rawData = rawData;
    }

    public ProfilingDataJson(final ProfilingData data, final URI uri) {
        this(uri.getPath(), data.getRawData());
    }

    public Map<String, List<Long>> getRawData() {
        return rawData;
    }

    public String getCreatedUri() {
        return createdUri;
    }
}
