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

package com.ning.billing.meter.timeline.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.consumer.SampleProcessor;
import com.ning.billing.meter.timeline.samples.HalfFloat;
import com.ning.billing.meter.timeline.samples.RepeatSample;
import com.ning.billing.meter.timeline.samples.SampleBase;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.times.DefaultTimelineCursor;
import com.ning.billing.meter.timeline.times.TimelineCursor;

/**
 * Instances of this class encode sample streams.  In addition, this class
 * contains a collection of static methods providing lower-level encoding plumbing
 */
@SuppressWarnings("unchecked")
public class DefaultSampleCoder implements SampleCoder {

    private static final Logger log = LoggerFactory.getLogger(DefaultSampleCoder.class);
    private static final BigInteger BIGINTEGER_ZERO_VALUE = new BigInteger("0");
    private static final ScalarSample<Void> DOUBLE_ZERO_SAMPLE = new ScalarSample<Void>(SampleOpcode.DOUBLE_ZERO, null);
    private static final ScalarSample<Void> INT_ZERO_SAMPLE = new ScalarSample<Void>(SampleOpcode.INT_ZERO, null);

    // TODO: Figure out if 1/200 is an acceptable level of inaccuracy
    // For the HalfFloat, which has a 10-bit mantissa, this means that it could differ
    // in the last 3 bits of the mantissa and still be treated as matching.
    public static final double MAX_FRACTION_ERROR = 1.0 / 200.0;
    public static final double HALF_MAX_FRACTION_ERROR = MAX_FRACTION_ERROR / 2.0;

    private static final double MIN_BYTE_DOUBLE_VALUE = ((double) Byte.MIN_VALUE) * (1.0 + HALF_MAX_FRACTION_ERROR);
    private static final double MAX_BYTE_DOUBLE_VALUE = ((double) Byte.MAX_VALUE) * (1.0 + HALF_MAX_FRACTION_ERROR);

    private static final double MIN_SHORT_DOUBLE_VALUE = ((double) Short.MIN_VALUE) * (1.0 + HALF_MAX_FRACTION_ERROR);
    private static final double MAX_SHORT_DOUBLE_VALUE = ((double) Short.MAX_VALUE) * (1.0 + HALF_MAX_FRACTION_ERROR);

    @SuppressWarnings("unused")
    private static final double INVERSE_MAX_FRACTION_ERROR = 1.0 / MAX_FRACTION_ERROR;

    @Override
    public byte[] compressSamples(final List<ScalarSample> samples) {
        final SampleAccumulator accumulator = new SampleAccumulator(this);
        accumulator.addSampleList(samples);
        return accumulator.getEncodedSamples().getEncodedBytes();
    }

    @Override
    public List<ScalarSample> decompressSamples(final byte[] sampleBytes) throws IOException {
        final List<ScalarSample> returnedSamples = new ArrayList<ScalarSample>();
        final ByteArrayInputStream byteStream = new ByteArrayInputStream(sampleBytes);
        final DataInputStream inputStream = new DataInputStream(byteStream);
        while (true) {
            final int opcodeByte;
            opcodeByte = inputStream.read();
            if (opcodeByte == -1) {
                break; // At "eof"
            }
            final SampleOpcode opcode = SampleOpcode.getOpcodeFromIndex(opcodeByte);
            switch (opcode) {
                case REPEAT_BYTE:
                case REPEAT_SHORT:
                    final int repeatCount = opcode == SampleOpcode.REPEAT_BYTE ? inputStream.readUnsignedByte() : inputStream.readUnsignedShort();
                    final SampleOpcode repeatedOpcode = SampleOpcode.getOpcodeFromIndex(inputStream.read());
                    final Object value = decodeScalarValue(inputStream, repeatedOpcode);
                    for (int i = 0; i < repeatCount; i++) {
                        returnedSamples.add(new ScalarSample(repeatedOpcode, value));
                    }
                    break;
                default:
                    returnedSamples.add(new ScalarSample(opcode, decodeScalarValue(inputStream, opcode)));
                    break;
            }
        }
        return returnedSamples;
    }


