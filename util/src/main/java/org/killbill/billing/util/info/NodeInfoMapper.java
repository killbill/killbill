/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.util.info;

import java.io.IOException;

import javax.inject.Inject;

import org.killbill.billing.util.info.json.NodeInfoModelJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class NodeInfoMapper {

    private final ObjectMapper mapper;

    @Inject
    public NodeInfoMapper() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    public String serialize(final NodeInfoModelJson nodeInfo) throws JsonProcessingException {
        return mapper.writeValueAsString(nodeInfo);
    }


    public NodeInfoModelJson deserialize(final String nodeInfo) throws IOException {
        return mapper.readValue(nodeInfo, NodeInfoModelJson.class);
    }
}
