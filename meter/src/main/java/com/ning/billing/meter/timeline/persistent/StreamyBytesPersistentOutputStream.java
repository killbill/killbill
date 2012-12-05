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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.util.membuf.StreamyBytesMemBuffer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

public class StreamyBytesPersistentOutputStream extends OutputStream {

    private static final Logger log = LoggerFactory.getLogger(StreamyBytesPersistentOutputStream.class);
    private static final int BUF_SIZE = 0x1000; // 4K

    private final String basePath;
    private final String prefix;
    private final StreamyBytesMemBuffer inputBuffer;
    private final boolean deleteFilesOnClose;
    private final List<String> createdFiles = new ArrayList<String>();

    private long bytesOnDisk = 0L;

    public StreamyBytesPersistentOutputStream(String basePath, final String prefix, final StreamyBytesMemBuffer inputBuffer, final boolean deleteFilesOnClose) {
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        this.basePath = basePath;

        this.prefix = prefix;
        this.inputBuffer = inputBuffer;
        this.deleteFilesOnClose = deleteFilesOnClose;
    }

    @Override
    public void write(final int b) throws IOException {
        final byte data = (byte) b;
        write(new byte[]{data}, 0, 1);
    }

    @Override
    public void write(final byte[] data, final int off, final int len) throws IOException {
        if (!inputBuffer.tryAppend(data, off, len)) {
            // Buffer full - need to flush
            // TODO sync with HTTP call, performance hit?
            flushUnderlyingBufferAndReset();

            if (!inputBuffer.tryAppend(data, off, len)) {
                log.warn("Unable to append data: 1 byte lost");
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Cleanup volatile data
        inputBuffer.clear();

        // Cleanup persistent data
        if (deleteFilesOnClose) {
            for (final String path : createdFiles) {
                log.info("Discarding file: {}", path);
                if (!new File(path).delete()) {
                    log.warn("Unable to discard file: {}", path);
                }
            }
        }
    }

    private void flushUnderlyingBufferAndReset() {
        synchronized (inputBuffer) {
            if (inputBuffer.available() == 0) {
                // Somebody beat you to it
                return;
            }

            final String pathname = getFileName();
            createdFiles.add(pathname);
            log.debug("Flushing in-memory buffer to disk: {}", pathname);

            try {
                final File out = new File(pathname);
                flushToFile(out);
            } catch (IOException e) {
                log.warn("Error flushing data", e);
            } finally {
                reset();
            }
        }
    }

    @VisibleForTesting
    String getFileName() {
        return basePath + "killbill." + prefix + "." + System.nanoTime() + ".bin";
    }

    private void flushToFile(final File out) throws IOException {
        final byte[] buf = new byte[BUF_SIZE];
        FileOutputStream transfer = null;

        int bytesTransferred = 0;
        try {
            transfer = Files.newOutputStreamSupplier(out).getOutput();

            while (true) {
                final int r = inputBuffer.readIfAvailable(buf);
                if (r == 0) {
                    break;
                }
                transfer.write(buf, 0, r);
                bytesTransferred += r;
            }
        } finally {
            if (transfer != null) {
                try {
                    transfer.write(SmileConstants.TOKEN_LITERAL_END_ARRAY);
                    bytesTransferred++;
                    bytesOnDisk += bytesTransferred;
                } finally {
                    transfer.flush();
                }
            }
        }
        log.debug("Saved {} bytes to disk", bytesTransferred);
    }

    public void reset() {
        inputBuffer.clear();
        try {
            write(SmileConstants.TOKEN_LITERAL_START_ARRAY);
        } catch (IOException e) {
            // Not sure how to recover?
        }
    }

    public List<String> getCreatedFiles() {
        return createdFiles;
    }

    public long getBytesOnDisk() {
        return bytesOnDisk;
    }

    public long getBytesInMemory() {
        return inputBuffer.getTotalPayloadLength();
    }

    public long getInMemoryAvailableSpace() {
        return inputBuffer.getMaximumAvailableSpace();
    }

    public boolean isEmpty() {
        return inputBuffer.isEmpty();
    }
}
