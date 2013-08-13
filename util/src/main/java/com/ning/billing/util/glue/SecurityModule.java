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

package com.ning.billing.util.glue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.shiro.aop.AnnotationMethodInterceptor;
import org.apache.shiro.aop.AnnotationResolver;
import org.apache.shiro.guice.aop.ShiroAopModule;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.security.api.SecurityApi;
import com.ning.billing.util.config.SecurityConfig;
import com.ning.billing.util.security.AnnotationHierarchicalResolver;
import com.ning.billing.util.security.AopAllianceMethodInterceptorAdapter;
import com.ning.billing.util.security.PermissionAnnotationMethodInterceptor;

import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;

public class SecurityModule extends ShiroAopModule {

    private final AnnotationHierarchicalResolver resolver = new AnnotationHierarchicalResolver();

    private final ConfigSource configSource;

    private SecurityConfig securityConfig;
    private SecurityApi securityApi;

    public SecurityModule() {
        this(new SimplePropertyConfigSource(System.getProperties()));
    }

    public SecurityModule(final ConfigSource configSource) {
        super();
        this.configSource = configSource;
    }

    // LAME - the configure method is final in ShiroAopModule so we piggy back configureInterceptors
    private void doConfigure() {
        installConfig();
        installSecurityApi();
    }

    private void installConfig() {
        securityConfig = new ConfigurationObjectFactory(configSource).build(SecurityConfig.class);
        bind(SecurityConfig.class).toInstance(securityConfig);
    }

    private void installSecurityApi() {
        securityApi = new SecurityApiProvider(securityConfig).get();
        bind(SecurityApi.class).toInstance(securityApi);
    }

    @Override
    protected AnnotationResolver createAnnotationResolver() {
        return resolver;
    }

    @Override
    protected void configureInterceptors(final AnnotationResolver resolver) {
        // HACK
        doConfigure();

        super.configureInterceptors(resolver);
        bindShiroInterceptorWithHierarchy(new PermissionAnnotationMethodInterceptor(securityApi, resolver));
    }

    // Similar to bindShiroInterceptor but will look for annotations in the class hierarchy
    protected final void bindShiroInterceptorWithHierarchy(final AnnotationMethodInterceptor methodInterceptor) {
        bindInterceptor(Matchers.any(),
                        new AbstractMatcher<Method>() {
                            public boolean matches(final Method method) {
                                final Class<? extends Annotation> annotation = methodInterceptor.getHandler().getAnnotationClass();
                                return resolver.getAnnotationFromMethod(method, annotation) != null;
                            }
                        }, new AopAllianceMethodInterceptorAdapter(methodInterceptor));
    }
}
