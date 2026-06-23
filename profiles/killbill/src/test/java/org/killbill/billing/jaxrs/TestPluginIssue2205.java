/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.killbill.billing.osgi.http.DefaultServletRouter;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Integration tests for {@code PluginResource.OSGIServletResponseWrapper}.
 *
 * <p>These tests cover the full-buffering wrapper introduced for
 * <a href="https://github.com/killbill/killbill/issues/2205">#2205</a>: any servlet API a plugin may call
 * (sendError, sendRedirect, getWriter, flushBuffer, reset, isCommitted, ...) must be captured in-memory so it cannot
 * commit / close the underlying Jetty response before Jersey writes the response built by {@code PluginResource}.</p>
 *
 * <p>Before that change, calling {@code sendError(...)} from a plugin (or hitting an unknown plugin path) caused
 * {@code EofException: Closed} on the server side.</p>
 *
 * <h2>Why this lives in its own file (separate from {@link TestPlugin})</h2>
 *
 * <p>{@link TestPlugin} already exercises {@code /plugins/*} routing across all HTTP verbs
 * (GET/HEAD/POST/PUT/DELETE/OPTIONS), with a 6-marker symmetric structure and uses the {@code KillBillHttpClient}
 * helpers. Folding these tests into that class would mix two distinct concerns &mdash; HTTP method routing on one
 * hand, and the response-wrapper Servlet contract on the other &mdash; and would also force two HTTP-client styles
 * to coexist in one file (see {@link #doGet} below for why). Keeping them separate makes intent obvious from the
 * file name (it pins the tests to the originating issue), keeps {@link TestPlugin}'s symmetry intact, and makes it
 * easier to evolve issue&nbsp;#2205-specific tests (e.g. add a logback appender check for {@code EofException}
 * suppression) without polluting the routing tests.</p>
 */
public class TestPluginIssue2205 extends TestJaxrsBase {

    private static final String PLUGIN_PATH = "/plugins/";
    private static final String TEST_PLUGIN_NAME = "test-osgi-resource";

    private static final String PATH_SEND_ERROR_404 = "sendError404";
    private static final String PATH_SEND_ERROR_410_WITH_MESSAGE = "sendError410WithMessage";
    private static final String PATH_SEND_REDIRECT = "sendRedirect";
    private static final String PATH_USE_WRITER = "useWriter";
    private static final String PATH_RESET_THEN_WRITE = "resetThenWrite";
    private static final String PATH_REPORT_IS_COMMITTED = "reportIsCommitted";
    private static final String PATH_OUTPUT_STREAM_THEN_WRITER = "outputStreamThenWriter";

    private static final String SEND_ERROR_410_MESSAGE = "gone-from-plugin";
    private static final String WRITER_BODY = "hello-from-writer";
    private static final String RESET_FIRST_BODY = "first-payload-should-be-discarded";
    private static final String RESET_SECOND_BODY = "second-payload-after-reset";
    private static final String REDIRECT_LOCATION = "/plugins/test-osgi-resource/elsewhere";

    private final AtomicReference<Boolean> reportedIsCommitted = new AtomicReference<>(null);
    private final AtomicBoolean outputStreamThenWriterThrew = new AtomicBoolean(false);

    private HttpClient httpClient;
    private String baseUrl;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        setupOSGIPlugin();
        reportedIsCommitted.set(null);
        outputStreamThenWriterThrew.set(false);

        // The KillBillHttpClient helper returns null for >= 400 responses, which
        // would mask the very thing we need to assert (status code on sendError,
        // 302 on sendRedirect, ...). Use the JDK client directly so we can read
        // the raw status code, headers and body for any response.
        httpClient = HttpClient.newBuilder()
                               .followRedirects(HttpClient.Redirect.NEVER)
                               .connectTimeout(Duration.ofSeconds(30))
                               .build();
        final String host = System.getProperty("org.killbill.server.ip", "127.0.0.1");
        final int port = Integer.parseInt(System.getProperty("org.killbill.server.port", "8080"));
        baseUrl = String.format("http://%s:%d", host, port);
    }

    /**
     * Direct reproducer for the original issue: a plugin calling
     * {@code sendError(int)} must NOT commit / close the underlying Jetty
     * response. Before #2205 this surfaced as a server-side
     * {@code EofException: Closed} and the client could see a truncated /
     * inconsistent response. The client must now see a clean 404.
     */
    @Test(groups = "slow")
    public void testSendErrorWithoutMessageIsCaptured() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_SEND_ERROR_404);

        Assert.assertEquals(response.statusCode(), 404,
                            "sendError(404) from a plugin should be reflected as a 404 status to the client");
    }

    /**
     * The {@code sendError(int, String)} variant must also be captured: the
     * status is propagated and the message bytes are written into the
     * buffered body (not flushed to Jetty).
     */
    @Test(groups = "slow")
    public void testSendErrorWithMessageIsCaptured() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_SEND_ERROR_410_WITH_MESSAGE);

        Assert.assertEquals(response.statusCode(), 410);
        Assert.assertEquals(new String(response.body(), StandardCharsets.UTF_8), SEND_ERROR_410_MESSAGE,
                            "sendError message bytes must be returned as the response body");
    }

    /**
     * {@code sendRedirect(String)} must be captured as a 302 + Location
     * header, again without committing the underlying response.
     */
    @Test(groups = "slow")
    public void testSendRedirectIsCaptured() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_SEND_REDIRECT);

        Assert.assertEquals(response.statusCode(), 302);
        // We only assert on the path component of the Location URI: the wrapper
        // forwards the plugin-supplied target verbatim via setHeader(...), but
        // Jetty (and per the Servlet spec, sendRedirect itself) absolutizes the
        // value to "<scheme>://<host>[:<port>]<path>" on the way out. The
        // scheme/host/port are environment-dependent, so we compare just the
        // path to keep the assertion robust.
        final String location = response.headers().firstValue("Location").orElse(null);
        Assert.assertNotNull(location, "Location header must be set on the redirect");
        Assert.assertEquals(URI.create(location).getPath(), REDIRECT_LOCATION,
                            "The plugin-supplied redirect target path must survive the wrapper");
    }

    /**
     * Plugins writing through {@code getWriter()} (rather than
     * {@code getOutputStream()}) must have their characters buffered too.
     * {@code flushBuffer()} must be a no-op on the underlying response.
     */
    @Test(groups = "slow")
    public void testGetWriterAndFlushBufferAreCaptured() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_USE_WRITER);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(new String(response.body(), StandardCharsets.UTF_8), WRITER_BODY);
    }

    /**
     * {@code reset()} must clear the buffered body and any previously set
     * status / headers, allowing the plugin to write a brand new response.
     * Because nothing is ever committed, this must succeed at any point in
     * the plugin's execution (no {@code IllegalStateException}).
     */
    @Test(groups = "slow")
    public void testResetDiscardsPreviousBody() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_RESET_THEN_WRITE);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertEquals(new String(response.body(), StandardCharsets.UTF_8), RESET_SECOND_BODY,
                            "Body written before reset() must be discarded; only the post-reset body should be visible");
    }

    /**
     * From the plugin's point of view the response is never committed, so
     * {@code isCommitted()} must always return {@code false}. This is what
     * lets a plugin safely call {@code reset()} after partial writes.
     */
    @Test(groups = "slow")
    public void testIsCommittedAlwaysReportsFalse() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_REPORT_IS_COMMITTED);

        Assert.assertEquals(response.statusCode(), 200);
        Assert.assertNotNull(reportedIsCommitted.get(),
                             "The test plugin should have observed isCommitted() at least once");
        Assert.assertFalse(reportedIsCommitted.get(),
                           "isCommitted() must always return false from the plugin's perspective");
    }

    /**
     * Per the Servlet spec, {@code getOutputStream()} and {@code getWriter()}
     * are mutually exclusive on the same response. The buffering wrapper must
     * preserve that contract.
     */
    @Test(groups = "slow")
    public void testGetOutputStreamThenGetWriterThrows() throws Exception {
        final HttpResponse<byte[]> response = doGet(TEST_PLUGIN_NAME + "/" + PATH_OUTPUT_STREAM_THEN_WRITER);

        // Whatever status the plugin returns is fine; what we care about is
        // that the wrapper threw IllegalStateException when getWriter() was
        // called after getOutputStream().
        Assert.assertNotNull(response);
        Assert.assertTrue(outputStreamThenWriterThrew.get(),
                          "Calling getWriter() after getOutputStream() must throw IllegalStateException");
    }

    /**
     * Issues a GET against {@code /plugins/<pluginUri>} using the JDK's
     * {@link HttpClient} rather than the {@code pluginGET(...)} helper used
     * by {@link TestPlugin}.
     *
     * <p>Why not reuse {@code pluginGET} / {@code KillBillHttpClient}?
     * {@code KillBillHttpClient.doGet} (and the other verb helpers) returns
     * {@code null} for any response with status &gt;= 400 &mdash; see
     * {@code TestPlugin.testAndResetAllMarkers}, line&nbsp;~151, which asserts
     * {@code Assert.assertNull(response)} for 404/204. That short-circuit
     * would mask exactly what these tests need to verify (the 404 status
     * and body of {@code sendError(...)}, the 302 status and {@code Location}
     * header of {@code sendRedirect(...)}, etc.). The JDK client lets us
     * read the raw status code, headers and body for any response. We also
     * configure {@link HttpClient.Redirect#NEVER} so the 302 from
     * {@code sendRedirect} is observable instead of being followed.
     */
    private HttpResponse<byte[]> doGet(final String pluginUri) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                                               .uri(URI.create(baseUrl + PLUGIN_PATH + pluginUri))
                                               .timeout(Duration.ofSeconds(30))
                                               .GET()
                                               .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * Registers a dedicated test OSGI plugin that exposes one path per
     * wrapper-contract scenario.
     *
     * <p>Why a dedicated plugin (and not extend the one used by
     * {@link TestPlugin})? The plugin in {@link TestPlugin} is registered
     * under its own name and is shaped around HTTP-method markers.
     * Registering under a different name ({@value #TEST_PLUGIN_NAME}) keeps
     * these wrapper-contract paths isolated, avoids any chance of a path
     * collision with {@code TestPlugin.testPassRequestsToKnownPluginButWrongPath}
     * (which relies on unknown paths returning 200 with empty body), and
     * means adding new #2205 scenarios here will never accidentally change
     * the behavior of the routing tests.
     */
    private void setupOSGIPlugin() {
        ((DefaultServletRouter) servletRouter).registerServiceFromPath(TEST_PLUGIN_NAME, new HttpServlet() {
            @Override
            protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                final String pathInfo = req.getPathInfo();
                if (("/" + PATH_SEND_ERROR_404).equals(pathInfo)) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                } else if (("/" + PATH_SEND_ERROR_410_WITH_MESSAGE).equals(pathInfo)) {
                    resp.sendError(HttpServletResponse.SC_GONE, SEND_ERROR_410_MESSAGE);
                } else if (("/" + PATH_SEND_REDIRECT).equals(pathInfo)) {
                    resp.sendRedirect(REDIRECT_LOCATION);
                } else if (("/" + PATH_USE_WRITER).equals(pathInfo)) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    resp.setContentType("text/plain");
                    resp.getWriter().print(WRITER_BODY);
                    // Must be a safe no-op on the underlying Jetty response.
                    resp.flushBuffer();
                } else if (("/" + PATH_RESET_THEN_WRITE).equals(pathInfo)) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.setHeader("X-Should-Be-Cleared", "yes");
                    resp.getOutputStream().write(RESET_FIRST_BODY.getBytes(StandardCharsets.UTF_8));
                    // Wrapper reports isCommitted() == false, so reset() must
                    // succeed and discard everything written above.
                    resp.reset();
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getOutputStream().write(RESET_SECOND_BODY.getBytes(StandardCharsets.UTF_8));
                } else if (("/" + PATH_REPORT_IS_COMMITTED).equals(pathInfo)) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getOutputStream().write("payload".getBytes(StandardCharsets.UTF_8));
                    resp.flushBuffer();
                    // Capture the value the plugin observes for the test to
                    // assert on (we cannot easily round-trip a boolean header
                    // through the wrapper here without coupling the test to
                    // header propagation details).
                    reportedIsCommitted.set(resp.isCommitted());
                } else if (("/" + PATH_OUTPUT_STREAM_THEN_WRITER).equals(pathInfo)) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getOutputStream();
                    try {
                        resp.getWriter();
                    } catch (final IllegalStateException expected) {
                        outputStreamThenWriterThrew.set(true);
                    }
                } else {
                    // Anything else: 200 with empty body, matching the
                    // permissive default in TestPlugin.
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
            }
        });
    }
}
