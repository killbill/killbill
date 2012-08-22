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

package com.ning.billing.jaxrs;

import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.testng.annotations.Test;

import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestTag extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testTagDefinitionOk() throws Exception {
        final TagDefinitionJson input = new TagDefinitionJson(null, false, "blue", "relaxing color", ImmutableList.<String>of());
        String baseJson = mapper.writeValueAsString(input);
        Response response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final String location = response.getHeader("Location");
        assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        final TagDefinitionJson objFromJson = mapper.readValue(baseJson, TagDefinitionJson.class);
        assertNotNull(objFromJson);
        assertEquals(objFromJson.getName(), input.getName());
        assertEquals(objFromJson.getDescription(), input.getDescription());
    }

    @Test(groups = "slow")
    public void testMultipleTagDefinitionOk() throws Exception {
        Response response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();

        List<TagDefinitionJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        final int sizeSystemTag = (objFromJson == null || objFromJson.size() == 0) ? 0 : objFromJson.size();

        final TagDefinitionJson inputBlue = new TagDefinitionJson(null, false, "blue", "relaxing color", ImmutableList.<String>of());
        baseJson = mapper.writeValueAsString(inputBlue);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final TagDefinitionJson inputRed = new TagDefinitionJson(null, false, "red", "hot color", ImmutableList.<String>of());
        baseJson = mapper.writeValueAsString(inputRed);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final TagDefinitionJson inputYellow = new TagDefinitionJson(null, false, "yellow", "vibrant color", ImmutableList.<String>of());
        baseJson = mapper.writeValueAsString(inputYellow);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        final TagDefinitionJson inputGreen = new TagDefinitionJson(null, false, "green", "super relaxing color", ImmutableList.<String>of());
        baseJson = mapper.writeValueAsString(inputGreen);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();

        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);

        final String uri = JaxrsResource.TAG_DEFINITIONS_PATH + "/" + objFromJson.get(0).getId();
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());

        response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();

        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);
    }
}
