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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ning.billing.jaxrs.json.TagDefinitionJson;
import com.ning.billing.jaxrs.resources.BaseJaxrsResource;
import com.ning.http.client.Response;

public class TestTag extends TestJaxrsBase {

    private static final Logger log = LoggerFactory.getLogger(TestTag.class);

    @Test(groups="slow", enabled=true)
    public void testTagDefinitionOk() throws Exception {
    
        TagDefinitionJson input = new TagDefinitionJson("blue", "realxing color");
        String baseJson = mapper.writeValueAsString(input);
        Response response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        String location = response.getHeader("Location");
        assertNotNull(location);

        // Retrieves by Id based on Location returned
        response = doGetWithUrl(location, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());

        baseJson = response.getResponseBody();
        TagDefinitionJson objFromJson = mapper.readValue(baseJson, TagDefinitionJson.class);
        assertNotNull(objFromJson);
        assertEquals(objFromJson, input);
    }
    
    @Test(groups="slow", enabled=true)
    public void testMultipleTagDefinitionOk() throws Exception {
    
        Response response = doGet(BaseJaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        String baseJson = response.getResponseBody();
        
        List<TagDefinitionJson> objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        int sizeSystemTag = (objFromJson == null || objFromJson.size() == 0) ? 0 : objFromJson.size();
        
        TagDefinitionJson input = new TagDefinitionJson("blue", "realxing color");
        baseJson = mapper.writeValueAsString(input);
        response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        input = new TagDefinitionJson("red", "hot color");
        baseJson = mapper.writeValueAsString(input);
        response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        input = new TagDefinitionJson("yellow", "vibrant color");
        baseJson = mapper.writeValueAsString(input);
        response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        input = new TagDefinitionJson("green", "super realxing color");
        baseJson = mapper.writeValueAsString(input);
        response = doPost(BaseJaxrsResource.TAG_DEFINITIONS_PATH, baseJson, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.CREATED.getStatusCode());

        response = doGet(BaseJaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        
        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 4 + sizeSystemTag);

        // STEPH currently broken Tag API does not work as expected...
        
        /*
        String uri = BaseJaxrsResource.TAG_DEFINITIONS_PATH + "/green"; 
        response = doDelete(uri, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
    
        response = doGet(BaseJaxrsResource.TAG_DEFINITIONS_PATH, DEFAULT_EMPTY_QUERY, DEFAULT_HTTP_TIMEOUT_SEC);
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
        baseJson = response.getResponseBody();
        
        objFromJson = mapper.readValue(baseJson, new TypeReference<List<TagDefinitionJson>>() {});
        assertNotNull(objFromJson);
        assertEquals(objFromJson.size(), 3 + sizeSystemTag);
        */
    }
    
}