    /**
     * This method writes the binary encoding of the sample to the outputStream.  This encoding
     * is the form saved in the db and scanned when read from the db.
     *
     * @param outputStream the stream to which bytes should be written
     * @param sample       the sample to be written
     */
    @Override
    public void encodeSample(final DataOutputStream outputStream, final SampleBase sample) {
        final SampleOpcode opcode = sample.getOpcode();
        try {
            // First put out the opcode value
            switch (opcode) {
                case REPEAT_BYTE:
                case REPEAT_SHORT:
                    final RepeatSample r = (RepeatSample) sample;
                    final ScalarSample repeatee = r.getSampleRepeated();
                    outputStream.write(opcode.getOpcodeIndex());
                    if (opcode == SampleOpcode.REPEAT_BYTE) {
                        outputStream.write(r.getRepeatCount());
                    } else {
                        outputStream.writeShort(r.getRepeatCount());
                    }
                    encodeScalarValue(outputStream, repeatee.getOpcode(), repeatee.getSampleValue());
                case NULL:
                    break;
                default:
                    if (sample instanceof ScalarSample) {
                        encodeScalarValue(outputStream, opcode, ((ScalarSample) sample).getSampleValue());
                    } else {
                        log.error("In encodeSample, opcode {} is not ScalarSample; instead {}", opcode.name(), sample.getClass().getName());
                    }
            }
        } catch (IOException e) {
            log.error(String.format("In encodeSample, IOException encoding opcode %s and value %s", opcode.name(), String.valueOf(sample)), e);
        }
    }

    /**
     * Output the scalar value into the output stream
     *
     * @param outputStream the stream to which bytes should be written
     * @param value        the sample value, interpreted according to the opcode
     */
    @Override
    public void encodeScalarValue(final DataOutputStream outputStream, final SampleOpcode opcode, final Object value) {
        try {
            outputStream.write(opcode.getOpcodeIndex());
            switch (opcode) {
                case NULL:
                case DOUBLE_ZERO:
                case INT_ZERO:
                    break;
                case BYTE:
                case BYTE_FOR_DOUBLE:
                    outputStream.writeByte((Byte) value);
                    break;
                case SHORT:
                case SHORT_FOR_DOUBLE:
                case HALF_FLOAT_FOR_DOUBLE:
                    outputStream.writeShort((Short) value);
                    break;
                case INT:
                    outputStream.writeInt((Integer) value);
                    break;
                case LONG:
                    outputStream.writeLong((Long) value);
                    break;
                case FLOAT:
                case FLOAT_FOR_DOUBLE:
                    outputStream.writeFloat((Float) value);
                    break;
                case DOUBLE:
                    outputStream.writeDouble((Double) value);
                    break;
                case STRING:
                    final String s = (String) value;
                    final byte[] bytes = s.getBytes("UTF-8");
                    outputStream.writeShort(s.length());
                    outputStream.write(bytes, 0, bytes.length);
                    break;
                case BIGINT:
                    final String bs = value.toString();
                    // Only support bigints whose length can be encoded as a short
                    if (bs.length() > Short.MAX_VALUE) {
                        throw new IllegalStateException(String.format("In DefaultSampleCoder.encodeScalarValue(), the string length of the BigInteger is %d; too large to be represented in a Short", bs.length()));
                    }
                    final byte[] bbytes = bs.getBytes("UTF-8");
                    outputStream.writeShort(bs.length());
                    outputStream.write(bbytes, 0, bbytes.length);
                    break;
                default:
                    final String err = String.format("In encodeScalarSample, opcode %s is unrecognized", opcode.name());
                    log.error(err);
                    throw new IllegalArgumentException(err);
            }
        } catch (IOException e) {
            log.error(String.format("In encodeScalarValue, IOException encoding opcode %s and value %s", opcode.name(), String.valueOf(value)), e);
        }
    }

