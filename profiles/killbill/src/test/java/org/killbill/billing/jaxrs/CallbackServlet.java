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

package org.killbill.billing.jaxrs;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.killbill.billing.jaxrs.json.NotificationJson;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class CallbackServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(CallbackServlet.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Joiner SPACE_JOINER = Joiner.on(" ");
    private static final long DELAY = 60000;

    // Cross tenants (for now)
    private final Collection<ExtBusEventType> nextExpectedEvent = new Stack<ExtBusEventType>();

    private boolean isListenerFailed = false;
    private String listenerFailedMsg;
    private boolean completed = true;

    final AtomicInteger receivedCalls = new AtomicInteger(0);
    final AtomicBoolean forceToFail = new AtomicBoolean(false);

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final int current = receivedCalls.incrementAndGet();
        if (forceToFail.get()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            log.info("CallmebackServlet is forced to fail for testing purposes");
            return;
        }

        final String body = CharStreams.toString(new InputStreamReader(request.getInputStream(), "UTF-8"));
        response.setStatus(HttpServletResponse.SC_OK);

        final NotificationJson notification = objectMapper.readValue(body, NotificationJson.class);
        log.info("Got notification: {}", notification);
        assertEqualsNicely(notification.getEventType() == null ? null : ExtBusEventType.valueOf(notification.getEventType()));
        notifyIfStackEmpty();
    }

    public void assertListenerStatus() {
        // Bail early
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }

        try {
            assertTrue(isCompleted(DELAY));
        } catch (final Exception e) {
            fail("assertListenerStatus didn't complete", e);
        }

        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }

    public synchronized void reset() {
        receivedCalls.set(0);
        forceToFail.set(false);
        nextExpectedEvent.clear();
        completed = true;

        isListenerFailed = false;
        listenerFailedMsg = null;
    }

    public void pushExpectedEvents(final ExtBusEventType... events) {
        for (final ExtBusEventType event : events) {
            pushExpectedEvent(event);
        }
    }

    public synchronized void pushExpectedEvent(final ExtBusEventType next) {
        nextExpectedEvent.add(next);
        log.info("Stacking expected event {}, got [{}]", next, SPACE_JOINER.join(nextExpectedEvent));
        completed = false;
    }

    private synchronized boolean isCompleted(final long timeout) {
        long waitTimeMs = timeout;
        do {
            try {
                final long before = System.currentTimeMillis();
                wait(100);
                final long after = System.currentTimeMillis();
                waitTimeMs -= (after - before);
            } catch (final Exception ignore) {
                return false;
            }
        } while (waitTimeMs > 0 && !completed);

        if (!completed) {
            log.error("CallbackServlet did not complete in " + timeout + " ms, remaining events are " + SPACE_JOINER.join(nextExpectedEvent));
        }
        return completed;
    }

    private synchronized void notifyIfStackEmpty() {
        if (nextExpectedEvent.isEmpty()) {
            log.debug("CallbackServlet EMPTY");
            completed = true;
            notify();
        }
    }

    private synchronized void assertEqualsNicely(final ExtBusEventType received) {
        boolean foundIt = false;
        final Iterator<ExtBusEventType> it = nextExpectedEvent.iterator();
        while (it.hasNext()) {
            final ExtBusEventType ev = it.next();
            if (ev == received) {
                it.remove();
                foundIt = true;
                log.info("Found expected event: {}; remaining expected events [{}]", received, SPACE_JOINER.join(nextExpectedEvent));
                break;
            }
        }
        if (!foundIt) {
            final String errorMsg = "CallbackServlet: received unexpected event " + received + "; remaining expected events [" + SPACE_JOINER.join(nextExpectedEvent) + "]";
            log.error(errorMsg);
            failed(errorMsg);
        }
    }

    private void failed(final String msg) {
        this.isListenerFailed = true;
        this.listenerFailedMsg = msg;
    }
}
