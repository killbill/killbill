/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ReadListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.jaxrs.util.Context;
import org.killbill.billing.jaxrs.util.JaxrsUriBuilder;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.commons.utils.collect.MultiValueHashMap;
import org.killbill.commons.utils.collect.MultiValueMap;
import org.killbill.clock.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;

@Singleton
@Path(JaxrsResource.PLUGINS_PATH + "{subResources:.*}")
@Api(value = JaxrsResource.PLUGINS_PATH + "{subResources:.*}", description = "Plugins servlets", hidden = true)
public class PluginResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(PluginResource.class);
    private static final Charset UTF_8 = StandardCharsets.UTF_8;

    private final HttpServlet osgiServlet;

    @Inject
    public PluginResource(@Named("osgi") final HttpServlet osgiServlet, // See DefaultOSGIModule.OSGI_NAMED
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final AccountUserApi accountUserApi,
                          final PaymentApi paymentApi,
                          final InvoicePaymentApi invoicePaymentApi,
                          final Clock clock,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, accountUserApi, paymentApi, invoicePaymentApi, null, clock, context);
        this.osgiServlet = osgiServlet;
    }

    @DELETE
    public Response doDELETE(@javax.ws.rs.core.Context final HttpServletRequest request,
                             @javax.ws.rs.core.Context final HttpServletResponse response,
                             @javax.ws.rs.core.Context final ServletContext servletContext,
                             @javax.ws.rs.core.Context final ServletConfig servletConfig,
                             @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @GET
    public Response doGET(@javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response,
                          @javax.ws.rs.core.Context final ServletContext servletContext,
                          @javax.ws.rs.core.Context final ServletConfig servletConfig,
                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @OPTIONS
    public Response doOPTIONS(@javax.ws.rs.core.Context final HttpServletRequest request,
                              @javax.ws.rs.core.Context final HttpServletResponse response,
                              @javax.ws.rs.core.Context final ServletContext servletContext,
                              @javax.ws.rs.core.Context final ServletConfig servletConfig,
                              @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response doFormPOST(final MultivaluedMap<String, String> form,
                               @javax.ws.rs.core.Context final HttpServletRequest request,
                               @javax.ws.rs.core.Context final HttpServletResponse response,
                               @javax.ws.rs.core.Context final ServletContext servletContext,
                               @javax.ws.rs.core.Context final ServletConfig servletConfig,
                               @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(form, request, response, servletContext, servletConfig, uriInfo);
    }

    @POST
    public Response doPOST(@javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response,
                           @javax.ws.rs.core.Context final ServletContext servletContext,
                           @javax.ws.rs.core.Context final ServletConfig servletConfig,
                           @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @PUT
    public Response doPUT(@javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response,
                          @javax.ws.rs.core.Context final ServletContext servletContext,
                          @javax.ws.rs.core.Context final ServletConfig servletConfig,
                          @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @HEAD
    public Response doHEAD(@javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response,
                           @javax.ws.rs.core.Context final ServletContext servletContext,
                           @javax.ws.rs.core.Context final ServletConfig servletConfig,
                           @javax.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);

        // Make sure to return 204
        return Response.noContent().build();
    }

    private Response serviceViaOSGIPlugin(final HttpServletRequest request, final HttpServletResponse response,
                                          final ServletContext servletContext, final ServletConfig servletConfig,
                                          final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, request.getInputStream(), null, response, servletContext, servletConfig, uriInfo);
    }

    private Response serviceViaOSGIPlugin(final MultivaluedMap<String, String> form,
                                          final HttpServletRequest request, final HttpServletResponse response,
                                          final ServletContext servletContext, final ServletConfig servletConfig,
                                          final UriInfo uriInfo) throws ServletException, IOException {
        // form will contain form parameters, if any. Even if the request contains such parameters, it may be empty
        // if a filter (e.g. Shiro) has already consumed them (see kludge below)
        return serviceViaOSGIPlugin(request, createInputStream(request, form), form, response, servletContext, servletConfig, uriInfo);
    }

    private Response serviceViaOSGIPlugin(final HttpServletRequest request, final InputStream inputStream, @Nullable final MultivaluedMap<String, String> formData,
                                          final HttpServletResponse response, final ServletContext servletContext,
                                          final ServletConfig servletConfig, final UriInfo uriInfo) throws ServletException, IOException {
        prepareOSGIRequest(request, servletContext, servletConfig);

        final ServletRequest req = new OSGIServletRequestWrapper(request, inputStream, formData, uriInfo.getQueryParameters());

        // The real ServletOutputStream is a HttpOutput, which we don't want to give to plugins.
        // Jooby for instance would commit the underlying HTTP channel (via ServletServletResponse#send),
        // meaning that any further headers (e.g. Profiling) that we would add would not be returned.
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final OSGIServletResponseWrapper res = new OSGIServletResponseWrapper(response,
                                                                              new ServletOutputStream() {
                                                                                  @Override
                                                                                  public boolean isReady() {
                                                                                      return true;
                                                                                  }

                                                                                  @Override
                                                                                  public void setWriteListener(final WriteListener writeListener) {
                                                                                      throw new UnsupportedOperationException();
                                                                                  }

                                                                                  @Override
                                                                                  public void write(final int b) throws IOException {
                                                                                      byteArrayOutputStream.write(b);
                                                                                  }
                                                                              });

        osgiServlet.service(req, res);

        if (response.getStatus() >= 400) {
            log.warn("{} responded {}", request.getPathInfo(), response.getStatus());
        }

        final Response.ResponseBuilder builder  = Response.status(response.getStatus())
                .entity(byteArrayOutputStream.toByteArray());
        for (final String name : response.getHeaderNames()) {
            for (final String value : response.getHeaders(name)) {
                builder.header(name, value);
            }
        }
        if (response.getContentType() != null) {
            builder.type(response.getContentType());
        }
        return builder.build();
    }

    private InputStream createInputStream(final HttpServletRequest request, final MultivaluedMap<String, String> form) throws IOException {
        // /!\ Kludge alert (pierre) /!\
        // This is awful... But because of various servlet filters we have in place, include Shiro,
        // the request parameters and/or body at this point have already been looked at.
        // We can't use @FormParam in PluginResource because we don't know the form parameter names
        // in advance.
        // So... We just stick them back in :-)
        // TODO Support application/x-www-form-urlencoded vs multipart/form-data
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final Map<String, String> data = new HashMap<String, String>();
        for (final String key : request.getParameterMap().keySet()) {
            data.put(key, request.getParameter(key));
        }
        for (final String key : form.keySet()) {
            data.put(key, form.getFirst(key));
        }
        appendFormParametersToBody(out, data);
        // changes related https://github.com/killbill/killbill/issues/1975
        // request.getInputStream().transferTo(out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void appendFormParametersToBody(final ByteArrayOutputStream out, final Map<String, String> data) throws IOException {
        int idx = 0;
        for (final Entry<String, String> entry : data.entrySet()) {
            if (idx > 0) {
                out.write("&".getBytes(UTF_8));
            }

            out.write((entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).getBytes(UTF_8));
            idx++;
        }
    }

    private void prepareOSGIRequest(final HttpServletRequest request, final ServletContext servletContext, final ServletConfig servletConfig) {
        request.setAttribute("killbill.osgi.servletContext", servletContext);
        request.setAttribute("killbill.osgi.servletConfig", servletConfig);
    }

    // Request wrapper to hide the /plugins prefix to OSGI bundles and fiddle with the input stream
    private static final class OSGIServletRequestWrapper extends HttpServletRequestWrapper {

        private final InputStream inputStream;
        private final Map<String, String[]> parameterMap;

        public OSGIServletRequestWrapper(final HttpServletRequest request, final InputStream inputStream, @Nullable final MultivaluedMap<String, String> formData, final MultivaluedMap<String, String> queryParameters) {
            super(request);
            this.inputStream = inputStream;
            this.parameterMap = new HashMap<String, String[]>();

            // Query string parameters and posted form data must appear in the parameters
            final MultiValueMap<String, String> tmpParameterMap = new MultiValueHashMap<>();
            if (formData != null) {
                tmpParameterMap.putAll(formData);
            }
            tmpParameterMap.putAll(queryParameters);
            for (final Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                tmpParameterMap.put(entry.getKey(), List.of(entry.getValue()));
            }

            for (final Entry<String, List<String>> entry : tmpParameterMap.entrySet()) {
                parameterMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            }
        }

        @Override
        public String getParameter(final String name) {
            final String[] values = parameterMap.get(name);
            if (values == null || values.length == 0) {
                return null;
            } else {
                return values[0];
            }
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.<String, String[]>unmodifiableMap(parameterMap);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.<String>enumeration(parameterMap.keySet());
        }

        @Override
        public String[] getParameterValues(final String name) {
            return parameterMap.get(name);
        }

        @Override
        public String getPathInfo() {
            return super.getPathInfo().replaceFirst(JaxrsResource.PLUGINS_PATH, "");
        }

        @Override
        public String getContextPath() {
            return JaxrsResource.PLUGINS_PATH;
        }

        @Override
        public String getServletPath() {
            return super.getServletPath().replaceFirst(JaxrsResource.PLUGINS_PATH, "");
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStreamWrapper(inputStream);
        }
    }

    private static final class ServletInputStreamWrapper extends ServletInputStream {

        private final InputStream inputStream;
        private final AtomicBoolean eof = new AtomicBoolean(false);

        public ServletInputStreamWrapper(final InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public int read() throws IOException {
            final int next = inputStream.read();
            if (next == -1) {
                eof.set(true);
            }
            return next;
        }

        @Override
        public boolean isFinished() {
            return eof.get();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException("setReadListener");
        }
    }

    private static final class OSGIServletResponseWrapper extends HttpServletResponseWrapper {

        private final ServletOutputStream servletOutputStream;

        public OSGIServletResponseWrapper(final HttpServletResponse response, final ServletOutputStream servletOutputStream) {
            super(response);
            this.servletOutputStream = servletOutputStream;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return servletOutputStream;
        }
    }
}
