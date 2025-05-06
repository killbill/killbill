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

package org.killbill.billing.catalog;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.FixedType;
import org.killbill.billing.catalog.api.TierBlockPolicy;

public class CatalogSafetyInitializer {

    public static final Integer DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE = -1;
    public static final Double DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE = (double) -1;
    public static final BigDecimal DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE = new BigDecimal("-1");

    // Cache of prototype instances with default values already set, avoids repeated reflection
    private static final Map<String, Object> prototypeCache = new ConcurrentHashMap<>();

    // Cache for zero-length arrays
    private static final Map<Class<?>, Object> zeroLengthArrayCache = new ConcurrentHashMap<>();

    public static void initializeNonRequiredNullFieldsWithDefaultValue(final Object obj) {
        final Class<?> clazz = obj.getClass();

        try {
            Object prototype = prototypeCache.get(clazz.getName());

            if (prototype == null) {
                // Create and cache a fully initialized prototype to reduce reflection overhead
                prototype = createInitializedPrototype(clazz);
            }

            applyPrototypeValues(prototype, obj);
        } catch (Exception e) {
            // Fall back to the old field-by-field method if prototype approach fails
            legacyFieldByFieldInitialization(obj);
        }
    }

    /**
     * Creates a fully initialized prototype object for the given class.
     */
    private static Object createInitializedPrototype(Class<?> clazz) throws Exception {
        final Object prototype = clazz.getDeclaredConstructor().newInstance();

        // Initialize all non-required fields with default values
        for (final Field field : clazz.getDeclaredFields()) {
            if (!field.trySetAccessible()) {
                continue;
            }

            if (shouldInitializeField(field)) {
                if (field.getType().isArray()) {
                    field.set(prototype, getZeroLengthArray(field.getType().getComponentType()));
                } else {
                    handleEnumInitialization(prototype, field);
                }
            }
        }

        // Cache the prototype
        final Object existingPrototype = prototypeCache.putIfAbsent(clazz.getName(), prototype);
        return existingPrototype != null ? existingPrototype : prototype;
    }

    private static void handleEnumInitialization(final Object prototype, final Field field) throws IllegalAccessException {
        if (field.getType().isEnum()) {
            setDefaultEnumValue(prototype, field);
        } else if (Integer.class.equals(field.getType())) {
            field.set(prototype, DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE);
        } else if (Double.class.equals(field.getType())) {
            field.set(prototype, DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE);
        } else if (BigDecimal.class.equals(field.getType())) {
            field.set(prototype, DEFAULT_NON_REQUIRED_BIGDECIMAL_FIELD_VALUE);
        }
    }

    /**
     * Determines if a field should be initialized based on annotations.
     */
    private static boolean shouldInitializeField(final Field field) {
        // Don't initialize primitive types
        if (field.getType().isPrimitive()) {
            return false;
        }

        // Check for array types
        if (field.getType().isArray()) {
            final XmlElementWrapper xmlElementWrapper =
                    field.getAnnotation(XmlElementWrapper.class);
            if (xmlElementWrapper != null) {
                return !xmlElementWrapper.required();
            }

            final XmlElement xmlElement = field.getAnnotation(XmlElement.class);
            return xmlElement != null && !xmlElement.required();
        }

        return field.getType().isEnum() ||
               Integer.class.equals(field.getType()) ||
               Double.class.equals(field.getType()) ||
               BigDecimal.class.equals(field.getType());
    }

    /**
     * Sets the default enum value for a field.
     */
    private static void setDefaultEnumValue(Object obj, Field field) throws IllegalAccessException {
        if (FixedType.class.equals(field.getType())) {
            field.set(obj, FixedType.ONE_TIME);
        } else if (BlockType.class.equals(field.getType())) {
            field.set(obj, BlockType.VANILLA);
        } else if (TierBlockPolicy.class.equals(field.getType())) {
            field.set(obj, TierBlockPolicy.ALL_TIERS);
        }
    }

    /**
     * Applies values from the prototype to null fields in the target object for minimal reflection usage.
     */
    private static void applyPrototypeValues(Object prototype, Object target) throws IllegalAccessException {
        for (final Field field : target.getClass().getDeclaredFields()) {
            if (!field.trySetAccessible()) {
                continue;
            }

            if (field.get(target) == null && shouldInitializeField(field)) {
                field.set(target, field.get(prototype));
            }
        }
    }

    /**
     * Gets or creates a zero-length array of the specified component type.
     */
    private static Object getZeroLengthArray(Class<?> componentType) {
        Object array = zeroLengthArrayCache.get(componentType);
        if (array == null) {
            array = Array.newInstance(componentType, 0);
            final Object existing = zeroLengthArrayCache.putIfAbsent(componentType, array);
            if (existing != null) {
                array = existing;
            }
        }
        return array;
    }

    /**
     * Legacy field-by-field initialization method as fallback, used when prototype creation fails.
     */
    private static void legacyFieldByFieldInitialization(Object obj) {
        try {
            for (final Field field : obj.getClass().getDeclaredFields()) {
                if (!field.trySetAccessible()) {
                    continue;
                }

                if (field.get(obj) != null) {
                    continue;
                }

                if (field.getType().isArray()) {
                    if (shouldInitializeField(field)) {
                        field.set(obj, getZeroLengthArray(field.getType().getComponentType()));
                    }
                } else if (!field.getType().isPrimitive()) {
                    handleEnumInitialization(obj, field);
                }
            }
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Failed during catalog initialization", e);
        }
    }
}
