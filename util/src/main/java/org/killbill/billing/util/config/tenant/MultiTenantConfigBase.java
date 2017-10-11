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

package org.killbill.billing.util.config.tenant;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.skife.config.Config;
import org.skife.config.Separator;
import org.skife.config.TimeSpan;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public abstract class MultiTenantConfigBase {

    private final Map<String, Method> methodsCache = new HashMap<String, Method>();
    protected final CacheConfig cacheConfig;

    private final static Function<String, Integer> INT_CONVERTER = new Function<String, Integer>() {
        @Override
        public Integer apply(final String input) {
            return Integer.valueOf(input);
        }
    };

    private final static Function<String, TimeSpan> TIME_SPAN_CONVERTER = new Function<String, TimeSpan>() {
        @Override
        public TimeSpan apply(final String input) {
            return new TimeSpan(input);
        }
    };

    public MultiTenantConfigBase(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
    }

    //
    // The conversion methds are rather limited (but this is all we need).
    // Ideally we could reuse the bully/Coercer from skife package, but those are kept private.
    //

    protected List<String> convertToListString(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        final Iterable<String> tokens = getTokens(method, value);
        return ImmutableList.copyOf(tokens);
    }

    protected List<TimeSpan> convertToListTimeSpan(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        final Iterable<String> tokens = getTokens(method, value);
        return ImmutableList.copyOf(Iterables.transform(tokens, TIME_SPAN_CONVERTER));
    }

    protected List<Integer> convertToListInteger(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        final Iterable<String> tokens = getTokens(method, value);
        return ImmutableList.copyOf(Iterables.transform(tokens, INT_CONVERTER));
    }

    protected String getStringTenantConfig(final String methodName, final InternalTenantContext tenantContext) {
        // That means we want to default to static config value
        if (tenantContext == null) {
            return null;
        }
        final Method method = getConfigStaticMethodWithChecking(methodName);
        return getCachedValue(method.getAnnotation(Config.class), tenantContext);
    }

    private String getCachedValue(final Config annotation, final InternalTenantContext tenantContext) {
        final PerTenantConfig perTenantConfig = cacheConfig.getPerTenantConfig(tenantContext);
        for (final String propertyName : annotation.value()) {
            final String result = perTenantConfig.get(propertyName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Method getConfigStaticMethodWithChecking(final String methodName) {
        final Method method = getConfigStaticMethod(methodName);
        if (!method.isAnnotationPresent(Config.class)) {
            throw new RuntimeException("Missing @Config annotation to skife config method " + method.getName());
        }
        return method;
    }

    private List<String> getTokens(final Method method, final String value) {
        final Separator separator = method.getAnnotation(Separator.class);
        if (value == null || value.isEmpty()) {
            return ImmutableList.of();
        } else {
            return ImmutableList.copyOf(value.split(separator == null ? Separator.DEFAULT : separator.value()));
        }
    }

    protected Method getConfigStaticMethod(final String methodName) {
        Method method = methodsCache.get(methodName);
        if (method == null) {
            synchronized (methodsCache) {
                method = methodsCache.get(methodName);
                if (method == null) {
                    try {
                        method = getConfigClass().getMethod(methodName, InternalTenantContext.class);
                        methodsCache.put(methodName, method);
                    } catch (final NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return method;
    }

    protected abstract Class<? extends KillbillConfig> getConfigClass();
}
