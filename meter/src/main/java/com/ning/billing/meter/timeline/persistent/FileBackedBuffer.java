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

package com.ning.billing.meter.timeline.persistent;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileGenerator;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.util.membuf.MemBuffersForBytes;
import com.fasterxml.util.membuf.StreamyBytesMemBuffer;
import com.google.common.annotations.VisibleForTesting;

/**
 * Backing buffer for a single TimelineSourceEventAccumulator that spools to disk
 */
public class FileBackedBuffer {

    private static final Logger log = LoggerFactory.getLogger(FileBackedBuffer.class);

    private static final SmileFactory smileFactory = new SmileFactory();
    private final ObjectMapper smileObjectMapper;

    static {
        // Disable all magic for now as we don't write the Smile header (we share the same smileGenerator
        // across multiple backend files)
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_NAMES, false);
        smileFactory.configure(SmileGenerator.Feature.CHECK_SHARED_STRING_VALUES, false);
    }

    private final String basePath;
    private final String prefix;
    private final boolean deleteFilesOnClose;
    private final AtomicLong samplesforTimestampWritten = new AtomicLong();
    private final Object recyclingMonitor = new Object();

    private final StreamyBytesMemBuffer inputBuffer;
    private StreamyBytesPersistentOutputStream out = null;
    private SmileGenerator smileGenerator;

    public FileBackedBuffer(final String basePath, final String prefix, final int segmentsSize, final int maxNbSegments) throws IOException {
        this(basePath, prefix, true, segmentsSize, maxNbSegments);
    }

    public FileBackedBuffer(final String basePath, final String prefix, final boolean deleteFilesOnClose, final int segmentsSize, final int maxNbSegments) throws IOException {
        this.basePath = basePath;
        this.prefix = prefix;
        this.deleteFilesOnClose = deleteFilesOnClose;

        smileObjectMapper = new ObjectMapper(smileFactory);
        smileObjectMapper.registerModule(new JodaModule());
        smileObjectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final MemBuffersForBytes bufs = new MemBuffersForBytes(segmentsSize, 1, maxNbSegments);
        inputBuffer = bufs.createStreamyBuffer(1, maxNbSegments);

        recycle();
    }

    public boolean append(final SourceSamplesForTimestamp sourceSamplesForTimestamp) {
        try {
            synchronized (recyclingMonitor) {
                smileObjectMapper.writeValue(smileGenerator, sourceSamplesForTimestamp);
                samplesforTimestampWritten.incrementAndGet();
                return true;
            }
        } catch (IOException e) {
            log.warn("Unable to backup samples", e);
            return false;
        }
    }

    /**
     * Discard in-memory and on-disk data
     */
    public void discard() {
        try {
            recycle();
        } catch (IOException e) {
            log.warn("Exception discarding buffer", e);
        }
    }

    private void recycle() throws IOException {
        synchronized (recyclingMonitor) {
            if (out != null && !out.isEmpty()) {
                out.close();
            }

            out = new StreamyBytesPersistentOutputStream(basePath, prefix, inputBuffer, deleteFilesOnClose);
            smileGenerator = smileFactory.createJsonGenerator(out, JsonEncoding.UTF8);
            // Drop the Smile header
            smileGenerator.flush();
            out.reset();

            samplesforTimestampWritten.set(0);
        }
    }

    //@MonitorableManaged(description = "Return the approximate size of bytes on disk for samples not yet in the database", monitored = true, monitoringType = {MonitoringType.VALUE})
    public long getBytesOnDisk() {
        return out.getBytesOnDisk();
    }

    //@MonitorableManaged(description = "Return the approximate size of bytes in memory for samples not yet in the database", monitored = true, monitoringType = {MonitoringType.VALUE})
    public long getBytesInMemory() {
        return out.getBytesInMemory();
    }

    //@MonitorableManaged(description = "Return the approximate size of bytes available in memory (before spilling over to disk) for samples not yet in the database", monitored = true, monitoringType = {MonitoringType.VALUE})
    public long getInMemoryAvailableSpace() {
        return out.getInMemoryAvailableSpace();
    }

    @VisibleForTesting
    public long getFilesCreated() {
        return out.getCreatedFiles().size();
    }
}