    /**
     * This routine returns a ScalarSample that may have a smaller representation than the
     * ScalarSample argument.  In particular, if tries hard to choose the most compact
     * representation of double-precision values.
     *
     * @param sample A ScalarSample to be compressed
     * @return Either the same ScalarSample is that input, for for some cases of opcode DOUBLE,
     *         a more compact ScalarSample which when processed returns a double value.
     */
    @Override
    public ScalarSample compressSample(final ScalarSample sample) {
        switch (sample.getOpcode()) {
            case INT:
                final int intValue = (Integer) sample.getSampleValue();
                if (intValue == 0) {
                    return INT_ZERO_SAMPLE;
                } else if (intValue >= Byte.MIN_VALUE && intValue <= Byte.MAX_VALUE) {
                    return new ScalarSample(SampleOpcode.BYTE, (byte) intValue);
                } else if (intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE) {
                    return new ScalarSample(SampleOpcode.SHORT, (short) intValue);
                } else {
                    return sample;
                }
            case LONG:
                final long longValue = (Long) sample.getSampleValue();
                if (longValue == 0) {
                    return INT_ZERO_SAMPLE;
                } else if (longValue >= Byte.MIN_VALUE && longValue <= Byte.MAX_VALUE) {
                    return new ScalarSample(SampleOpcode.BYTE, (byte) longValue);
                } else if (longValue >= Short.MIN_VALUE && longValue <= Short.MAX_VALUE) {
                    return new ScalarSample(SampleOpcode.SHORT, (short) longValue);
                } else if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    return new ScalarSample(SampleOpcode.INT, (int) longValue);
                } else {
                    return sample;
                }
            case BIGINT:
                final BigInteger bigValue = (BigInteger) sample.getSampleValue();
                if (bigValue.compareTo(BIGINTEGER_ZERO_VALUE) == 0) {
                    return INT_ZERO_SAMPLE;
                }
                final int digits = 1 + bigValue.bitCount();
                if (digits <= 8) {
                    return new ScalarSample(SampleOpcode.BYTE, (byte) bigValue.intValue());
                } else if (digits <= 16) {
                    return new ScalarSample(SampleOpcode.SHORT, (short) bigValue.intValue());
                } else if (digits <= 32) {
                    return new ScalarSample(SampleOpcode.INT, bigValue.intValue());
                } else if (digits <= 64) {
                    return new ScalarSample(SampleOpcode.LONG, bigValue.longValue());
                } else {
                    return sample;
                }
            case FLOAT:
                return encodeFloatOrDoubleSample(sample, (double) ((Float) sample.getSampleValue()));
            case DOUBLE:
                return encodeFloatOrDoubleSample(sample, (Double) sample.getSampleValue());
            default:
                return sample;
        }
    }

    private ScalarSample encodeFloatOrDoubleSample(final ScalarSample sample, final double value) {
        // We prefer representations in the following order: byte, HalfFloat, short, float and int
        // The criterion for using each representation is the fractional error
        if (value == 0.0) {
            return DOUBLE_ZERO_SAMPLE;
        }
        final boolean integral = value >= MIN_SHORT_DOUBLE_VALUE && value <= MAX_SHORT_DOUBLE_VALUE && (Math.abs((value - (double) ((int) value)) / value) <= MAX_FRACTION_ERROR);
        if (integral && value >= MIN_BYTE_DOUBLE_VALUE && value <= MAX_BYTE_DOUBLE_VALUE) {
            return new ScalarSample<Byte>(SampleOpcode.BYTE_FOR_DOUBLE, (byte) value);
        } else if (integral && value >= MIN_SHORT_DOUBLE_VALUE && value <= MAX_SHORT_DOUBLE_VALUE) {
            return new ScalarSample<Short>(SampleOpcode.SHORT_FOR_DOUBLE, (short) value);
        } else {
            final int halfFloatValue = HalfFloat.fromFloat((float) value);
            if ((Math.abs(value - HalfFloat.toFloat(halfFloatValue)) / value) <= MAX_FRACTION_ERROR) {
                return new ScalarSample<Short>(SampleOpcode.HALF_FLOAT_FOR_DOUBLE, (short) halfFloatValue);
            } else if (value >= Float.MIN_VALUE && value <= Float.MAX_VALUE) {
                return new ScalarSample<Float>(SampleOpcode.FLOAT_FOR_DOUBLE, (float) value);
            } else {
                return sample;
            }
        }
    }

    @Override
    public Object decodeScalarValue(final DataInputStream inputStream, final SampleOpcode opcode) throws IOException {
        switch (opcode) {
            case NULL:
                return null;
            case DOUBLE_ZERO:
                return 0.0;
            case INT_ZERO:
                return 0;
            case BYTE:
                return inputStream.readByte();
            case SHORT:
                return inputStream.readShort();
            case INT:
                return inputStream.readInt();
            case LONG:
                return inputStream.readLong();
            case FLOAT:
                return inputStream.readFloat();
            case DOUBLE:
                return inputStream.readDouble();
            case STRING:
                final short s = inputStream.readShort();
                final byte[] bytes = new byte[s];
                final int byteCount = inputStream.read(bytes, 0, s);
                if (byteCount != s) {
                    log.error("Reading string came up short");
                }
                return new String(bytes, "UTF-8");
            case BIGINT:
                final short bs = inputStream.readShort();
                final byte[] bbytes = new byte[bs];
                final int bbyteCount = inputStream.read(bbytes, 0, bs);
                if (bbyteCount != bs) {
                    log.error("Reading bigint came up short");
                }
                return new BigInteger(new String(bbytes, "UTF-8"), 10);
            case BYTE_FOR_DOUBLE:
                return (double) inputStream.readByte();
            case SHORT_FOR_DOUBLE:
                return (double) inputStream.readShort();
            case FLOAT_FOR_DOUBLE:
                final float floatForDouble = inputStream.readFloat();
                return (double) floatForDouble;
            case HALF_FLOAT_FOR_DOUBLE:
                final float f = HalfFloat.toFloat(inputStream.readShort());
                return (double) f;
            default:
                final String err = String.format("In decodeScalarSample, opcode %s unrecognized", opcode.name());
                log.error(err);
                throw new IllegalArgumentException(err);
        }
    }

    /*
     * This differs from decodeScalarValue because this delivers exactly the
     * type in the byte stream.  Specifically, it does not convert the arg
     * of *_FOR_DOUBLE int a Double()
     */
    private Object decodeOpcodeArg(final DataInputStream inputStream, final SampleOpcode opcode) throws IOException {
        switch (opcode) {
            case NULL:
                return null;
            case DOUBLE_ZERO:
                return 0.0;
            case INT_ZERO:
                return 0;
            case BYTE:
                return inputStream.readByte();
            case SHORT:
                return inputStream.readShort();
            case INT:
                return inputStream.readInt();
            case LONG:
                return inputStream.readLong();
            case FLOAT:
                return inputStream.readFloat();
            case DOUBLE:
                return inputStream.readDouble();
            case STRING:
                final short s = inputStream.readShort();
                final byte[] bytes = new byte[s];
                final int byteCount = inputStream.read(bytes, 0, s);
                if (byteCount != s) {
                    log.error("Reading string came up short");
                }
                return new String(bytes, "UTF-8");
            case BIGINT:
                final short bs = inputStream.readShort();
                final byte[] bbytes = new byte[bs];
                final int bbyteCount = inputStream.read(bbytes, 0, bs);
                if (bbyteCount != bs) {
                    log.error("Reading bigint came up short");
                }
                return new BigInteger(new String(bbytes, "UTF-8"), 10);
            case BYTE_FOR_DOUBLE:
                return inputStream.readByte();
            case SHORT_FOR_DOUBLE:
                return inputStream.readShort();
            case FLOAT_FOR_DOUBLE:
                return inputStream.readFloat();
            case HALF_FLOAT_FOR_DOUBLE:
                return inputStream.readShort();
            default:
                final String err = String.format("In decodeOpcodeArg(), opcode %s unrecognized", opcode.name());
                log.error(err);
                throw new IllegalArgumentException(err);
        }
    }

    @Override
    public double getMaxFractionError() {
        return MAX_FRACTION_ERROR;
    }

    @Override
    public byte[] combineSampleBytes(final List<byte[]> sampleBytesList) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final DataOutputStream dataStream = new DataOutputStream(outputStream);
        try {
            SampleBase lastSample = null;
            for (final byte[] samples : sampleBytesList) {
                final ByteArrayInputStream byteStream = new ByteArrayInputStream(samples);
                final DataInputStream byteDataStream = new DataInputStream(byteStream);
                while (true) {
                    final int opcodeByte = byteDataStream.read();
                    if (opcodeByte == -1) {
                        break;
                    }
                    final SampleOpcode opcode = SampleOpcode.getOpcodeFromIndex(opcodeByte);
                    switch (opcode) {
                        case REPEAT_BYTE:
                        case REPEAT_SHORT:
                            final int newRepeatCount = opcode == SampleOpcode.REPEAT_BYTE ? byteDataStream.read() : byteDataStream.readUnsignedShort();
                            final SampleOpcode newRepeatedOpcode = SampleOpcode.getOpcodeFromIndex(byteDataStream.read());
                            final Object newValue = decodeOpcodeArg(byteDataStream, newRepeatedOpcode);
                            final ScalarSample newRepeatedSample = new ScalarSample(newRepeatedOpcode, newValue);
                            if (lastSample == null) {
                                lastSample = new RepeatSample(newRepeatCount, new ScalarSample(newRepeatedOpcode, newValue));
                            } else if (lastSample instanceof RepeatSample) {
                                final RepeatSample repeatSample = (RepeatSample) lastSample;
                                final ScalarSample repeatedScalarSample = repeatSample.getSampleRepeated();
                                if (repeatedScalarSample.getOpcode() == newRepeatedOpcode &&
                                        (newRepeatedOpcode.getNoArgs() ||
                                                (ScalarSample.sameSampleValues(repeatedScalarSample.getSampleValue(), newValue) &&
                                                        repeatSample.getRepeatCount() + newRepeatCount < RepeatSample.MAX_SHORT_REPEAT_COUNT))) {
                                    // We can just increment the count in the repeat instance
                                    repeatSample.incrementRepeatCount(newRepeatCount);
                                } else {
                                    encodeSample(dataStream, lastSample);
                                    lastSample = new RepeatSample(newRepeatCount, newRepeatedSample);
                                }
                            } else if (lastSample.equals(newRepeatedSample)) {
                                lastSample = new RepeatSample(newRepeatCount + 1, newRepeatedSample);
                            } else {
                                encodeSample(dataStream, lastSample);
                                lastSample = new RepeatSample(newRepeatCount, newRepeatedSample);
                            }
                            break;
                        default:
                            final ScalarSample newSample = new ScalarSample(opcode, decodeOpcodeArg(byteDataStream, opcode));
                            if (lastSample == null) {
                                lastSample = newSample;
                            } else if (lastSample instanceof RepeatSample) {
                                final RepeatSample repeatSample = (RepeatSample) lastSample;
                                final ScalarSample repeatedScalarSample = repeatSample.getSampleRepeated();
                                if (newSample.equals(repeatedScalarSample)) {
                                    repeatSample.incrementRepeatCount();
                                } else {
                                    encodeSample(dataStream, lastSample);
                                    lastSample = newSample;
                                }
                            } else if (lastSample.equals(newSample)) {
                                lastSample = new RepeatSample(2, newSample);
                            } else {
                                encodeSample(dataStream, lastSample);
                                lastSample = newSample;
                            }
                    }
                }
            }
            if (lastSample != null) {
                encodeSample(dataStream, lastSample);
            }
            dataStream.flush();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("In combineSampleBytes(), exception combining sample byte arrays", e);
            return new byte[0];
        }
    }

    /**
     * This invokes the processor on the values in the timeline bytes.
     *
     * @param chunk     the timeline chuck to scan
     * @param processor the callback to which values value counts are passed to be processed.
     * @throws java.io.IOException
     */
    @Override
    public void scan(final TimelineChunk chunk, final SampleProcessor processor) throws IOException {
        //System.out.printf("Decoded: %s\n", new String(Hex.encodeHex(bytes)));
        scan(chunk.getTimeBytesAndSampleBytes().getSampleBytes(), chunk.getTimeBytesAndSampleBytes().getTimeBytes(), chunk.getSampleCount(), processor);
    }

    @Override
    public void scan(final byte[] samples, final byte[] times, final int sampleCount, final SampleProcessor processor) throws IOException {
        final ByteArrayInputStream byteStream = new ByteArrayInputStream(samples);
        final DataInputStream inputStream = new DataInputStream(byteStream);
        final TimelineCursor timeCursor = new DefaultTimelineCursor(times, sampleCount);
        int sampleNumber = 0;
        while (true) {
            final int opcodeByte;
            opcodeByte = inputStream.read();
            if (opcodeByte == -1) {
                return; // At "eof"
            }
            final SampleOpcode opcode = SampleOpcode.getOpcodeFromIndex(opcodeByte);
            switch (opcode) {
                case REPEAT_BYTE:
                case REPEAT_SHORT:
                    final int repeatCount = opcode == SampleOpcode.REPEAT_BYTE ? inputStream.readUnsignedByte() : inputStream.readUnsignedShort();
                    final SampleOpcode repeatedOpcode = SampleOpcode.getOpcodeFromIndex(inputStream.read());
                    final Object value = decodeScalarValue(inputStream, repeatedOpcode);
                    final SampleOpcode replacementOpcode = repeatedOpcode.getReplacement();
                    processor.processSamples(timeCursor, repeatCount, replacementOpcode, value);
                    sampleNumber += repeatCount;
                    timeCursor.skipToSampleNumber(sampleNumber);
                    break;
                default:
                    processor.processSamples(timeCursor, 1, opcode.getReplacement(), decodeScalarValue(inputStream, opcode));
                    break;
            }
        }
    }
}
