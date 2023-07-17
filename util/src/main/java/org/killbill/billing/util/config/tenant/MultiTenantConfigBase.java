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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.BusInternalEvent.BusInternalEventType;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.skife.config.Config;
import org.skife.config.Separator;
import org.skife.config.TimeSpan;

public abstract class MultiTenantConfigBase implements KillbillConfig {

    private final Map<String, Method> methodsCache = new HashMap<>();
    protected final KillbillConfig staticConfig;

    protected final CacheConfig cacheConfig;

    private static final Function<String, Integer> INT_CONVERTER = Integer::valueOf;

    private static final Function<String, TimeSpan> TIME_SPAN_CONVERTER = TimeSpan::new;

    private static final Function<String, BusInternalEventType> BUS_EVENT_TYPE_CONVERTER = BusInternalEventType::valueOf;


    public MultiTenantConfigBase(final KillbillConfig staticConfig, final CacheConfig cacheConfig) {
        this.staticConfig = staticConfig;
        this.cacheConfig = cacheConfig;
    }

    //
    // The conversion methods are rather limited (but this is all we need).
    // Ideally we could reuse the bully/Coercer from skife package, but those are kept private.
    //
    protected List<String> convertToListString(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        final List<String> tokens = getTokens(method, value);
        return List.copyOf(tokens);
    }

    protected List<TimeSpan> convertToListTimeSpan(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        return getTokens(method, value).stream()
                .map(TIME_SPAN_CONVERTER)
                .collect(Collectors.toUnmodifiableList());
    }

    protected List<BusInternalEventType> convertToListBusInternalEventType(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        return getTokens(method, value).stream()
                .map(BUS_EVENT_TYPE_CONVERTER)
                .collect(Collectors.toUnmodifiableList());
    }

    protected List<Integer> convertToListInteger(final String value, final String methodName) {
        final Method method = getConfigStaticMethodWithChecking(methodName);
        return getTokens(method, value).stream()
                .map(INT_CONVERTER)
                .collect(Collectors.toUnmodifiableList());
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
            return Collections.emptyList();
        } else {
            return List.of(value.split(separator == null ? Separator.DEFAULT : separator.value()));
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
