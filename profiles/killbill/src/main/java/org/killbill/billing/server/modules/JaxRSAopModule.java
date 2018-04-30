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

package org.killbill.billing.server.modules;

import java.lang.reflect.Method;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.killbill.billing.jaxrs.resources.JaxrsResource;
import org.killbill.billing.util.entity.dao.DBRouterUntyped;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

public class JaxRSAopModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(KillbillApiAopModule.class);

    private static final Matcher<Method> API_RESOURCE_METHOD_MATCHER = new Matcher<Method>() {
        @Override
        public boolean matches(final Method method) {
            return !method.isSynthetic() &&
                   (
                           method.getAnnotation(DELETE.class) != null ||
                           method.getAnnotation(GET.class) != null ||
                           method.getAnnotation(HEAD.class) != null ||
                           method.getAnnotation(OPTIONS.class) != null ||
                           method.getAnnotation(POST.class) != null ||
                           method.getAnnotation(PUT.class) != null
                   );
        }

        @Override
        public Matcher<Method> and(final Matcher<? super Method> other) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Matcher<Method> or(final Matcher<? super Method> other) {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    protected void configure() {
        bindInterceptor(Matchers.subclassesOf(JaxrsResource.class),
                        API_RESOURCE_METHOD_MATCHER,
                        new JaxRsMethodInterceptor());
    }

    public static class JaxRsMethodInterceptor implements MethodInterceptor {

        @Override
        public Object invoke(final MethodInvocation invocation) throws Throwable {
            return DBRouterUntyped.withRODBIAllowed(isRODBIAllowed(invocation),
                                                    new WithProfilingCallback<Object, Throwable>() {
                                                        @Override
                                                        public Object execute() throws Throwable {
                                                            logger.debug("Entering JAX-RS call {}, arguments: {}", invocation.getMethod(), invocation.getArguments());
                                                            final Object proceed = invocation.proceed();
                                                            logger.debug("Exiting  JXA-RS call {}, returning: {}", invocation.getMethod(), proceed);
                                                            return proceed;
                                                        }
                                                    });
        }

        private boolean isRODBIAllowed(final MethodInvocation invocation) {
            return invocation.getMethod().getAnnotation(GET.class) != null;
        }
    }
}
