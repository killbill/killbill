/*
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

package org.killbill.billing.util.glue;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.killbill.billing.KillbillApi;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

public class KillbillApiAopModule extends AbstractModule {

    @Override
    protected void configure() {

        bindInterceptor(Matchers.subclassesOf(KillbillApi.class),
                        Matchers.any(),
                        new ProfilingMethodInterceptor());
    }

    public static class ProfilingMethodInterceptor implements MethodInterceptor {

        private final Profiling prof = new Profiling<Object>();

        @Override
        public Object invoke(final MethodInvocation invocation) throws Throwable {
            return prof.executeWithProfiling("API:" + invocation.getMethod().getName(), new WithProfilingCallback() {
                @Override
                public Object execute() throws Throwable {
                    return invocation.proceed();
                }
            });
        }
    }
}
