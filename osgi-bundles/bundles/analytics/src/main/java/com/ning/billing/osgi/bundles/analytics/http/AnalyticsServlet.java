/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.osgi.bundles.analytics.http;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.osgi.bundles.analytics.AnalyticsRefreshException;
import com.ning.billing.osgi.bundles.analytics.api.BusinessSnapshot;
import com.ning.billing.osgi.bundles.analytics.api.user.AnalyticsUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Objects;

public class AnalyticsServlet extends HttpServlet {

    private static final String QUERY_TENANT_ID = "tenantId";
    private static final String HDR_CREATED_BY = "X-Killbill-CreatedBy";
    private static final String HDR_REASON = "X-Killbill-Reason";
    private static final String HDR_COMMENT = "X-Killbill-Comment";

    private static final ObjectMapper mapper = ObjectMapperProvider.get();

    private final AnalyticsUserApi analyticsUserApi;

    public AnalyticsServlet(final AnalyticsUserApi analyticsUserApi) {
        this.analyticsUserApi = analyticsUserApi;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final UUID kbAccountId = getKbAccountId(req, resp);
        final CallContext context = createCallContext(req, resp);

        final BusinessSnapshot businessSnapshot = analyticsUserApi.getBusinessSnapshot(kbAccountId, context);
        resp.getOutputStream().write(mapper.writeValueAsBytes(businessSnapshot));
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPut(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final UUID kbAccountId = getKbAccountId(req, resp);
        final CallContext context = createCallContext(req, resp);

        try {
            analyticsUserApi.rebuildAnalyticsForAccount(kbAccountId, context);
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (AnalyticsRefreshException e) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private CallContext createCallContext(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final String createdBy = Objects.firstNonNull(req.getHeader(HDR_CREATED_BY), req.getRemoteAddr());
        final String reason = req.getHeader(HDR_REASON);
        final String comment = Objects.firstNonNull(req.getHeader(HDR_COMMENT), req.getRequestURI());

        final String tenantIdString = req.getParameter(QUERY_TENANT_ID);
        if (tenantIdString == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing tenantId query parameter in request: " + req.getPathInfo());
            return null;
        }

        final UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantIdString);
        } catch (final IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid UUID for tenant id: " + tenantIdString);
            return null;
        }

        return new AnalyticsApiCallContext(createdBy, reason, comment, tenantId);
    }

    private UUID getKbAccountId(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final String kbAccountIdString;
        try {
            kbAccountIdString = req.getPathInfo().substring(1, req.getPathInfo().length());
        } catch (final StringIndexOutOfBoundsException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Badly formed kb account id in request: " + req.getPathInfo());
            return null;
        }

        final UUID kbAccountId;
        try {
            kbAccountId = UUID.fromString(kbAccountIdString);
        } catch (final IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid UUID for kb account id: " + kbAccountIdString);
            return null;
        }

        return kbAccountId;
    }

    private static final class AnalyticsApiCallContext implements CallContext {

        private final String createdBy;
        private final String reason;
        private final String comment;
        private final UUID tenantId;
        private final DateTime now;

        private AnalyticsApiCallContext(final String createdBy,
                                        final String reason,
                                        final String comment,
                                        final UUID tenantId) {
            this.createdBy = createdBy;
            this.reason = reason;
            this.comment = comment;
            this.tenantId = tenantId;

            this.now = new DateTime(DateTimeZone.UTC);
        }

        @Override
        public UUID getUserToken() {
            return UUID.randomUUID();
        }

        @Override
        public String getUserName() {
            return createdBy;
        }

        @Override
        public CallOrigin getCallOrigin() {
            return CallOrigin.EXTERNAL;
        }

        @Override
        public UserType getUserType() {
            return UserType.ADMIN;
        }

        @Override
        public String getReasonCode() {
            return reason;
        }

        @Override
        public String getComments() {
            return comment;
        }

        @Override
        public DateTime getCreatedDate() {
            return now;
        }

        @Override
        public DateTime getUpdatedDate() {
            return now;
        }

        @Override
        public UUID getTenantId() {
            return tenantId;
        }
    }
}
