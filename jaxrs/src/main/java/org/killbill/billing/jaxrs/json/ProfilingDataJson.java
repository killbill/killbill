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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.killbill.commons.profiling.ProfilingData;
import org.killbill.commons.profiling.ProfilingData.LogLineType;
import org.killbill.commons.profiling.ProfilingData.ProfilingDataItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModel;

@ApiModel(value="ProfilingData")
public class ProfilingDataJson {

    private final List<ProfilingDataJsonItem> rawData;

    @JsonCreator
    public ProfilingDataJson(@JsonProperty("rawData") final List<ProfilingDataJsonItem> rawData) {
        this.rawData = rawData;
    }

    public ProfilingDataJson(final ProfilingData input) {
        final List<ProfilingDataItem> items = input.getRawData();
        if (items.isEmpty()) {
            this.rawData = Collections.emptyList();
            return;
        }

        final List<ProfilingDataJsonItem> root = new ArrayList<ProfilingDataJsonItem>();

        final Stack<ProfilingDataJsonItem> stack = new Stack<ProfilingDataJsonItem>();
        while (items.size() > 0) {

            final ProfilingDataItem cur = items.remove(0);

            if (cur.getLineType() == LogLineType.START) {

                // Create new element
                final ProfilingDataJsonItem jsonItem = new ProfilingDataJsonItem(cur.getKey(), nanoToMicro(cur.getTimestampNsec()), Long.MIN_VALUE, new ArrayList<ProfilingDataJsonItem>());
                // If stack is empty this belong to top level list, if to the parent's list
                if (stack.isEmpty()) {
                    root.add(jsonItem);
                } else {
                    final ProfilingDataJsonItem parent = stack.peek();
                    parent.addChild(jsonItem);
                }
                // Add current element to the stack
                stack.push(jsonItem);
            } else /* LogLineType.STOP */ {
                // Fetch current element and update its duration time
                final ProfilingDataJsonItem jsonItem = stack.pop();
                jsonItem.setDurationUsec(nanoToMicro(cur.getTimestampNsec()) - jsonItem.getStartUsec());
            }
        }
        Preconditions.checkState(stack.isEmpty());
        this.rawData = root;
    }

    public List<ProfilingDataJsonItem> getRawData() {
        return rawData;
    }

    public class ProfilingDataJsonItem {

        private final String name;
        private final Long startUsec;
        private final List<ProfilingDataJsonItem> calls;
        // Not final so we can build the data structure in one pass
        private Long durationUsec;

        @JsonCreator
        public ProfilingDataJsonItem(@JsonProperty("name") final String name,
                                     @JsonProperty("startUsec") final Long startUsec,
                                     @JsonProperty("durationUsec") final Long durationUsec,
                                     @JsonProperty("calls") final List<ProfilingDataJsonItem> calls) {
            this.name = name;
            this.startUsec = startUsec;
            this.durationUsec = durationUsec;
            this.calls = calls;
        }

        public String getName() {
            return name;
        }

        @JsonIgnore
        public Long getStartUsec() {
            return startUsec;
        }

        public Long getDurationUsec() {
            return durationUsec;
        }

        public void addChild(final ProfilingDataJsonItem child) {
            calls.add(child);
        }

        public List<ProfilingDataJsonItem> getCalls() {
            return calls;
        }

        public void setDurationUsec(final Long durationUsec) {
            this.durationUsec = durationUsec;
        }
    }

    private static Long nanoToMicro(final Long nanoSec) {
        return (nanoSec / 1000);
    }
}
