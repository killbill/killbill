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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

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

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;

@Singleton
@Path(JaxrsResource.PLUGINS_PATH + "{subResources:.*}")
@Hidden
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
    public Response doDELETE(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                             @jakarta.ws.rs.core.Context final HttpServletResponse response,
                             @jakarta.ws.rs.core.Context final ServletContext servletContext,
                             @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                             @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @GET
    public Response doGET(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                          @jakarta.ws.rs.core.Context final HttpServletResponse response,
                          @jakarta.ws.rs.core.Context final ServletContext servletContext,
                          @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                          @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @OPTIONS
    public Response doOPTIONS(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                              @jakarta.ws.rs.core.Context final HttpServletResponse response,
                              @jakarta.ws.rs.core.Context final ServletContext servletContext,
                              @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                              @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response doFormPOST(final MultivaluedMap<String, String> form,
                               @jakarta.ws.rs.core.Context final HttpServletRequest request,
                               @jakarta.ws.rs.core.Context final HttpServletResponse response,
                               @jakarta.ws.rs.core.Context final ServletContext servletContext,
                               @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                               @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(form, request, response, servletContext, servletConfig, uriInfo);
    }

    @POST
    public Response doPOST(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                           @jakarta.ws.rs.core.Context final HttpServletResponse response,
                           @jakarta.ws.rs.core.Context final ServletContext servletContext,
                           @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                           @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @PUT
    public Response doPUT(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                          @jakarta.ws.rs.core.Context final HttpServletResponse response,
                          @jakarta.ws.rs.core.Context final ServletContext servletContext,
                          @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                          @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig, uriInfo);
    }

    @HEAD
    public Response doHEAD(@jakarta.ws.rs.core.Context final HttpServletRequest request,
                           @jakarta.ws.rs.core.Context final HttpServletResponse response,
                           @jakarta.ws.rs.core.Context final ServletContext servletContext,
                           @jakarta.ws.rs.core.Context final ServletConfig servletConfig,
                           @jakarta.ws.rs.core.Context final UriInfo uriInfo) throws ServletException, IOException {
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
        // The wrapper fully buffers the response (output stream, writer, sendError, sendRedirect,
        // flushBuffer, reset, ...) so no servlet API call by a plugin can commit / close the
        // underlying Jetty response before PluginResource hands the result back to Jersey.
        // See https://github.com/killbill/killbill/issues/2205
        final OSGIServletResponseWrapper res = new OSGIServletResponseWrapper(response);

        osgiServlet.service(req, res);

        if (response.getStatus() >= 400) {
            log.warn("{} responded {}", request.getPathInfo(), response.getStatus());
        }

        final Response.ResponseBuilder builder  = Response.status(response.getStatus())
                .entity(res.getCapturedBytes());
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

            out.write((entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), UTF_8)).getBytes(UTF_8));
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

        // Fully buffer the response. None of the servlet APIs below ever touch the underlying
        // Jetty response body / commit state; only headers and status are propagated via the
        // HttpServletResponseWrapper super-calls (which do not commit). PluginResource reads the
        // captured bytes after the OSGI servlet returns and hands them to Jersey.
        // See https://github.com/killbill/killbill/issues/2205
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final ServletOutputStream servletOutputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(final WriteListener writeListener) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(final int b) {
                buffer.write(b);
            }

            @Override
            public void write(final byte[] b, final int off, final int len) {
                buffer.write(b, off, len);
            }
        };

        // Lazily created on getWriter(); mutually exclusive with direct getOutputStream() use,
        // per the Servlet spec.
        private PrintWriter printWriter;
        private boolean outputStreamRequested;

        public OSGIServletResponseWrapper(final HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (printWriter != null) {
                throw new IllegalStateException("getWriter() has already been called on this response");
            }
            outputStreamRequested = true;
            return servletOutputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputStreamRequested) {
                throw new IllegalStateException("getOutputStream() has already been called on this response");
            }
            if (printWriter == null) {
                final String encoding = getCharacterEncoding();
                // Servlet specs default response charset.
                // https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0.pdf~55
                Charset charset = StandardCharsets.ISO_8859_1;
                if (encoding != null) {
                    try {
                        charset = Charset.forName(encoding);
                    } catch (final IllegalArgumentException e) {
                        // IllegalCharsetNameException / UnsupportedCharsetException — fall back to
                        // the servlet default rather than failing the plugin call.
                    }
                }
                printWriter = new PrintWriter(new OutputStreamWriter(buffer, charset), false);
            }
            return printWriter;
        }

        @Override
        public void sendError(final int sc) throws IOException {
            setStatus(sc);
        }

        @Override
        public void sendError(final int sc, final String msg) throws IOException {
            setStatus(sc);
            if (msg != null) {
                buffer.write(msg.getBytes(UTF_8));
            }
        }

        // We deliberately do not delegate to super.sendRedirect(...): the default HttpServletResponseWrapper
        // implementation forwards to the underlying HttpServletResponse, which commits and closes the response,
        // and exactly the root cause of https://github.com/killbill/killbill/issues/2205. Instead, we stage the 302
        // status and the Location header on the wrapped response so PluginResource can hand a well-formed JAX-RS
        // Response back to Jersey.
        //
        // The plugin-supplied target is preserved verbatim. It MAY be normalized to an absolute URL
        // ("scheme://host[:port]<path>") by Jersey or the host servlet container before it reaches the wire. Either
        // form is legal per RFC 7231 and accepted by modern HTTP clients.
        @Override
        public void sendRedirect(final String location) throws IOException {
            setStatus(HttpServletResponse.SC_FOUND);
            setHeader("Location", location);
        }

        @Override
        public void flushBuffer() throws IOException {
            // Flush our captured writer (if any) into the byte buffer, but never touch the
            // underlying Jetty response — that is what would commit / close the channel.
            if (printWriter != null) {
                printWriter.flush();
            }
        }

        @Override
        public void resetBuffer() {
            if (printWriter != null) {
                // Discard any buffered chars in the PrintWriter.
                printWriter.flush();
            }
            buffer.reset();
        }

        @Override
        public void reset() {
            // Reset status and headers on the wrapped response (it is never committed by us, so
            // this is safe and does not throw IllegalStateException), then clear our buffer.
            super.reset();
            resetBuffer();
        }

        @Override
        public boolean isCommitted() {
            // We buffer everything; the response is never committed from the plugin's point of view.
            return false;
        }

        @Override
        public int getBufferSize() {
            // Our backing ByteArrayOutputStream grows as needed and is never auto-flushed to the
            // underlying response, so report an effectively-unbounded buffer. This signals to
            // plugins that they do not need to call flushBuffer() to avoid hitting a limit.
            return Integer.MAX_VALUE;
        }

        @Override
        public void setBufferSize(final int size) {
            // No-op: our buffer grows as needed and is never auto-flushed to the underlying
            // response.
        }

        /**
         * @return the bytes captured during the OSGI servlet invocation; flushes the lazily
         *         created PrintWriter (if any) so that any chars written via {@link #getWriter()}
         *         are included.
         */
        byte[] getCapturedBytes() {
            if (printWriter != null) {
                printWriter.flush();
            }
            return buffer.toByteArray();
        }
    }
}
