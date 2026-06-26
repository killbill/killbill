/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.killbill.commons.utils.annotation.VisibleForTesting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;

import jakarta.servlet.ServletConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Backward-compatible Swagger 2.0 spec endpoint — a deliberate hack.
 *
 * <h2>Background</h2>
 * Kill Bill migrated from swagger-core 1.x (Swagger 2.0 output) to swagger-core 2.x
 * (OpenAPI 3.0 output) as part of the Java 21 / Jakarta EE migration. OpenAPI 3.0 has
 * clearer, more correct spec annotations (e.g., requestBody is a first-class concept,
 * security schemes are properly typed, content negotiation is explicit per-response).
 * We want to keep these better annotations as the source of truth.
 *
 * <p>However, existing Kill Bill client SDKs (killbill-client-java, killbill-client-ruby,
 * etc.) were generated against the 0.24.x {@code /swagger.json} endpoint which produced
 * a Swagger 2.0 spec. To avoid breaking these clients (and the codegen pipelines that
 * produce them), this resource serves a Swagger 2.0 spec at the legacy URL by
 * converting the OpenAPI 3.0 model at runtime.
 *
 * <h2>Why not just annotate for Swagger 2.0 output?</h2>
 * swagger-core 2.x only produces OpenAPI 3.0. There is no official Java library to
 * convert OAS3 back to Swagger 2.0. Reverting to swagger-core 1.x would require
 * rewriting ~50+ source files (different annotation API). This runtime tree-surgery
 * approach keeps the codebase on modern annotations while serving the legacy format.
 *
 * <h2>Why swagger-core 1.x was technically wrong (and why we replicate it anyway)</h2>
 * swagger-core 1.x emitted {@code produces} and {@code consumes} on every operation —
 * even void endpoints (HTTP 204) that return no body at all. It blindly copied the
 * JAX-RS {@code @Produces}/{@code @Consumes} annotations regardless of whether a
 * response body actually existed. This is technically incorrect: a DELETE returning 204
 * does not "produce" application/json. However, the Kill Bill swagger-codegen templates
 * relied on this behavior to set Accept/Content-Type headers in generated clients. We
 * replicate it here (defaulting to "application/json" for ops with no content map) to
 * maintain codegen output compatibility.
 *
 * <h2>Note about api.html</h2>
 * The Swagger UI browser (api.html) loads {@code /openapi.json} — NOT this endpoint.
 * Swagger UI natively understands OpenAPI 3.0 and renders it correctly. Pointing it at
 * {@code /swagger.json} would add a useless conversion hop (OAS3 → SW2 → Swagger UI
 * internally converts back to OAS3 for rendering). This endpoint exists exclusively
 * for backward-compatible code generation.
 *
 * @see KillBillApiDefinition for OpenAPI 3.0 spec customization (the source of truth)
 */
@Hidden // Prevent swagger-core from scanning this resource into the spec itself
@Path("/swagger.{type:json|yaml}")
public class SwaggerResource {

    // ----------------------------------------------------------------------------------
    // Security scheme name mapping.
    // In OAS3, we use camelCase names (killbillApiKey, killbillApiSecret) which are
    // cleaner for the OpenAPI spec. But 0.24.x swagger used display-style names with
    // spaces. Generated clients reference these names, so we must map them back.
    // ----------------------------------------------------------------------------------
    private static final Map<String, String> SECURITY_SCHEME_NAMES = Map.of(
            "killbillApiKey", "Killbill Api Key",
            "killbillApiSecret", "Killbill Api Secret",
            "basicAuth", "basicAuth"
    );

    // ----------------------------------------------------------------------------------
    // Default produces/consumes for void operations.
    //
    // swagger-core 2.x (by design) only records media types inside OAS3 "content" maps,
    // which only exist when there's an actual body schema. For void operations (DELETE
    // returning 204, PUT with no response body), there is no content map, so the media
    // type information from JAX-RS @Produces/@Consumes is simply lost in the OAS3 output.
    //
    // In swagger-core 1.x, @Produces/@Consumes were ALWAYS copied to the operation level
    // regardless of whether a response body existed. The Kill Bill codegen relies on
    // "produces" being present to emit Accept headers in generated client code.
    //
    // We default to "application/json" because that's what 99% of void operations in
    // Kill Bill declare via @Produces(APPLICATION_JSON) — even though they return no body.
    // ----------------------------------------------------------------------------------
    private static final List<String> DEFAULT_PRODUCES = List.of("application/json");
    private static final List<String> DEFAULT_CONSUMES = List.of("application/json");

