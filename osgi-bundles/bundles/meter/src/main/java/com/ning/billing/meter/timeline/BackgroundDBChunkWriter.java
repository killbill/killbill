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

package com.ning.billing.meter.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.config.MeterConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * This class runs a thread that batch-writes TimelineChunks to the db.
 * This class is thread-safe, and only holds up threads that want to queue
 * TimelineChunks for the time it takes to copy the ArrayList of PendingChunkMaps.
 * <p/>
 * The background writing thread is scheduled every few seconds, as controlled by
 * config.getBackgroundWriteCheckInterval().  It writes the current inventory of
 * chunks if there are at least config.getBackgroundWriteBatchSize()
 * TimelineChunks to be written, or if the time since the last write exceeds
 * config.getBackgroundWriteMaxDelay().
 */
@Singleton
public class BackgroundDBChunkWriter {

    private static final Logger log = LoggerFactory.getLogger(BackgroundDBChunkWriter.class);

    private final TimelineDao timelineDAO;
    private final MeterConfig config;
    private final boolean performForegroundWrites;
    private final InternalCallContextFactory internalCallContextFactory;

    private final AtomicInteger pendingChunkCount = new AtomicInteger();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private List<PendingChunkMap> pendingChunks = new ArrayList<PendingChunkMap>();
    private DateTime lastWriteTime = new DateTime();
    private final AtomicBoolean doingWritesNow = new AtomicBoolean();
    private final ScheduledExecutorService backgroundWriteThread = Executors.newSingleThreadScheduledExecutor();

    private final AtomicLong maybePerformBackgroundWritesCount = new AtomicLong();
    private final AtomicLong backgroundWritesCount = new AtomicLong();
    private final AtomicLong pendingChunkMapsAdded = new AtomicLong();
    private final AtomicLong pendingChunksAdded = new AtomicLong();
    private final AtomicLong pendingChunkMapsWritten = new AtomicLong();
    private final AtomicLong pendingChunksWritten = new AtomicLong();
    private final AtomicLong pendingChunkMapsMarkedConsumed = new AtomicLong();
    private final AtomicLong foregroundChunkMapsWritten = new AtomicLong();
    private final AtomicLong foregroundChunksWritten = new AtomicLong();

    @Inject
    public BackgroundDBChunkWriter(final TimelineDao timelineDAO, final MeterConfig config, final InternalCallContextFactory internalCallContextFactory) {
        this(timelineDAO, config, config.getPerformForegroundWrites(), internalCallContextFactory);
    }

    public BackgroundDBChunkWriter(final TimelineDao timelineDAO, @Nullable final MeterConfig config,
                                   final boolean performForegroundWrites, final InternalCallContextFactory internalCallContextFactory) {
        this.timelineDAO = timelineDAO;
        this.config = config;
        this.performForegroundWrites = performForegroundWrites;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    public synchronized void addPendingChunkMap(final PendingChunkMap chunkMap) {
        if (shuttingDown.get()) {
            log.error("In addPendingChunkMap(), but finishBackgroundWritingAndExit is true!");
        } else {
            if (performForegroundWrites) {
                foregroundChunkMapsWritten.incrementAndGet();
                final List<TimelineChunk> chunksToWrite = new ArrayList<TimelineChunk>(chunkMap.getChunkMap().values());
                foregroundChunksWritten.addAndGet(chunksToWrite.size());
                timelineDAO.bulkInsertTimelineChunks(chunksToWrite, createCallContext());
                chunkMap.getAccumulator().markPendingChunkMapConsumed(chunkMap.getPendingChunkMapId());
            } else {
                pendingChunkMapsAdded.incrementAndGet();
                final int chunkCount = chunkMap.getChunkCount();
                pendingChunksAdded.addAndGet(chunkCount);
                pendingChunks.add(chunkMap);
                pendingChunkCount.addAndGet(chunkCount);
            }
        }
    }

    private void performBackgroundWrites() {
        backgroundWritesCount.incrementAndGet();
        List<PendingChunkMap> chunkMapsToWrite = null;
        synchronized (this) {
            chunkMapsToWrite = pendingChunks;
            pendingChunks = new ArrayList<PendingChunkMap>();
            pendingChunkCount.set(0);
        }
        final List<TimelineChunk> chunks = new ArrayList<TimelineChunk>();
        for (final PendingChunkMap map : chunkMapsToWrite) {
            pendingChunkMapsWritten.incrementAndGet();
            pendingChunksWritten.addAndGet(map.getChunkMap().size());
            chunks.addAll(map.getChunkMap().values());
        }
        timelineDAO.bulkInsertTimelineChunks(chunks, createCallContext());
        for (final PendingChunkMap map : chunkMapsToWrite) {
            pendingChunkMapsMarkedConsumed.incrementAndGet();
            map.getAccumulator().markPendingChunkMapConsumed(map.getPendingChunkMapId());
        }
    }

    private void maybePerformBackgroundWrites() {
        // If already running background writes, just return
        maybePerformBackgroundWritesCount.incrementAndGet();
        if (!doingWritesNow.compareAndSet(false, true)) {
            return;
        } else {
            try {
                if (shuttingDown.get()) {
                    performBackgroundWrites();
                }
                final int pendingCount = pendingChunkCount.get();
                if (pendingCount > 0) {
                    if (pendingCount >= config.getBackgroundWriteBatchSize() ||
                            new DateTime().isBefore(lastWriteTime.plusMillis((int) config.getBackgroundWriteMaxDelay().getMillis()))) {
                        performBackgroundWrites();
                        lastWriteTime = new DateTime();
                    }
                }
            } finally {
                doingWritesNow.set(false);
            }
        }
    }

    public synchronized boolean getShutdownFinished() {
        return !doingWritesNow.get() && pendingChunks.size() == 0;
    }

    public void initiateShutdown() {
        shuttingDown.set(true);
    }

    public void runBackgroundWriteThread() {
        if (!performForegroundWrites) {
            backgroundWriteThread.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    maybePerformBackgroundWrites();
                }
            },
                                                         config.getBackgroundWriteCheckInterval().getMillis(),
                                                         config.getBackgroundWriteCheckInterval().getMillis(),
                                                         TimeUnit.MILLISECONDS);
        }
    }

    public void stopBackgroundWriteThread() {
        if (!performForegroundWrites) {
            backgroundWriteThread.shutdown();
        }
    }

    public long getMaybePerformBackgroundWritesCount() {
        return maybePerformBackgroundWritesCount.get();
    }

    public long getBackgroundWritesCount() {
        return backgroundWritesCount.get();
    }

    public long getPendingChunkMapsAdded() {
        return pendingChunkMapsAdded.get();
    }

    public long getPendingChunksAdded() {
        return pendingChunksAdded.get();
    }

    public long getPendingChunkMapsWritten() {
        return pendingChunkMapsWritten.get();
    }

    public long getPendingChunksWritten() {
        return pendingChunksWritten.get();
    }

    public long getPendingChunkMapsMarkedConsumed() {
        return pendingChunkMapsMarkedConsumed.get();
    }

    public long getForegroundChunkMapsWritten() {
        return foregroundChunkMapsWritten.get();
    }

    public long getForegroundChunksWritten() {
        return foregroundChunksWritten.get();
    }

    private InternalCallContext createCallContext() {
        // TODO information about accountRecordId and tenatRecordId
        return internalCallContextFactory.createInternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, null, "ChunkWriter", CallOrigin.INTERNAL, UserType.SYSTEM, null);
    }
}
