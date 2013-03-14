/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.meter.timeline.samples;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

/**
 * A sample value associated with its opcode
 *
 * @param <T> A value consistent with the opcode
 */
public class ScalarSample<T> extends SampleBase {

    private static final String KEY_OPCODE = "O";
    private static final String KEY_SAMPLE_CLASS = "K";
    private static final String KEY_SAMPLE_VALUE = "V";

    private final T sampleValue;

    public static ScalarSample fromObject(final Object sampleValue) {
        if (sampleValue == null) {
            return new ScalarSample<Void>(SampleOpcode.NULL, null);
        } else if (sampleValue instanceof Byte) {
            return new ScalarSample<Byte>(SampleOpcode.BYTE, (Byte) sampleValue);
        } else if (sampleValue instanceof Short) {
            return new ScalarSample<Short>(SampleOpcode.SHORT, (Short) sampleValue);
        } else if (sampleValue instanceof Integer) {
            try {
                // Can it fit in a short?
                final short optimizedShort = Shorts.checkedCast(Long.valueOf(sampleValue.toString()));
                return new ScalarSample<Short>(SampleOpcode.SHORT, optimizedShort);
            } catch (IllegalArgumentException e) {
                return new ScalarSample<Integer>(SampleOpcode.INT, (Integer) sampleValue);
            }
        } else if (sampleValue instanceof Long) {
            try {
                // Can it fit in a short?
                final short optimizedShort = Shorts.checkedCast(Long.valueOf(sampleValue.toString()));
                return new ScalarSample<Short>(SampleOpcode.SHORT, optimizedShort);
            } catch (IllegalArgumentException e) {
                try {
                    // Can it fit in an int?
                    final int optimizedLong = Ints.checkedCast(Long.valueOf(sampleValue.toString()));
                    return new ScalarSample<Integer>(SampleOpcode.INT, optimizedLong);
                } catch (IllegalArgumentException ohWell) {
                    return new ScalarSample<Long>(SampleOpcode.LONG, (Long) sampleValue);
                }
            }
        } else if (sampleValue instanceof Float) {
            return new ScalarSample<Float>(SampleOpcode.FLOAT, (Float) sampleValue);
        } else if (sampleValue instanceof Double) {
            return new ScalarSample<Double>(SampleOpcode.DOUBLE, (Double) sampleValue);
        } else {
            return new ScalarSample<String>(SampleOpcode.STRING, sampleValue.toString());
        }
    }

    public ScalarSample(final SampleOpcode opcode, final T sampleValue) {
        super(opcode);
        this.sampleValue = sampleValue;
    }

    public ScalarSample(final String opcode, final T sampleValue) {
        this(SampleOpcode.valueOf(opcode), sampleValue);
    }

    public double getDoubleValue() {
        final Object sampleValue = getSampleValue();
        return getDoubleValue(getOpcode(), sampleValue);
    }

    public static double getDoubleValue(final SampleOpcode opcode, final Object sampleValue) {
        switch (opcode) {
            case NULL:
            case DOUBLE_ZERO:
            case INT_ZERO:
                return 0.0;
            case BYTE:
            case BYTE_FOR_DOUBLE:
                return (double) ((Byte) sampleValue);
            case SHORT:
            case SHORT_FOR_DOUBLE:
                return (double) ((Short) sampleValue);
            case INT:
                return (double) ((Integer) sampleValue);
            case LONG:
                return (double) ((Long) sampleValue);
            case FLOAT:
            case FLOAT_FOR_DOUBLE:
                return (double) ((Float) sampleValue);
            case HALF_FLOAT_FOR_DOUBLE:
                return (double) HalfFloat.toFloat((Short) sampleValue);
            case DOUBLE:
                return (Double) sampleValue;
            case BIGINT:
                return ((BigInteger) sampleValue).doubleValue();
            default:
                throw new IllegalArgumentException(String.format("In getDoubleValue(), sample opcode is %s, sample value is %s",
                                                                 opcode.name(), String.valueOf(sampleValue)));
        }
    }

    @JsonCreator
    public ScalarSample(@JsonProperty(KEY_OPCODE) final byte opcodeIdx,
                        @JsonProperty(KEY_SAMPLE_CLASS) final Class klass,
                        @JsonProperty(KEY_SAMPLE_VALUE) final T sampleValue) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        super(SampleOpcode.getOpcodeFromIndex(opcodeIdx));
        // Numerical classes have a String constructor
        this.sampleValue = (T) klass.getConstructor(String.class).newInstance(sampleValue.toString());
    }

    @JsonValue
    public Map<String, Object> toMap() {
        // Work around type erasure by storing explicitly the sample class. This avoid deserializing shorts as integers
        // at replay time for instance
        return ImmutableMap.of(KEY_OPCODE, opcode.getOpcodeIndex(), KEY_SAMPLE_CLASS, sampleValue.getClass(), KEY_SAMPLE_VALUE, sampleValue);
    }

    public T getSampleValue() {
        return sampleValue;
    }

    @Override
    public String toString() {
        return sampleValue.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (other == null || !(other instanceof SampleBase)) {
            return false;
        }
        final ScalarSample otherSample = (ScalarSample) other;
        final Object otherValue = otherSample.getSampleValue();
        if (getOpcode() != otherSample.getOpcode()) {
            return false;
        } else if (!opcode.getNoArgs() && !(sameSampleValues(sampleValue, otherValue))) {
            return false;
        }
        return true;
    }

    public static boolean sameSampleValues(final Object o1, final Object o2) {
        if (o1 == o2) {
            return true;
        } else if (o1.getClass() == o2.getClass()) {
            return o1.equals(o2);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return sampleValue != null ? sampleValue.hashCode() : 0;
    }
}
