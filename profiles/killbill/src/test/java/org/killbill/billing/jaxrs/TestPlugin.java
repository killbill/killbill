/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.osgi.http.DefaultServletRouter;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.http.client.Response;

public class TestPlugin extends TestJaxrsBase {

    private static final String PLUGIN_PATH = "/plugins/";

    private static final String TEST_PLUGIN_NAME = "test-osgi";

    private static final byte[] TEST_PLUGIN_RESPONSE_BYTES = new byte[]{0xC, 0x0, 0xF, 0xF, 0xE, 0xE};

    private static final String TEST_PLUGIN_VALID_GET_PATH = "setGETMarkerToTrue";
    private static final String TEST_PLUGIN_VALID_HEAD_PATH = "setHEADMarkerToTrue";
    private static final String TEST_PLUGIN_VALID_POST_PATH = "setPOSTMarkerToTrue";
    private static final String TEST_PLUGIN_VALID_PUT_PATH = "setPUTMarkerToTrue";
    private static final String TEST_PLUGIN_VALID_DELETE_PATH = "setDELETEMarkerToTrue";
    private static final String TEST_PLUGIN_VALID_OPTIONS_PATH = "setOPTIONSMarkerToTrue";

    private final AtomicBoolean requestGETMarker = new AtomicBoolean(false);
    private final AtomicBoolean requestHEADMarker = new AtomicBoolean(false);
    private final AtomicBoolean requestPOSTMarker = new AtomicBoolean(false);
    private final AtomicBoolean requestPUTMarker = new AtomicBoolean(false);
    private final AtomicBoolean requestDELETEMarker = new AtomicBoolean(false);
    private final AtomicBoolean requestOPTIONSMarker = new AtomicBoolean(false);

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        setupOSGIPlugin();
        resetAllMarkers();
    }

    @Test(groups = "slow")
    public void testPassRequestsToUnknownPlugin() throws Exception {
        final String uri = "pluginDoesNotExist/something";
        Response response;

        // We don't test the output here as it is some Jetty specific HTML blurb

        response = pluginGET(uri, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);

        response = pluginHEAD(uri, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);

        response = pluginPOST(uri, null, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);

        response = pluginPUT(uri, null, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);

        response = pluginDELETE(uri, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);

        response = pluginOPTIONS(uri, requestOptions);
        testAndResetAllMarkers(response, 404, null, false, false, false, false, false, false);
    }

    @Test(groups = "slow")
    public void testPassRequestsToKnownPluginButWrongPath() throws Exception {
        final String uri = TEST_PLUGIN_NAME + "/somethingSomething";
        Response response;

        response = pluginGET(uri, requestOptions);
        testAndResetAllMarkers(response, 200, new byte[]{}, false, false, false, false, false, false);

        response = pluginHEAD(uri, requestOptions);
        testAndResetAllMarkers(response, 204, new byte[]{}, false, false, false, false, false, false);

        response = pluginPOST(uri, null, requestOptions);
        testAndResetAllMarkers(response, 200, new byte[]{}, false, false, false, false, false, false);

        response = pluginPUT(uri, null, requestOptions);
        testAndResetAllMarkers(response, 200, new byte[]{}, false, false, false, false, false, false);

        response = pluginDELETE(uri, requestOptions);
        testAndResetAllMarkers(response, 200, new byte[]{}, false, false, false, false, false, false);

        response = pluginOPTIONS(uri, requestOptions);
        testAndResetAllMarkers(response, 200, new byte[]{}, false, false, false, false, false, false);
    }

    @Test(groups = "slow")
    public void testPassRequestsToKnownPluginAndKnownPath() throws Exception {
        Response response;

        response = pluginGET(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_GET_PATH, requestOptions);
        testAndResetAllMarkers(response, 230, TEST_PLUGIN_RESPONSE_BYTES, true, false, false, false, false, false);

        response = pluginHEAD(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_HEAD_PATH, requestOptions);
        testAndResetAllMarkers(response, 204, new byte[]{}, false, true, false, false, false, false);

        response = pluginPOST(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_POST_PATH, null, requestOptions);
        testAndResetAllMarkers(response, 230, TEST_PLUGIN_RESPONSE_BYTES, false, false, true, false, false, false);

        response = pluginPUT(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_PUT_PATH, null, requestOptions);
        testAndResetAllMarkers(response, 230, TEST_PLUGIN_RESPONSE_BYTES, false, false, false, true, false, false);

        response = pluginDELETE(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_DELETE_PATH, requestOptions);
        testAndResetAllMarkers(response, 230, TEST_PLUGIN_RESPONSE_BYTES, false, false, false, false, true, false);

        response = pluginOPTIONS(TEST_PLUGIN_NAME + "/" + TEST_PLUGIN_VALID_OPTIONS_PATH, requestOptions);
        testAndResetAllMarkers(response, 230, TEST_PLUGIN_RESPONSE_BYTES, false, false, false, false, false, true);
    }

    private void testAndResetAllMarkers(@Nullable final Response response, final int responseCode, @Nullable final byte[] responseBytes, final boolean get, final boolean head,
                                        final boolean post, final boolean put, final boolean delete, final boolean options) throws IOException {
        if (responseCode == 404 || responseCode == 204) {
            Assert.assertNull(response);
        } else {
            Assert.assertNotNull(response);
            Assert.assertEquals(response.getStatusCode(), responseCode);
            if (responseBytes != null) {
                Assert.assertEquals(response.getResponseBodyAsBytes(), responseBytes);
            }
        }

        Assert.assertEquals(requestGETMarker.get(), get);
        Assert.assertEquals(requestHEADMarker.get(), head);
        Assert.assertEquals(requestPOSTMarker.get(), post);
        Assert.assertEquals(requestPUTMarker.get(), put);
        Assert.assertEquals(requestDELETEMarker.get(), delete);
        Assert.assertEquals(requestOPTIONSMarker.get(), options);

        resetAllMarkers();
    }

    private void resetAllMarkers() {
        requestGETMarker.set(false);
        requestHEADMarker.set(false);
        requestPOSTMarker.set(false);
        requestPUTMarker.set(false);
        requestDELETEMarker.set(false);
        requestOPTIONSMarker.set(false);
    }

    //
    // Plugin routing endpoints are not officially part of api (yet)
    //
    private Response pluginGET(final String uri, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doGet(PLUGIN_PATH + uri, inputOptions);
    }

    private Response pluginHEAD(final String uri, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doHead(PLUGIN_PATH + uri, inputOptions);
    }

    private Response pluginPOST(final String uri, @Nullable final String body, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doPost(PLUGIN_PATH + uri, body, inputOptions);
    }

    private Response pluginDELETE(final String uri, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doDelete(PLUGIN_PATH + uri, inputOptions);
    }

    private Response pluginPUT(final String uri, @Nullable final String body, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doPut(PLUGIN_PATH + uri, body, inputOptions);
    }

    private Response pluginOPTIONS(final String uri, final RequestOptions inputOptions) throws Exception {
        return killBillHttpClient.doOptions(PLUGIN_PATH + uri, inputOptions);
    }

    private void setupOSGIPlugin() {
        ((DefaultServletRouter) servletRouter).registerServiceFromPath(TEST_PLUGIN_NAME, new HttpServlet() {
            @Override
            protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_GET_PATH).equals(req.getPathInfo())) {
                    requestGETMarker.set(true);
                    resp.getOutputStream().write(TEST_PLUGIN_RESPONSE_BYTES);
                    resp.setStatus(230);
                }
            }

            @Override
            protected void doHead(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_HEAD_PATH).equals(req.getPathInfo())) {
                    requestHEADMarker.set(true);
                }
            }

            @Override
            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_POST_PATH).equals(req.getPathInfo())) {
                    requestPOSTMarker.set(true);
                    resp.getOutputStream().write(TEST_PLUGIN_RESPONSE_BYTES);
                    resp.setStatus(230);
                }
            }

            @Override
            protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_PUT_PATH).equals(req.getPathInfo())) {
                    requestPUTMarker.set(true);
                    resp.getOutputStream().write(TEST_PLUGIN_RESPONSE_BYTES);
                    resp.setStatus(230);
                }
            }

            @Override
            protected void doDelete(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_DELETE_PATH).equals(req.getPathInfo())) {
                    requestDELETEMarker.set(true);
                    resp.getOutputStream().write(TEST_PLUGIN_RESPONSE_BYTES);
                    resp.setStatus(230);
                }
            }

            @Override
            protected void doOptions(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
                if (("/" + TEST_PLUGIN_VALID_OPTIONS_PATH).equals(req.getPathInfo())) {
                    requestOPTIONSMarker.set(true);
                    resp.getOutputStream().write(TEST_PLUGIN_RESPONSE_BYTES);
                    resp.setStatus(230);
                }
            }
        });
    }
}
