/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.jaxrs.JaxrsTestSuite;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestTagDefinitionJson extends JaxrsTestSuite {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String id = UUID.randomUUID().toString();
        final Boolean isControlTag = true;
        final String name = UUID.randomUUID().toString();
        final String description = UUID.randomUUID().toString();
        final TagDefinitionJson tagDefinitionJson = new TagDefinitionJson(id, isControlTag, name, description);
        Assert.assertEquals(tagDefinitionJson.getId(), id);
        Assert.assertEquals(tagDefinitionJson.isControlTag(), isControlTag);
        Assert.assertEquals(tagDefinitionJson.getName(), name);
        Assert.assertEquals(tagDefinitionJson.getDescription(), description);

        final String asJson = mapper.writeValueAsString(tagDefinitionJson);
        final TagDefinitionJson fromJson = mapper.readValue(asJson, TagDefinitionJson.class);
        Assert.assertEquals(fromJson, tagDefinitionJson);
    }
}
