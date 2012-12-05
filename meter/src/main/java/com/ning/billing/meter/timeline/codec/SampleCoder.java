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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.consumer.SampleProcessor;
import com.ning.billing.meter.timeline.samples.SampleBase;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;

/**
 * Samples compressor and decompressor
 */
public interface SampleCoder {

    public byte[] compressSamples(final List<ScalarSample> samples);

    public List<ScalarSample> decompressSamples(final byte[] sampleBytes) throws IOException;

    /**
     * This method writes the binary encoding of the sample to the outputStream.  This encoding
     * is the form saved in the db and scanned when read from the db.
     *
     * @param outputStream the stream to which bytes should be written
     * @param sample       the sample to be written
     */
    public void encodeSample(final DataOutputStream outputStream, final SampleBase sample);

    /**
     * Output the scalar value into the output stream
     *
     * @param outputStream the stream to which bytes should be written
     * @param value        the sample value, interpreted according to the opcode
     */
    public void encodeScalarValue(final DataOutputStream outputStream, final SampleOpcode opcode, final Object value);

    /**
     * This routine returns a ScalarSample that may have a smaller representation than the
     * ScalarSample argument.  In particular, if tries hard to choose the most compact
     * representation of double-precision values.
     *
     * @param sample A ScalarSample to be compressed
     * @return Either the same ScalarSample is that input, for for some cases of opcode DOUBLE,
     *         a more compact ScalarSample which when processed returns a double value.
     */
    public ScalarSample compressSample(final ScalarSample sample);

    public Object decodeScalarValue(final DataInputStream inputStream, final SampleOpcode opcode) throws IOException;

    public double getMaxFractionError();

    public byte[] combineSampleBytes(final List<byte[]> sampleBytesList);

    public void scan(final TimelineChunk chunk, final SampleProcessor processor) throws IOException;

    public void scan(final byte[] samples, final byte[] times, final int sampleCount, final SampleProcessor processor) throws IOException;
}
