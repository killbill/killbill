/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.util.security;

import org.apache.shiro.aop.AnnotationResolver;
import org.apache.shiro.aop.MethodInvocation;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.aop.AuthorizingAnnotationHandler;
import org.apache.shiro.authz.aop.AuthorizingAnnotationMethodInterceptor;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionAnnotationMethodInterceptor extends AuthorizingAnnotationMethodInterceptor {

    private static final String SKIP_AUTH_FOR_PLUGINS = "org.killbill.security.skipAuthForPlugins";
    private static final Logger logger = LoggerFactory.getLogger(PermissionAnnotationMethodInterceptor.class);

    private final KillbillConfigSource killbillConfigSource;

    public PermissionAnnotationMethodInterceptor(final KillbillConfigSource killbillConfigSource,
                                                 final AuthorizingAnnotationHandler handler,
                                                 final AnnotationResolver resolver) {
        super(handler, resolver);
        this.killbillConfigSource = killbillConfigSource;
    }

    @Override
    public void assertAuthorized(final MethodInvocation mi) throws AuthorizationException {
        if (shouldSkipAuthForPlugins(mi)) {
            return;
        }

        try {
            ((AuthorizingAnnotationHandler) getHandler()).assertAuthorized(getAnnotation(mi));
        } catch (final AuthorizationException ae) {
            // Annotation handler doesn't know why it was called, so add the information here if possible.
            // Don't wrap the exception here since we don't want to mask the specific exception, such as
            // UnauthenticatedException etc.
            if (ae.getCause() == null) {
                ae.initCause(new AuthorizationException("Not authorized to invoke method: " + mi.getMethod()));
            }
            throw ae;
        }
    }

    private boolean shouldSkipAuthForPlugins(final MethodInvocation mi) {
        if (!Boolean.parseBoolean(killbillConfigSource.getString(SKIP_AUTH_FOR_PLUGINS))) {
            return false;
        }

        final Object[] arguments = mi.getArguments();
        // Should be the last one
        for (int i = arguments.length - 1; i >= 0; i--) {
            final Object argument = arguments[i];
            if (argument instanceof CallContext) {
                final CallContext callContext = (CallContext) argument;
                if (callContext.getCallOrigin() == CallOrigin.INTERNAL && callContext.getUserType() == UserType.ADMIN) {
                    logger.debug("Skipping authorization check for userName={}, userToken={}", callContext.getUserName(), callContext.getUserToken());
                    return true;
                }
            }
        }
        return false;
    }
}
