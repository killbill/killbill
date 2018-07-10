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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.killbill.billing.catalog.api.BlockType;
import org.killbill.billing.catalog.api.FixedType;
import org.killbill.billing.catalog.api.TierBlockPolicy;

public class CatalogSafetyInitializer {


    public static final Integer DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE = -1;
    public static final Double DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE = new Double(-1);

    private static final Map<Class, LinkedList<Field>> perCatalogClassNonRequiredFields = new HashMap<Class, LinkedList<Field>>();

    //
    // Ensure that all uninitialized arrays for which there is neither a 'required' XmlElementWrapper or XmlElement annotation
    // end up initialized with a default zero length array (allowing to safely get the length and iterate over (0) element.
    //
    public static void initializeNonRequiredNullFieldsWithDefaultValue(final Object obj) {

        LinkedList<Field> fields = perCatalogClassNonRequiredFields.get(obj.getClass());
        if (fields == null) {
            fields = initializeNonRequiredFields(obj.getClass());
            perCatalogClassNonRequiredFields.put(obj.getClass(), fields);
        }
        try {
            for (final Field f : fields) {
                if (f.getType().isArray()) {
                    initializeArrayIfNull(obj, f);
                } else if (!f.getType().isPrimitive()) {
                    if (f.getType().isEnum()) {
                        if (FixedType.class.equals(f.getType())) {
                            initializeFieldWithValue(obj, f, FixedType.ONE_TIME);
                        } else if (BlockType.class.equals(f.getType())) {
                            initializeFieldWithValue(obj, f, BlockType.VANILLA);
                        } else if (TierBlockPolicy.class.equals(f.getType())) {
                            initializeFieldWithValue(obj, f, TierBlockPolicy.ALL_TIERS);
                        }
                    } else if (Integer.class.equals(f.getType())) {
                        initializeFieldWithValue(obj, f, DEFAULT_NON_REQUIRED_INTEGER_FIELD_VALUE);
                    } else if (Double.class.equals(f.getType())) {
                        initializeFieldWithValue(obj, f, DEFAULT_NON_REQUIRED_DOUBLE_FIELD_VALUE);
                    }
                }
            }
        } catch (final IllegalAccessException e) {
            throw new RuntimeException("Failed during catalog initialization : ", e);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Failed during catalog initialization : ", e);
        }
    }

    // For each type of catalog object we keep the 'Field' associated to non required attribute fields
    private static LinkedList<Field> initializeNonRequiredFields(final Class<?> aClass) {

        final LinkedList<Field> result = new LinkedList();
        final Field[] fields = aClass.getDeclaredFields();
        for (final Field f : fields) {
            if (f.getType().isArray()) {
                final XmlElementWrapper xmlElementWrapper = f.getAnnotation(XmlElementWrapper.class);
                if (xmlElementWrapper != null) {
                    if (!xmlElementWrapper.required()) {
                        result.add(f);
                    }
                } else {
                    final XmlElement xmlElement = f.getAnnotation(XmlElement.class);
                    if (xmlElement != null && !xmlElement.required()) {
                        result.add(f);
                    }
                }
            } else if (!f.getType().isPrimitive()) {
                if (f.getType().isEnum()) {
                    if (FixedType.class.equals(f.getType())) {
                        result.add(f);
                    } else if (BlockType.class.equals(f.getType())) {
                        result.add(f);
                    } else if (TierBlockPolicy.class.equals(f.getType())) {
                        result.add(f);
                    }
                } else if (Integer.class.equals(f.getType())) {
                    result.add(f);
                } else if (Double.class.equals(f.getType())) {
                    result.add(f);
                }
            }
        }
        return result;
    }

    private static void initializeFieldWithValue(final Object obj, final Field f, final Object value) throws IllegalAccessException, ClassNotFoundException {
        synchronized (perCatalogClassNonRequiredFields) {
            f.setAccessible(true);
            if (f.get(obj) == null) {
                f.set(obj, value);
            }
            f.setAccessible(false);
        }
    }

    private static void initializeArrayIfNull(final Object obj, final Field f) throws IllegalAccessException, ClassNotFoundException {
        synchronized (perCatalogClassNonRequiredFields) {
            f.setAccessible(true);
            if (f.get(obj) == null) {
                f.set(obj, getZeroLengthArrayInitializer(f));
            }
            f.setAccessible(false);
        }
    }


    private static Object[] getZeroLengthArrayInitializer(final Field f) throws ClassNotFoundException {
        // Yack... type erasure, why?
        final String arrayClassName = f.getType().getCanonicalName();
        final Class type = Class.forName(arrayClassName.substring(0, arrayClassName.length() - 2));
        return (Object[]) Array.newInstance(type, 0);
    }
}
