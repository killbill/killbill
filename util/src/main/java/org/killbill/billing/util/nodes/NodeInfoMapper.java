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

package org.killbill.billing.util.nodes;

import java.io.IOException;

import javax.inject.Inject;

import org.killbill.billing.util.nodes.json.NodeInfoModelJson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class NodeInfoMapper {

    private final ObjectMapper mapper;

    @Inject
    public NodeInfoMapper() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    public String serializeNodeInfo(final NodeInfoModelJson nodeInfo) throws JsonProcessingException {
        return mapper.writeValueAsString(nodeInfo);
    }

    public NodeInfoModelJson deserializeNodeInfo(final String nodeInfo) throws IOException {
        return mapper.readValue(nodeInfo, NodeInfoModelJson.class);
    }

    public String serializeNodeCommand(final NodeCommandMetadata nodeCommandMetadata) throws JsonProcessingException {
        return mapper.writeValueAsString(nodeCommandMetadata);
    }

    public NodeCommandMetadata deserializeNodeCommand(final String nodeCommand, final String type) throws IOException {

        final SystemNodeCommandType systemType = Iterables.tryFind(ImmutableList.copyOf(SystemNodeCommandType.values()), new Predicate<SystemNodeCommandType>() {
            @Override
            public boolean apply(final SystemNodeCommandType input) {
                return input.name().equals(type);
            }
        }).orNull();

        return (systemType != null) ?
               mapper.readValue(nodeCommand, systemType.getCommandMetadataClass()) :
               mapper.readValue(nodeCommand, DefaultNodeCommandMetadata.class);
    }

}