    // ----------------------------------------------------------------------------------
    // Operations that MUST NOT receive a "produces" entry in the Swagger 2.0 output.
    //
    // In the 0.24.x swagger spec, these 12 operations had no "produces" field. Unlike
    // the ~69 void operations above that have @Produces(APPLICATION_JSON) on their
    // JAX-RS methods (and thus had "produces" in 0.24.x), these operations genuinely
    // lack @Produces — they return 201/204 with no body and no content-type declaration.
    //
    // Two categories:
    //
    // (A) 7 operations with no @Produces AND no response @Content in OAS3:
    //     Without this set, DEFAULT_PRODUCES would incorrectly add produces:["application/json"].
    //
    // (B) 5 operations with no @Produces BUT with @Content in their @ApiResponse:
    //     The @Content was added solely for SCHEMA backward compatibility (preserving the
    //     generated return type — e.g., BlockingState[] or String). A side-effect is that
    //     the converter extracts produces from the response content map. Without this set,
    //     they would incorrectly gain produces:["application/json"] (or "*/*").
    //
    // Adding produces to these operations causes codegen to emit an Accept header that
    // was never present in the 0.24.x generated client.
    //
    // If a new operation is added without @Produces AND the codegen test detects a diff,
    // add its operationId here.
    // ----------------------------------------------------------------------------------
    private static final Set<String> SKIP_PRODUCES_OPS = Set.of(
            // (A) No @Produces, no response @Content
            "deleteCatalog",
            "deletePerTenantConfiguration",
            "deletePluginConfiguration",
            "deletePluginPaymentStateMachineConfig",
            "deletePushNotificationCallbacks",
            "deleteUserKeyValue",
            "renameExternalKey",
            // (B) No @Produces, but @Content added for schema backward compat
            "addAccountBlockingState",
            "addBundleBlockingState",
            "addSubscriptionBlockingState",
            "uploadCatalogXml",
            "uploadOverdueConfigXml"
    );

    // ----------------------------------------------------------------------------------
    // Operations that MUST NOT receive a "consumes" entry in the Swagger 2.0 output.
    //
    // Same principle as SKIP_PRODUCES_OPS: these 19 operations had no @Consumes annotation
    // on their JAX-RS methods, and the 0.24.x spec had no "consumes" for them. Without
    // this set, DEFAULT_CONSUMES would incorrectly add consumes:["application/json"],
    // causing codegen to emit a Content-Type header that was never present in the 0.24.x
    // generated client.
    //
    // Note: all non-JSON consumes (text/xml, text/plain, text/html, */*) come from
    // operations WITH a requestBody, so they're correctly extracted from the OAS3
    // requestBody content map and are NOT affected by this set.
    //
    // If a new operation is added without @Consumes AND the codegen test detects a diff,
    // add its operationId here.
    // ----------------------------------------------------------------------------------
    private static final Set<String> SKIP_CONSUMES_OPS = Set.of(
            "closeAccount",
            "removeEmail",
            "invalidatesCache",
            "invalidatesCacheByAccount",
            "invalidatesCacheByTenant",
            "putOutOfRotation",
            "deleteCatalog",
            "deletePaymentMethod",
            "cancelSubscriptionPlan",
            "deleteTagDefinition",
            "deletePushNotificationCallbacks",
            "deletePerTenantConfiguration",
            "deletePluginConfiguration",
            "deletePluginPaymentStateMachineConfig",
            "deleteUserKeyValue",
            "refreshPaymentMethods",
            "putInRotation",
            "uncancelSubscriptionPlan",
            "undoChangeSubscriptionPlan"
    );

    @Context
    private ServletConfig config;

    @Context
    private Application app;

    // Simple volatile cache — the spec doesn't change at runtime, so we generate once.
    private static volatile String cachedJson;
    private static volatile String cachedYaml;

    @GET
    @Produces({MediaType.APPLICATION_JSON, "application/yaml"})
    public Response getSwagger(@PathParam("type") final String type) throws Exception {
        if ("yaml".equalsIgnoreCase(type)) {
            if (cachedYaml == null) {
                cachedYaml = generateSwagger2(true);
            }
            return Response.ok(cachedYaml).type("application/yaml").build();
        } else {
            if (cachedJson == null) {
                cachedJson = generateSwagger2(false);
            }
            return Response.ok(cachedJson).type(MediaType.APPLICATION_JSON).build();
        }
    }

