/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.util.listener;

import java.io.IOException;

import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.bus.api.BusEvent;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class SubscriberNotificationEventDeserializer extends JsonDeserializer<SubscriberNotificationEvent> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SubscriberNotificationEvent deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException, JsonProcessingException {
        final JsonNode node = p.getCodec().readTree(p);

        final Class<BusEvent> busEventClass;
        try {
            busEventClass = (Class<BusEvent>) Class.forName(node.get("busEventClass").textValue());
        } catch (final ClassNotFoundException e) {
            throw new IOException(e);
        }

        return new SubscriberNotificationEvent(objectMapper.treeToValue(node.get("busEvent"), busEventClass), busEventClass);
    }
}
