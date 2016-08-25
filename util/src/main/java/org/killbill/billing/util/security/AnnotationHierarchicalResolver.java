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

package org.killbill.billing.util.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.apache.shiro.aop.AnnotationResolver;
import org.apache.shiro.aop.MethodInvocation;

public class AnnotationHierarchicalResolver implements AnnotationResolver {

    private final Map<String, Annotation> methodToAnnotation = new HashMap<String, Annotation>();

    @Override
    public Annotation getAnnotation(final MethodInvocation mi, final Class<? extends Annotation> clazz) {
        return getAnnotationFromMethod(mi.getMethod(), clazz);
    }

    public Annotation getAnnotationFromMethod(final Method method, final Class<? extends Annotation> clazz) {
        final String key = method.toString();
        Annotation annotation = methodToAnnotation.get(key);
        if (annotation == null) {
            synchronized (methodToAnnotation) {
                annotation = methodToAnnotation.get(key);
                if (annotation == null) {
                    annotation = findAnnotation(method, clazz);
                    methodToAnnotation.put(key, annotation);
                }
            }
        }
        return annotation;
    }

    // The following comes from spring-core (AnnotationUtils) to handle annotations on interfaces

    /**
     * Get a single {@link Annotation} of <code>annotationType</code> from the supplied {@link java.lang.reflect.Method},
     * traversing its super methods if no annotation can be found on the given method itself.
     * <p>Annotations on methods are not inherited by default, so we need to handle this explicitly.
     *
     * @param method         the method to look for annotations on
     * @param annotationType the annotation class to look for
     * @return the annotation found, or <code>null</code> if none found
     */
    public static <A extends Annotation> A findAnnotation(final Method method, final Class<A> annotationType) {
        A annotation = getAnnotation(method, annotationType);
        Class<?> cl = method.getDeclaringClass();
        if (annotation == null) {
            annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
        }
        while (annotation == null) {
            cl = cl.getSuperclass();
            if (cl == null || cl == Object.class) {
                break;
            }
            try {
                final Method equivalentMethod = cl.getDeclaredMethod(method.getName(), method.getParameterTypes());
                annotation = getAnnotation(equivalentMethod, annotationType);
                if (annotation == null) {
                    annotation = searchOnInterfaces(method, annotationType, cl.getInterfaces());
                }
            } catch (NoSuchMethodException ex) {
                // We're done...
            }
        }
        return annotation;
    }

    /**
     * Get a single {@link Annotation} of <code>annotationType</code> from the supplied {@link Method}.
     *
     * @param method         the method to look for annotations on
     * @param annotationType the annotation class to look for
     * @return the annotations found
     */
    public static <A extends Annotation> A getAnnotation(final Method method, final Class<A> annotationType) {
        A ann = method.getAnnotation(annotationType);
        if (ann == null) {
            for (final Annotation metaAnn : method.getAnnotations()) {
                ann = metaAnn.annotationType().getAnnotation(annotationType);
                if (ann != null) {
                    break;
                }
            }
        }
        return ann;
    }

    private static <A extends Annotation> A searchOnInterfaces(final Method method, final Class<A> annotationType, final Class<?>[] ifcs) {
        A annotation = null;
        for (final Class<?> iface : ifcs) {
            if (isInterfaceWithAnnotatedMethods(iface)) {
                try {
                    final Method equivalentMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                    annotation = getAnnotation(equivalentMethod, annotationType);
                } catch (NoSuchMethodException ex) {
                    // Skip this interface - it doesn't have the method...
                }
                if (annotation != null) {
                    break;
                }
            }
        }
        return annotation;
    }

    private static final Map<Class<?>, Boolean> annotatedInterfaceCache = new WeakHashMap<Class<?>, Boolean>();

    private static boolean isInterfaceWithAnnotatedMethods(final Class<?> iface) {
        synchronized (annotatedInterfaceCache) {
            final Boolean flag = annotatedInterfaceCache.get(iface);
            if (flag != null) {
                return flag;
            }
            boolean found = false;
            for (final Method ifcMethod : iface.getMethods()) {
                if (ifcMethod.getAnnotations().length > 0) {
                    found = true;
                    break;
                }
            }
            annotatedInterfaceCache.put(iface, found);
            return found;
        }
    }
}
