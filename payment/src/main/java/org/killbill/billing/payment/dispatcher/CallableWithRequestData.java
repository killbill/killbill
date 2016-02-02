/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.dispatcher;

import java.util.Random;
import java.util.concurrent.Callable;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.request.Request;
import org.killbill.commons.request.RequestData;

public class CallableWithRequestData<T> implements Callable<T> {

    private final RequestData requestData;
    private final Random random;
    private final SecurityManager securityManager;
    private final Subject subject;
    private final Callable<T> delegate;

    public CallableWithRequestData(final RequestData requestData, final Random random, final SecurityManager securityManager, final Subject subject, final Callable<T> delegate) {
        this.requestData = requestData;
        this.random = random;
        this.securityManager = securityManager;
        this.subject = subject;
        this.delegate = delegate;
    }

    @Override
    public T call() throws Exception {
        try {
            Request.setPerThreadRequestData(requestData);
            UUIDs.setRandom(random);
            ThreadContext.bind(securityManager);
            ThreadContext.bind(subject);
            return delegate.call();
        } finally {
            Request.resetPerThreadRequestData();
            UUIDs.setRandom(null);
            ThreadContext.unbindSecurityManager();
            ThreadContext.unbindSubject();
        }
    }
}
