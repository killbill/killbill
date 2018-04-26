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

package org.killbill.billing.util.glue;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.killbill.billing.KillbillApi;
import org.killbill.billing.callcontext.DefaultTenantContext;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.osgi.api.ROTenantContext;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;

public class KillbillApiAopModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(KillbillApiAopModule.class);
    private static final ThreadLocal<Boolean> perThreadDirtyDBFlag = new ThreadLocal<Boolean>();

    static {
        // Set an initial value
        resetDirtyDBFlag();
    }

    @Override
    protected void configure() {

        bindInterceptor(Matchers.subclassesOf(KillbillApi.class),
                        Matchers.not(SYNTHETIC_METHOD_MATCHER),
                        new ProfilingMethodInterceptor());
    }

    public static class ProfilingMethodInterceptor implements MethodInterceptor {

        private final Profiling prof = new Profiling<Object, Throwable>();

        @Override
        public Object invoke(final MethodInvocation invocation) throws Throwable {
            return prof.executeWithProfiling(ProfilingFeatureType.API, invocation.getMethod().getName(), new WithProfilingCallback() {
                @Override
                public Object execute() throws Throwable {
                    final boolean useRODBIfAvailable = shouldUseRODBIfAvailable(invocation);
                    if (!useRODBIfAvailable) {
                        setDirtyDBFlag();
                    }

                    try {
                        logger.debug("Entering API call {}, arguments: {}", invocation.getMethod(), invocation.getArguments());
                        final Object proceed = invocation.proceed();
                        logger.debug("Exiting  API call {}, returning: {}", invocation.getMethod(), proceed);
                        return proceed;
                    } finally {
                        resetDirtyDBFlag();
                    }
                }
            });
        }

        private boolean shouldUseRODBIfAvailable(final MethodInvocation invocation) {
            // Verify if the flag is already set for re-entrant calls
            if (getDirtyDBFlag()) {
                return false;
            }

            final Object[] arguments = invocation.getArguments();
            if (arguments.length == 0) {
                return false;
            }

            // Snowflakes from server filters
            final boolean safeROOperations = "getTenantByApiKey".equals(invocation.getMethod().getName()) || "login".equals(invocation.getMethod().getName());
            if (safeROOperations) {
                return true;
            }

            for (int i = arguments.length - 1; i >= 0; i--) {
                final Object argument = arguments[i];
                // DefaultTenantContext belongs to killbill-internal-api and shouldn't be used by plugins
                final boolean fromJAXRS = argument instanceof DefaultTenantContext && !(argument instanceof CallContext);
                // Kill Bill internal re-entrant calls
                final boolean fromInternalAPIs = argument instanceof InternalTenantContext && !(argument instanceof InternalCallContext);
                // RO DB explicitly requested by a plugin
                final boolean pluginRequestROInstance = argument instanceof ROTenantContext && !(argument instanceof CallContext);
                if (fromJAXRS || fromInternalAPIs || pluginRequestROInstance) {
                    return true;
                }
            }

            return false;
        }
    }

    public static void setDirtyDBFlag() {
        perThreadDirtyDBFlag.set(true);
    }

    public static void resetDirtyDBFlag() {
        perThreadDirtyDBFlag.set(false);
    }

    public static Boolean getDirtyDBFlag() {
        // If unset, we don't come from an API call (i.e. through KillbillApiAopModule): could be bus events for instance.
        // In that case, for safety, always go to the RW instance.
        return perThreadDirtyDBFlag.get() == null ||
               perThreadDirtyDBFlag.get() == Boolean.TRUE;
    }

    private static final Matcher<Method> SYNTHETIC_METHOD_MATCHER = new Matcher<Method>() {
        @Override
        public boolean matches(final Method method) {
            return method.isSynthetic();
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
}
