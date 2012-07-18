/*
 * Copyright 2010-2011 Ning, Inc.
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

import javax.ws.rs.core.Response.Status;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.resources.JaxrsResource;
import com.ning.http.client.Response;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestTag extends TestJaxrsBase {
    @Test(groups = "slow", enabled = true)
    public void testTagDefinitionOk() throws Exception {

        final TagDefinitionJson input = new TagDefinitionJson(null, "blue", "relaxing color");
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
        assertTrue(objFromJson.equalsNoId(input));
    }

    @Test(groups = "slow", enabled = true)
    public void testMultipleTagDefinitionOk() throws Exception {

        Response response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();

        List<TagDefinitionJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        final int sizeSystemTag = (objFromJson == null || objFromJson.size() == 0) ? 0 : objFromJson.size();

        TagDefinitionJson inputBlue = new TagDefinitionJson(null, "blue", "relaxing color");
        baseJson = mapper.writeValueAsString(inputBlue);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        TagDefinitionJson inputRed = new TagDefinitionJson(null, "red", "hot color");
        baseJson = mapper.writeValueAsString(inputRed);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        TagDefinitionJson inputYellow = new TagDefinitionJson(null, "yellow", "vibrant color");
        baseJson = mapper.writeValueAsString(inputYellow);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        TagDefinitionJson inputGreen = new TagDefinitionJson(null, "green", "super relaxing color");
        baseJson = mapper.writeValueAsString(inputGreen);
        response = doPost(JaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();

        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);



        String uri = JaxrsResource.TAG_DEFINITIONS_PATH + "/" + objFromJson.get(0).getId();
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        response = doGet(JaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();

        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);

    }

}
