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

package org.killbill.billing.util.glue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.apache.shiro.aop.AnnotationMethodInterceptor;
import org.apache.shiro.aop.AnnotationResolver;
import org.apache.shiro.guice.aop.ShiroAopModule;

import org.killbill.billing.util.security.AnnotationHierarchicalResolver;
import org.killbill.billing.util.security.AopAllianceMethodInterceptorAdapter;
import org.killbill.billing.util.security.PermissionAnnotationHandler;
import org.killbill.billing.util.security.PermissionAnnotationMethodInterceptor;

import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;

// Provides authentication via Shiro
public class KillBillShiroAopModule extends ShiroAopModule {

    private final AnnotationHierarchicalResolver resolver = new AnnotationHierarchicalResolver();

    @Override
    protected AnnotationResolver createAnnotationResolver() {
        return resolver;
    }

    @Override
    protected void configureInterceptors(final AnnotationResolver resolver) {
        super.configureInterceptors(resolver);

        if (!KillBillShiroModule.isRBACEnabled()) {
            return;
        }

        final PermissionAnnotationHandler permissionAnnotationHandler = new PermissionAnnotationHandler();
        // Inject the Security API
        requestInjection(permissionAnnotationHandler);

        final PermissionAnnotationMethodInterceptor methodInterceptor = new PermissionAnnotationMethodInterceptor(permissionAnnotationHandler, resolver);
        bindShiroInterceptorWithHierarchy(methodInterceptor);
    }

    // Similar to bindShiroInterceptor but will look for annotations in the class hierarchy
    protected final void bindShiroInterceptorWithHierarchy(final AnnotationMethodInterceptor methodInterceptor) {
        bindInterceptor(Matchers.any(),
                        new AbstractMatcher<Method>() {
                            public boolean matches(final Method method) {
                                final Class<? extends Annotation> annotation = methodInterceptor.getHandler().getAnnotationClass();
                                return resolver.getAnnotationFromMethod(method, annotation) != null;
                            }
                        },
                        new AopAllianceMethodInterceptorAdapter(methodInterceptor));
    }
}