    private String generateSwagger2(final boolean yaml) throws Exception {
        // Reuse the same OpenAPI context that the /openapi.json endpoint uses.
        // This ensures KillBillApiDefinition's beforeScan/afterScan customizations
        // (security schemes, writeOnly property removal, orphaned schema cleanup)
        // are already applied to the model we're converting.
        final OpenApiContext ctx = new JaxrsOpenApiContextBuilder<>()
                .servletConfig(config)
                .application(app)
                .buildContext(true);
        final OpenAPI openAPI = ctx.read();

        final ObjectMapper mapper = Json.mapper();
        final ObjectNode oas3 = (ObjectNode) mapper.valueToTree(openAPI);
        final ObjectNode swagger2 = convertToSwagger2(mapper, oas3);

        if (yaml) {
            return createSwagger2YamlMapper().writerWithDefaultPrettyPrinter().writeValueAsString(swagger2);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(swagger2);
    }

    /**
     * Creates a YAML mapper matching the output style of swagger-core 1.x (0.24.x).
     *
     * <p>Key settings:
     * <ul>
     *   <li>No YAML document start marker ({@code ---}) — 0.24.x never emitted it</li>
     *   <li>Numbers always quoted as strings — critical for response code keys like
     *       "200" vs 200. Without quoting, YAML parsers interpret bare {@code 200:} as
     *       an integer key, which breaks swagger-codegen's response code lookup.</li>
     * </ul>
     */
    @VisibleForTesting
    ObjectMapper createSwagger2YamlMapper() {
        final YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .build();
        return new ObjectMapper(yamlFactory);
    }

    // ==================================================================================
    // OAS3 → Swagger 2.0 tree conversion
    //
    // The structural differences between OAS3 and Swagger 2.0 are well-defined and
    // mechanical. For Kill Bill's API surface (vanilla REST, no callbacks/links/webhooks),
    // the conversion is lossless. The transformations below are numbered for traceability.
    // ==================================================================================

    @VisibleForTesting
    ObjectNode convertToSwagger2(final ObjectMapper mapper, final ObjectNode oas3) {
        final ObjectNode sw2 = mapper.createObjectNode();

        // [1] Version: "openapi":"3.0.1" → "swagger":"2.0"
        sw2.put("swagger", "2.0");

        // [2] Info: contact.email → contact.name (0.24.x used "name" for the email value)
        if (oas3.has("info")) {
            final ObjectNode info = oas3.get("info").deepCopy();
            if (info.has("contact")) {
                final ObjectNode contact = (ObjectNode) info.get("contact");
                if (contact.has("email")) {
                    contact.put("name", contact.get("email").asText());
                    contact.remove("email");
                }
            }
            sw2.set("info", info);
        }

        // [3] Tags: strip descriptions (0.24.x swagger-core 1.x never emitted tag descriptions)
        if (oas3.has("tags")) {
            final ArrayNode tags = oas3.get("tags").deepCopy();
            for (final JsonNode tag : tags) {
                ((ObjectNode) tag).remove("description");
            }
            sw2.set("tags", tags);
        }

        // [4+5] Security definitions: rename scheme keys + convert basicAuth type
        //   OAS3: components.securitySchemes.basicAuth = {type:"http", scheme:"basic"}
        //   SW20: securityDefinitions.basicAuth = {type:"basic"}
        final ObjectNode components = (ObjectNode) oas3.get("components");
        if (components != null && components.has("securitySchemes")) {
            sw2.set("securityDefinitions", convertSecurityDefinitions(mapper, (ObjectNode) components.get("securitySchemes")));
        }

        // [6-10] Paths: requestBody, responses, params, produces/consumes, security
        if (oas3.has("paths")) {
            sw2.set("paths", convertPaths(mapper, (ObjectNode) oas3.get("paths")));
        }

        // [11] Definitions: OAS3 components/schemas → SW2 top-level "definitions"
        if (components != null && components.has("schemas")) {
            sw2.set("definitions", components.get("schemas").deepCopy());
        }

        // [12] Fix all $ref paths throughout the tree:
        //   "#/components/schemas/AccountJson" → "#/definitions/AccountJson"
        fixRefs(sw2);

        return sw2;
    }

    /**
     * Converts OAS3 security schemes to Swagger 2.0 security definitions.
     *
     * <p>Two transformations:
     * <ul>
     *   <li>basicAuth: OAS3 uses {type:"http", scheme:"basic"}, SW2 uses {type:"basic"}</li>
     *   <li>Key names: OAS3 camelCase → SW2 display names with spaces (for generated client compat)</li>
     * </ul>
     */
    @VisibleForTesting
    ObjectNode convertSecurityDefinitions(final ObjectMapper mapper, final ObjectNode securitySchemes) {
        final ObjectNode secDefs = mapper.createObjectNode();
        final Iterator<Map.Entry<String, JsonNode>> fields = securitySchemes.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            final String oas3Name = entry.getKey();
            final ObjectNode scheme = (ObjectNode) entry.getValue().deepCopy();

            // OAS3 basic auth: {type:"http", scheme:"basic"} → SW2: {type:"basic"}
            if ("http".equals(textOrNull(scheme, "type")) && "basic".equals(textOrNull(scheme, "scheme"))) {
                scheme.removeAll();
                scheme.put("type", "basic");
            }

            final String sw2Name = SECURITY_SCHEME_NAMES.getOrDefault(oas3Name, oas3Name);
            secDefs.set(sw2Name, scheme);
        }
        return secDefs;
    }

