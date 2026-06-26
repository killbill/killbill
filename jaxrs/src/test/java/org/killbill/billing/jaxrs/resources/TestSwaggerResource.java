/*
 * Copyright 2026 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestSwaggerResource extends JaxrsTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();
    private SwaggerResource resource;

    @BeforeMethod(groups = "fast")
    public void setUp() {
        resource = new SwaggerResource();
    }

    @Test(groups = "fast")
    public void testTextOrNullReturnsValueWhenPresent() {
        final ObjectNode node = mapper.createObjectNode().put("key", "value");
        Assert.assertEquals(SwaggerResource.textOrNull(node, "key"), "value");
    }

    @Test(groups = "fast")
    public void testTextOrNullReturnsNullWhenAbsent() {
        final ObjectNode node = mapper.createObjectNode();
        Assert.assertNull(SwaggerResource.textOrNull(node, "missing"));
    }

    @Test(groups = "fast")
    public void testCopyIfPresentCopiesExistingField() {
        final ObjectNode from = mapper.createObjectNode().put("type", "string");
        final ObjectNode to = mapper.createObjectNode();
        SwaggerResource.copyIfPresent(from, to, "type");
        Assert.assertEquals(to.get("type").asText(), "string");
    }

    @Test(groups = "fast")
    public void testCopyIfPresentIgnoresMissingField() {
        final ObjectNode from = mapper.createObjectNode();
        final ObjectNode to = mapper.createObjectNode();
        SwaggerResource.copyIfPresent(from, to, "type");
        Assert.assertFalse(to.has("type"));
    }

    @Test(groups = "fast")
    public void testCopyIfPresentCopiesArrayField() {
        final ObjectNode from = mapper.createObjectNode();
        final ArrayNode enumValues = from.putArray("enum");
        enumValues.add("NONE");
        enumValues.add("FULL");
        final ObjectNode to = mapper.createObjectNode();
        SwaggerResource.copyIfPresent(from, to, "enum");
        Assert.assertTrue(to.has("enum"));
        Assert.assertEquals(to.get("enum").size(), 2);
    }

    @Test(groups = "fast")
    public void testFixRefsReplacesComponentPath() {
        final ObjectNode node = mapper.createObjectNode()
                .put("$ref", "#/components/schemas/AccountJson");
        resource.fixRefs(node);
        Assert.assertEquals(node.get("$ref").asText(), "#/definitions/AccountJson");
    }

    @Test(groups = "fast")
    public void testFixRefsIgnoresNonComponentRef() {
        final ObjectNode node = mapper.createObjectNode()
                .put("$ref", "#/definitions/AlreadyCorrect");
        resource.fixRefs(node);
        Assert.assertEquals(node.get("$ref").asText(), "#/definitions/AlreadyCorrect");
    }

    @Test(groups = "fast")
    public void testFixRefsRecursesIntoNestedObjects() {
        final ObjectNode root = mapper.createObjectNode();
        final ObjectNode nested = root.putObject("schema");
        nested.put("$ref", "#/components/schemas/InvoiceJson");
        resource.fixRefs(root);
        Assert.assertEquals(nested.get("$ref").asText(), "#/definitions/InvoiceJson");
    }

    @Test(groups = "fast")
    public void testFixRefsRecursesIntoArrays() {
        final ObjectNode root = mapper.createObjectNode();
        final ArrayNode arr = root.putArray("items");
        final ObjectNode item = mapper.createObjectNode()
                .put("$ref", "#/components/schemas/PaymentJson");
        arr.add(item);
        resource.fixRefs(root);
        Assert.assertEquals(item.get("$ref").asText(), "#/definitions/PaymentJson");
    }

    @Test(groups = "fast")
    public void testConvertSecurityDefinitionsBasicAuth() {
        final ObjectNode schemes = mapper.createObjectNode();
        final ObjectNode basicAuth = schemes.putObject("basicAuth");
        basicAuth.put("type", "http");
        basicAuth.put("scheme", "basic");

        final ObjectNode result = resource.convertSecurityDefinitions(mapper, schemes);

        Assert.assertTrue(result.has("basicAuth"));
        Assert.assertEquals(result.get("basicAuth").get("type").asText(), "basic");
        Assert.assertFalse(result.get("basicAuth").has("scheme"));
    }

    @Test(groups = "fast")
    public void testConvertSecurityDefinitionsRenamesApiKey() {
        final ObjectNode schemes = mapper.createObjectNode();
        final ObjectNode apiKey = schemes.putObject("killbillApiKey");
        apiKey.put("type", "apiKey");
        apiKey.put("name", "X-Killbill-ApiKey");
        apiKey.put("in", "header");

        final ObjectNode result = resource.convertSecurityDefinitions(mapper, schemes);

        Assert.assertFalse(result.has("killbillApiKey"));
        Assert.assertTrue(result.has("Killbill Api Key"));
        Assert.assertEquals(result.get("Killbill Api Key").get("type").asText(), "apiKey");
    }

    @Test(groups = "fast")
    public void testConvertSecurityDefinitionsRenamesApiSecret() {
        final ObjectNode schemes = mapper.createObjectNode();
        final ObjectNode apiSecret = schemes.putObject("killbillApiSecret");
        apiSecret.put("type", "apiKey");
        apiSecret.put("name", "X-Killbill-ApiSecret");
        apiSecret.put("in", "header");

        final ObjectNode result = resource.convertSecurityDefinitions(mapper, schemes);

        Assert.assertFalse(result.has("killbillApiSecret"));
        Assert.assertTrue(result.has("Killbill Api Secret"));
    }

    @Test(groups = "fast")
    public void testConvertSecurityDefinitionsPreservesUnknownScheme() {
        final ObjectNode schemes = mapper.createObjectNode();
        final ObjectNode custom = schemes.putObject("customScheme");
        custom.put("type", "oauth2");

        final ObjectNode result = resource.convertSecurityDefinitions(mapper, schemes);

        // Unknown scheme name is preserved as-is
        Assert.assertTrue(result.has("customScheme"));
        Assert.assertEquals(result.get("customScheme").get("type").asText(), "oauth2");
    }

    @Test(groups = "fast")
    public void testConvertOperationRequestBodyToBodyParam() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "createAccount");

        // Build requestBody
        final ObjectNode requestBody = op.putObject("requestBody");
        requestBody.put("required", true);
        final ObjectNode content = requestBody.putObject("content");
        final ObjectNode jsonContent = content.putObject("application/json");
        jsonContent.putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        resource.convertOperation(mapper, op, "post");

        // requestBody should be removed
        Assert.assertFalse(op.has("requestBody"));
        // parameters should contain body param
        Assert.assertTrue(op.has("parameters"));
        final ArrayNode params = (ArrayNode) op.get("parameters");
        Assert.assertEquals(params.size(), 1);
        Assert.assertEquals(params.get(0).get("in").asText(), "body");
        Assert.assertEquals(params.get(0).get("name").asText(), "body");
        Assert.assertTrue(params.get(0).get("required").asBoolean());
        Assert.assertTrue(params.get(0).has("schema"));
        // consumes should be extracted
        Assert.assertTrue(op.has("consumes"));
        Assert.assertEquals(op.get("consumes").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationBodyParamOrderingPathThenBodyThenQuery() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "updateAccount");

        // Pre-existing parameters: path + query
        final ArrayNode params = op.putArray("parameters");
        params.addObject().put("in", "query").put("name", "queryParam");
        params.addObject().put("in", "path").put("name", "accountId");

        // requestBody
        final ObjectNode requestBody = op.putObject("requestBody");
        requestBody.put("required", true);
        final ObjectNode content = requestBody.putObject("content");
        content.putObject("application/json").putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        resource.convertOperation(mapper, op, "put");

        final ArrayNode resultParams = (ArrayNode) op.get("parameters");
        // Order: path -> body -> query
        Assert.assertEquals(resultParams.get(0).get("in").asText(), "path");
        Assert.assertEquals(resultParams.get(0).get("name").asText(), "accountId");
        Assert.assertEquals(resultParams.get(1).get("in").asText(), "body");
        Assert.assertEquals(resultParams.get(2).get("in").asText(), "query");
        Assert.assertEquals(resultParams.get(2).get("name").asText(), "queryParam");
    }

    @Test(groups = "fast")
    public void testConvertOperationUnwrapsParamSchema() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode param = params.addObject();
        param.put("in", "query");
        param.put("name", "audit");
        final ObjectNode schema = param.putObject("schema");
        schema.put("type", "string");
        schema.put("default", "NONE");
        schema.put("format", "custom");
        schema.put("pattern", "\\w+");
        final ArrayNode enumValues = schema.putArray("enum");
        enumValues.add("NONE");
        enumValues.add("FULL");

        resource.convertOperation(mapper, op, "get");

        // Schema should be removed, fields inlined
        Assert.assertFalse(param.has("schema"));
        Assert.assertEquals(param.get("type").asText(), "string");
        Assert.assertEquals(param.get("default").asText(), "NONE");
        Assert.assertEquals(param.get("format").asText(), "custom");
        Assert.assertEquals(param.get("pattern").asText(), "\\w+");
        Assert.assertEquals(param.get("enum").size(), 2);
    }

    @Test(groups = "fast")
    public void testConvertOperationDoesNotUnwrapBodyParamSchema() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "createAccount");

        // Simulate a body param already present (from requestBody conversion)
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode bodyParam = params.addObject();
        bodyParam.put("in", "body");
        bodyParam.put("name", "body");
        bodyParam.putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        resource.convertOperation(mapper, op, "post");

        // Body param schema should NOT be unwrapped
        Assert.assertTrue(bodyParam.has("schema"));
        Assert.assertTrue(bodyParam.get("schema").has("$ref"));
    }

    @Test(groups = "fast")
    public void testConvertOperationAddsCollectionFormatForArrayParams() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccounts");
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode param = params.addObject();
        param.put("in", "query");
        param.put("name", "pluginProperty");
        param.putObject("schema").put("type", "array").putObject("items").put("type", "string");

        resource.convertOperation(mapper, op, "get");

        Assert.assertEquals(param.get("type").asText(), "array");
        Assert.assertEquals(param.get("collectionFormat").asText(), "multi");
    }

    @Test(groups = "fast")
    public void testConvertOperationNoCollectionFormatForNonArrayParams() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode param = params.addObject();
        param.put("in", "query");
        param.put("name", "audit");
        param.putObject("schema").put("type", "string");

        resource.convertOperation(mapper, op, "get");

        Assert.assertFalse(param.has("collectionFormat"));
    }

    @Test(groups = "fast")
    public void testConvertOperationDefaultsRequiredFalseForNonPathParams() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccounts");
        final ArrayNode params = op.putArray("parameters");
        params.addObject().put("in", "query").put("name", "offset").putObject("schema").put("type", "integer");
        params.addObject().put("in", "header").put("name", "X-Custom").putObject("schema").put("type", "string");

        resource.convertOperation(mapper, op, "get");

        Assert.assertFalse(((ObjectNode) op.get("parameters").get(0)).get("required").asBoolean());
        Assert.assertFalse(((ObjectNode) op.get("parameters").get(1)).get("required").asBoolean());
    }

    @Test(groups = "fast")
    public void testConvertOperationDoesNotOverrideExistingRequired() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode param = params.addObject();
        param.put("in", "query");
        param.put("name", "required_param");
        param.put("required", true);
        param.putObject("schema").put("type", "string");

        resource.convertOperation(mapper, op, "get");

        Assert.assertTrue(param.get("required").asBoolean());
    }

    @Test(groups = "fast")
    public void testConvertOperationFlattensResponseContent() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        final ObjectNode responses = op.putObject("responses");
        final ObjectNode resp200 = responses.putObject("200");
        resp200.put("description", "successful operation");
        final ObjectNode content = resp200.putObject("content");
        content.putObject("application/json").putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        resource.convertOperation(mapper, op, "get");

        // content should be removed, schema inlined on the response
        final ObjectNode result200 = (ObjectNode) op.get("responses").get("200");
        Assert.assertFalse(result200.has("content"));
        Assert.assertTrue(result200.has("schema"));
        Assert.assertEquals(result200.get("schema").get("$ref").asText(), "#/components/schemas/AccountJson");
        // produces should be extracted
        Assert.assertTrue(op.has("produces"));
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationResponseWithNoContent() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "deleteAccount");
        final ObjectNode responses = op.putObject("responses");
        responses.putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "delete");

        // No content -> default produces should be applied (deleteAccount is not in SKIP_PRODUCES_OPS)
        Assert.assertTrue(op.has("produces"));
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationDefaultProducesForVoidOp() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "updateAccount");
        op.putObject("responses").putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "put");

        Assert.assertTrue(op.has("produces"));
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationSkipProducesForSkippedOp() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "deleteCatalog");
        op.putObject("responses").putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "delete");

        Assert.assertFalse(op.has("produces"));
    }

    @Test(groups = "fast")
    public void testConvertOperationSkipProducesWithContentExtraction() {
        // Simulate an op in SKIP_PRODUCES_OPS that has @Content
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "addAccountBlockingState");
        final ObjectNode responses = op.putObject("responses");
        final ObjectNode resp201 = responses.putObject("201");
        resp201.put("description", "Blocking state created");
        resp201.putObject("content").putObject("application/json").putObject("schema")
               .put("$ref", "#/components/schemas/BlockingState");

        resource.convertOperation(mapper, op, "post");

        // Even though content map extracted produces, SKIP_PRODUCES_OPS should suppress it
        Assert.assertFalse(op.has("produces"));
    }

    @Test(groups = "fast")
    public void testConvertOperationDefaultConsumesForNonGetOp() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "updateAccount");
        op.putObject("responses").putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "put");

        Assert.assertTrue(op.has("consumes"));
        Assert.assertEquals(op.get("consumes").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationNoDefaultConsumesForGetOp() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        op.putObject("responses").putObject("200").put("description", "successful operation");

        resource.convertOperation(mapper, op, "get");

        Assert.assertFalse(op.has("consumes"));
    }

    @Test(groups = "fast")
    public void testConvertOperationSkipConsumesForSkippedOp() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "closeAccount");
        op.putObject("responses").putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "delete");

        Assert.assertFalse(op.has("consumes"));
    }

    @Test(groups = "fast")
    public void testConvertOperationExplicitConsumesFromRequestBody() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "uploadCatalogXml");
        final ObjectNode requestBody = op.putObject("requestBody");
        requestBody.put("required", true);
        requestBody.putObject("content").putObject("text/xml").putObject("schema").put("type", "string");

        resource.convertOperation(mapper, op, "post");

        Assert.assertTrue(op.has("consumes"));
        Assert.assertEquals(op.get("consumes").get(0).asText(), "text/xml");
    }

    @Test(groups = "fast")
    public void testConvertOperationSplitsSecurity() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getAccount");
        final ArrayNode security = op.putArray("security");
        final ObjectNode combined = security.addObject();
        combined.putArray("basicAuth");
        combined.putArray("killbillApiKey");
        combined.putArray("killbillApiSecret");

        resource.convertOperation(mapper, op, "get");

        final ArrayNode resultSecurity = (ArrayNode) op.get("security");
        Assert.assertEquals(resultSecurity.size(), 3);
        // Each should be a separate object with renamed keys
        Assert.assertTrue(resultSecurity.get(0).has("basicAuth"));
        Assert.assertTrue(resultSecurity.get(1).has("Killbill Api Key"));
        Assert.assertTrue(resultSecurity.get(2).has("Killbill Api Secret"));
    }

    @Test(groups = "fast")
    public void testConvertOperationSecurityWithOnlyBasicAuth() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "createTenant");
        final ArrayNode security = op.putArray("security");
        security.addObject().putArray("basicAuth");

        resource.convertOperation(mapper, op, "post");

        final ArrayNode resultSecurity = (ArrayNode) op.get("security");
        Assert.assertEquals(resultSecurity.size(), 1);
        Assert.assertTrue(resultSecurity.get(0).has("basicAuth"));
    }

    @Test(groups = "fast")
    public void testConvertPathsConvertsAllMethods() {
        final ObjectNode paths = mapper.createObjectNode();
        final ObjectNode pathItem = paths.putObject("/1.0/kb/accounts/{accountId}");
        final ObjectNode getOp = pathItem.putObject("get");
        getOp.put("operationId", "getAccount");
        getOp.putObject("responses").putObject("200").put("description", "successful");
        final ObjectNode putOp = pathItem.putObject("put");
        putOp.put("operationId", "updateAccount");
        putOp.putObject("responses").putObject("204").put("description", "successful");

        final ObjectNode result = resource.convertPaths(mapper, paths);

        Assert.assertTrue(result.has("/1.0/kb/accounts/{accountId}"));
        final ObjectNode resultPath = (ObjectNode) result.get("/1.0/kb/accounts/{accountId}");
        // GET should have produces (default)
        Assert.assertTrue(resultPath.get("get").has("produces"));
        // PUT should have both produces and consumes (default)
        Assert.assertTrue(resultPath.get("put").has("produces"));
        Assert.assertTrue(resultPath.get("put").has("consumes"));
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2VersionField() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        oas3.putObject("info").put("title", "Kill Bill");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        Assert.assertEquals(result.get("swagger").asText(), "2.0");
        Assert.assertFalse(result.has("openapi"));
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2InfoContactEmailToName() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        final ObjectNode info = oas3.putObject("info");
        info.put("title", "Kill Bill");
        info.putObject("contact").put("email", "killbilling-users@googlegroups.com");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        final ObjectNode contact = (ObjectNode) result.get("info").get("contact");
        Assert.assertEquals(contact.get("name").asText(), "killbilling-users@googlegroups.com");
        Assert.assertFalse(contact.has("email"));
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2TagsStripDescriptions() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        final ArrayNode tags = oas3.putArray("tags");
        tags.addObject().put("name", "Account").put("description", "Account operations");
        tags.addObject().put("name", "Invoice").put("description", "Invoice operations");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        final ArrayNode resultTags = (ArrayNode) result.get("tags");
        Assert.assertEquals(resultTags.size(), 2);
        for (final JsonNode tag : resultTags) {
            Assert.assertTrue(tag.has("name"));
            Assert.assertFalse(tag.has("description"));
        }
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2SecurityDefinitions() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        final ObjectNode components = oas3.putObject("components");
        final ObjectNode schemes = components.putObject("securitySchemes");
        final ObjectNode basicAuth = schemes.putObject("basicAuth");
        basicAuth.put("type", "http");
        basicAuth.put("scheme", "basic");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        Assert.assertTrue(result.has("securityDefinitions"));
        Assert.assertEquals(result.get("securityDefinitions").get("basicAuth").get("type").asText(), "basic");
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2Definitions() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        final ObjectNode components = oas3.putObject("components");
        final ObjectNode schemas = components.putObject("schemas");
        schemas.putObject("AccountJson").put("type", "object");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        Assert.assertTrue(result.has("definitions"));
        Assert.assertTrue(result.get("definitions").has("AccountJson"));
        Assert.assertFalse(result.has("components"));
    }

    @Test(groups = "fast")
    public void testConvertToSwagger2FixesRefs() {
        final ObjectNode oas3 = mapper.createObjectNode();
        oas3.put("openapi", "3.0.1");
        final ObjectNode components = oas3.putObject("components");
        components.putObject("schemas").putObject("AccountJson").put("type", "object");
        final ObjectNode paths = oas3.putObject("paths");
        final ObjectNode getOp = paths.putObject("/1.0/kb/accounts/{accountId}").putObject("get");
        getOp.put("operationId", "getAccount");
        final ObjectNode resp = getOp.putObject("responses").putObject("200");
        resp.put("description", "successful operation");
        resp.putObject("content").putObject("application/json")
            .putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        final ObjectNode result = resource.convertToSwagger2(mapper, oas3);

        final String ref = result.get("paths")
                                 .get("/1.0/kb/accounts/{accountId}")
                                 .get("get")
                                 .get("responses")
                                 .get("200")
                                 .get("schema")
                                 .get("$ref").asText();
        Assert.assertEquals(ref, "#/definitions/AccountJson");
    }

    @Test(groups = "fast")
    public void testCreateSwagger2YamlMapperQuotesNumbers() throws Exception {
        final ObjectMapper yamlMapper = resource.createSwagger2YamlMapper();
        final ObjectNode node = mapper.createObjectNode();
        node.putObject("200").put("description", "OK");

        final String yaml = yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

        // "200" should be quoted, not bare integer
        Assert.assertTrue(yaml.contains("\"200\""), "Response code 200 should be quoted in YAML output");
        // Should not have YAML document start marker
        Assert.assertFalse(yaml.startsWith("---"), "YAML should not have document start marker");
    }

    @Test(groups = "fast")
    public void testConvertOperationFullRealisticOperation() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "createAccount");
        op.put("summary", "Create account");

        // Path + query params
        final ArrayNode params = op.putArray("parameters");
        final ObjectNode pathParam = params.addObject();
        pathParam.put("in", "path");
        pathParam.put("name", "accountId");
        pathParam.put("required", true);
        pathParam.putObject("schema").put("type", "string").put("pattern", "\\w+-\\w+-\\w+-\\w+-\\w+");

        final ObjectNode queryParam = params.addObject();
        queryParam.put("in", "query");
        queryParam.put("name", "pluginProperty");
        queryParam.putObject("schema").put("type", "array").putObject("items").put("type", "string");

        // RequestBody
        final ObjectNode requestBody = op.putObject("requestBody");
        requestBody.put("required", true);
        requestBody.putObject("content").putObject("application/json")
                   .putObject("schema").put("$ref", "#/components/schemas/AccountJson");

        // Response with content
        final ObjectNode responses = op.putObject("responses");
        responses.putObject("201").put("description", "Created")
                 .putObject("content").putObject("application/json")
                 .putObject("schema").put("$ref", "#/components/schemas/AccountJson");
        responses.putObject("400").put("description", "Invalid data");

        // Security
        final ObjectNode secReq = op.putArray("security").addObject();
        secReq.putArray("basicAuth");
        secReq.putArray("killbillApiKey");
        secReq.putArray("killbillApiSecret");

        resource.convertOperation(mapper, op, "post");

        // Verify parameter order: path → body → query
        final ArrayNode resultParams = (ArrayNode) op.get("parameters");
        Assert.assertEquals(resultParams.get(0).get("in").asText(), "path");
        Assert.assertEquals(resultParams.get(0).get("name").asText(), "accountId");
        Assert.assertEquals(resultParams.get(0).get("pattern").asText(), "\\w+-\\w+-\\w+-\\w+-\\w+");
        Assert.assertFalse(resultParams.get(0).has("schema"));

        Assert.assertEquals(resultParams.get(1).get("in").asText(), "body");
        Assert.assertTrue(resultParams.get(1).has("schema"));

        Assert.assertEquals(resultParams.get(2).get("in").asText(), "query");
        Assert.assertEquals(resultParams.get(2).get("collectionFormat").asText(), "multi");

        // Verify produces/consumes
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/json");
        Assert.assertEquals(op.get("consumes").get(0).asText(), "application/json");

        // Verify security split
        Assert.assertEquals(op.get("security").size(), 3);

        // Verify requestBody removed
        Assert.assertFalse(op.has("requestBody"));

        // Verify response schema flattened
        Assert.assertFalse(op.get("responses").get("201").has("content"));
        Assert.assertTrue(op.get("responses").get("201").has("schema"));
    }

    @Test(groups = "fast")
    public void testConvertOperationNoParameters() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "healthcheck");
        op.putObject("responses").putObject("200").put("description", "OK");

        // Should not throw
        resource.convertOperation(mapper, op, "get");

        Assert.assertFalse(op.has("parameters"));
        Assert.assertTrue(op.has("produces"));
    }

    @Test(groups = "fast")
    public void testConvertOperationNoOperationId() {
        final ObjectNode op = mapper.createObjectNode();
        // No operationId — should not throw NPE, should apply defaults
        op.putObject("responses").putObject("204").put("description", "Successful operation");

        resource.convertOperation(mapper, op, "delete");

        Assert.assertTrue(op.has("produces"));
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/json");
    }

    @Test(groups = "fast")
    public void testConvertOperationRequestBodyWithNoContent() {
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "someOp");
        op.putObject("requestBody"); // empty, no content

        resource.convertOperation(mapper, op, "post");

        // requestBody removed even without content
        Assert.assertFalse(op.has("requestBody"));
    }

    @Test(groups = "fast")
    public void testConvertOperationOctetStreamProduces() {
        // Simulates getQueueEntries — has content with octet-stream, NOT in skip list
        final ObjectNode op = mapper.createObjectNode();
        op.put("operationId", "getQueueEntries");
        final ObjectNode responses = op.putObject("responses");
        responses.putObject("200").put("description", "successful operation")
                 .putObject("content").putObject("application/octet-stream");

        resource.convertOperation(mapper, op, "get");

        Assert.assertTrue(op.has("produces"));
        Assert.assertEquals(op.get("produces").get(0).asText(), "application/octet-stream");
    }
}