    @VisibleForTesting
    ObjectNode convertPaths(final ObjectMapper mapper, final ObjectNode paths) {
        final ObjectNode sw2Paths = mapper.createObjectNode();
        final Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            final Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            final ObjectNode pathItem = (ObjectNode) pathEntry.getValue().deepCopy();
            final Iterator<Map.Entry<String, JsonNode>> ops = pathItem.fields();
            while (ops.hasNext()) {
                final Map.Entry<String, JsonNode> opEntry = ops.next();
                if (opEntry.getValue().isObject()) {
                    convertOperation(mapper, (ObjectNode) opEntry.getValue(), opEntry.getKey());
                }
            }
            sw2Paths.set(pathEntry.getKey(), pathItem);
        }
        return sw2Paths;
    }

    @VisibleForTesting
    void convertOperation(final ObjectMapper mapper, final ObjectNode op, final String httpMethod) {
        final Set<String> produces = new LinkedHashSet<>();
        final Set<String> consumes = new LinkedHashSet<>();

        // ----------------------------------------------------------------------
        // [6] Convert requestBody → body parameter.
        //
        // In OAS3, request bodies are a first-class object separate from parameters.
        // In Swagger 2.0, they're a parameter with in:"body".
        // The content-type keys become the "consumes" array.
        // ----------------------------------------------------------------------
        if (op.has("requestBody")) {
            final ObjectNode requestBody = (ObjectNode) op.get("requestBody");
            final ObjectNode content = (ObjectNode) requestBody.get("content");
            if (content != null) {
                // Collect consumes media types from content keys
                content.fieldNames().forEachRemaining(consumes::add);

                // Extract schema from first content type entry
                final Map.Entry<String, JsonNode> firstContent = content.fields().next();
                final JsonNode mediaTypeObj = firstContent.getValue();
                final JsonNode schema = mediaTypeObj.get("schema");

                // Build the Swagger 2.0 body parameter
                final ObjectNode bodyParam = mapper.createObjectNode();
                bodyParam.put("in", "body");
                bodyParam.put("name", "body");
                if (requestBody.has("required")) {
                    bodyParam.set("required", requestBody.get("required"));
                }
                if (schema != null) {
                    bodyParam.set("schema", schema.deepCopy());
                }

                // Parameter ordering: path params → body → query → header.
                // This matches the 0.24.x order. swagger-codegen uses parameter position
                // to determine method argument order in generated clients, so changing
                // this order would break compilation of existing client code.
                ArrayNode params = (ArrayNode) op.get("parameters");
                if (params == null) {
                    params = mapper.createArrayNode();
                }
                final ArrayNode newParams = mapper.createArrayNode();
                for (final JsonNode p : params) {
                    if ("path".equals(textOrNull((ObjectNode) p, "in"))) {
                        newParams.add(p);
                    }
                }
                newParams.add(bodyParam);
                for (final JsonNode p : params) {
                    if (!"path".equals(textOrNull((ObjectNode) p, "in"))) {
                        newParams.add(p);
                    }
                }
                op.set("parameters", newParams);
            }
            op.remove("requestBody");
        }

        // ----------------------------------------------------------------------
        // [7] Convert parameters: unwrap "schema" to inline type/format/default/enum.
        //
        // In OAS3, all parameter type info is nested inside a "schema" object.
        // In Swagger 2.0, type/format/default/enum sit directly on the parameter.
        // Body params are excluded — they keep their schema as-is (a $ref).
        // ----------------------------------------------------------------------
        if (op.has("parameters")) {
            final ArrayNode params = (ArrayNode) op.get("parameters");
            for (final JsonNode paramNode : params) {
                final ObjectNode param = (ObjectNode) paramNode;
                final String in = textOrNull(param, "in");
                if ("body".equals(in)) {
                    continue;
                }
                if (param.has("schema")) {
                    final ObjectNode schema = (ObjectNode) param.get("schema");
                    copyIfPresent(schema, param, "type");
                    copyIfPresent(schema, param, "format");
                    copyIfPresent(schema, param, "default");
                    copyIfPresent(schema, param, "enum");
                    copyIfPresent(schema, param, "items");
                    copyIfPresent(schema, param, "pattern");
                    param.remove("schema");
                }
                // [7b] Add collectionFormat for array parameters.
                //
                // In Swagger 2.0, array query/header params need "collectionFormat" to
                // specify how multiple values are serialized. The default is "csv"
                // (comma-separated: ?key=a,b,c), but Kill Bill uses "multi"
                // (repeated params: ?key=a&key=b&key=c) — matching JAX-RS @QueryParam
                // List<String> binding semantics. The 0.24.x spec had "multi" on all
                // 106 array parameters. OAS3 uses "style":"form" + "explode":true for
                // the same behavior, but the Swagger 2.0 output needs the explicit field.
                if ("array".equals(textOrNull(param, "type"))) {
                    param.put("collectionFormat", "multi");
                }
                // In 0.24.x, non-path params without explicit required had required:false
                if (!"path".equals(in) && !param.has("required")) {
                    param.put("required", false);
                }
            }
        }

        // ----------------------------------------------------------------------
        // [8] Convert responses: flatten content.{mediaType}.schema → schema.
        //
        // In OAS3:  responses."200".content."application/json".schema.$ref
        // In SW20:  responses."200".schema.$ref
        // The content-type keys become the "produces" array.
        // ----------------------------------------------------------------------
        if (op.has("responses")) {
            final ObjectNode responses = (ObjectNode) op.get("responses");
            final Iterator<Map.Entry<String, JsonNode>> respEntries = responses.fields();
            while (respEntries.hasNext()) {
                final Map.Entry<String, JsonNode> respEntry = respEntries.next();
                if (!respEntry.getValue().isObject()) {
                    continue;
                }
                final ObjectNode resp = (ObjectNode) respEntry.getValue();
                if (resp.has("content")) {
                    final ObjectNode content = (ObjectNode) resp.get("content");
                    content.fieldNames().forEachRemaining(produces::add);
                    final Map.Entry<String, JsonNode> firstContent = content.fields().next();
                    final JsonNode mediaTypeObj = firstContent.getValue();
                    if (mediaTypeObj.has("schema")) {
                        resp.set("schema", mediaTypeObj.get("schema").deepCopy());
                    }
                    resp.remove("content");
                }
            }
        }

        // ----------------------------------------------------------------------
        // [9] Add produces/consumes arrays.
        //
        // WORKAROUND: Default to "application/json" when the OAS3 spec has no content
        // map (void operations returning 204, bodyless PUTs/DELETEs).
        //
        // This replicates a technically-incorrect behavior of swagger-core 1.x: it
        // always emitted produces/consumes from JAX-RS @Produces/@Consumes, even for
        // operations with no response body. Proof: a DELETE endpoint annotated with
        // @Produces(APPLICATION_JSON) that returns Response.status(204).build() — no
        // body is ever produced, yet swagger-core 1.x declared produces:["application/json"].
        //
        // swagger-core 2.x correctly omits media types for schema-less responses (by
        // design — OAS3 only places media types inside "content" maps). But the Kill Bill
        // codegen relies on "produces" to set Accept headers and "consumes" to set
        // Content-Type headers. Without these, generated clients omit the headers, which
        // changes the wire behavior.
        //
        // Note: SKIP_PRODUCES_OPS is checked first. These operations never had "produces"
        // in 0.24.x — some gained it here via response content extraction (side-effect of
        // @Content added for schema compat), others would get it from the default. Both
        // must be suppressed to match the 0.24.x codegen output.
        // ----------------------------------------------------------------------
        final String operationId = op.has("operationId") ? op.get("operationId").asText() : "";

        if (SKIP_PRODUCES_OPS.contains(operationId)) {
            // These operations never had "produces" in the 0.24.x spec — clear any
            // produces that were extracted from response content maps (side-effect of
            // @Content annotations added purely for schema backward compatibility).
            produces.clear();
        } else if (produces.isEmpty()) {
            DEFAULT_PRODUCES.forEach(produces::add);
        }

        if (!produces.isEmpty()) {
            final ArrayNode producesArray = op.putArray("produces");
            produces.forEach(producesArray::add);
        }

        // [9b] Default consumes for non-GET operations.
        //
        // Same rationale as produces default above: swagger-core 1.x always copied
        // @Consumes to the operation, even for bodyless DELETE/PUT/POST. swagger-core 2.x
        // correctly omits requestBody for bodyless operations (OAS3 semantics), so consumes
        // is empty after conversion. Codegen uses consumes to set Content-Type header.
        //
        // SKIP_CONSUMES_OPS mirrors SKIP_PRODUCES_OPS: these operations never had @Consumes
        // on their JAX-RS method and never had "consumes" in the 0.24.x spec. We also
        // exclude GET because no GET operation in the 0.24.x spec had consumes.
        if (!consumes.isEmpty()) {
            final ArrayNode consumesArray = op.putArray("consumes");
            consumes.forEach(consumesArray::add);
        } else if (!"get".equalsIgnoreCase(httpMethod) && !SKIP_CONSUMES_OPS.contains(operationId)) {
            final ArrayNode consumesArray = op.putArray("consumes");
            DEFAULT_CONSUMES.forEach(consumesArray::add);
        }

        // ----------------------------------------------------------------------
        // [10] Convert security: split combined object into separate per-scheme objects
        // + rename keys to 0.24.x display names.
        //
        // In OAS3, a single security requirement object with multiple keys means AND:
        //   [{"basicAuth":[], "killbillApiKey":[], "killbillApiSecret":[]}]
        //
        // In Swagger 2.0 (0.24.x output), each scheme was a separate requirement object:
        //   [{"basicAuth":[]}, {"Killbill Api Key":[]}, {"Killbill Api Secret":[]}]
        //
        // Note: Semantically these differ (OAS3=AND, SW2-as-emitted=OR), but swagger-core
        // 1.x always emitted them as separate objects and existing codegen handles it fine.
        // ----------------------------------------------------------------------
        if (op.has("security")) {
            final ArrayNode security = (ArrayNode) op.get("security");
            final ArrayNode sw2Security = op.arrayNode();
            for (final JsonNode reqNode : security) {
                final ObjectNode req = (ObjectNode) reqNode;
                final Iterator<Map.Entry<String, JsonNode>> schemes = req.fields();
                while (schemes.hasNext()) {
                    final Map.Entry<String, JsonNode> scheme = schemes.next();
                    final String sw2Name = SECURITY_SCHEME_NAMES.getOrDefault(scheme.getKey(), scheme.getKey());
                    final ObjectNode sw2Req = op.objectNode();
                    sw2Req.set(sw2Name, scheme.getValue());
                    sw2Security.add(sw2Req);
                }
            }
            op.set("security", sw2Security);
        }
    }

    /**
     * Recursively replaces all {@code $ref} values from OAS3 component paths to
     * Swagger 2.0 definition paths:
     * {@code #/components/schemas/X} → {@code #/definitions/X}
     */
    @VisibleForTesting
    void fixRefs(final JsonNode node) {
        if (node.isObject()) {
            final ObjectNode obj = (ObjectNode) node;
            if (obj.has("$ref")) {
                final String ref = obj.get("$ref").asText();
                if (ref.startsWith("#/components/schemas/")) {
                    obj.put("$ref", ref.replace("#/components/schemas/", "#/definitions/"));
                }
            }
            final Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                fixRefs(fields.next().getValue());
            }
        } else if (node.isArray()) {
            for (final JsonNode child : node) {
                fixRefs(child);
            }
        }
    }

    @VisibleForTesting
    static void copyIfPresent(final ObjectNode from, final ObjectNode to, final String field) {
        if (from.has(field)) {
            to.set(field, from.get(field));
        }
    }

    @VisibleForTesting
    static String textOrNull(final ObjectNode node, final String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }
}
