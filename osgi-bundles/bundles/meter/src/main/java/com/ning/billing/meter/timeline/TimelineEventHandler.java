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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.persistent.FileBackedBuffer;
import com.ning.billing.meter.timeline.persistent.Replayer;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.shutdown.ShutdownSaveMode;
import com.ning.billing.meter.timeline.shutdown.StartTimes;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.config.MeterConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class TimelineEventHandler {

    private static final Logger log = LoggerFactory.getLogger(TimelineEventHandler.class);
    private final ScheduledExecutorService purgeThread = Executors.newSingleThreadScheduledExecutor();
    private static final Comparator<TimelineChunk> CHUNK_COMPARATOR = new Comparator<TimelineChunk>() {

        @Override
        public int compare(final TimelineChunk o1, final TimelineChunk o2) {
            final int hostDiff = o1.getSourceId() - o1.getSourceId();
            if (hostDiff < 0) {
                return -1;
            } else if (hostDiff > 0) {
                return 1;
            } else {
                final int metricIdDiff = o1.getMetricId() - o2.getMetricId();
                if (metricIdDiff < 0) {
                    return -1;
                } else if (metricIdDiff > 0) {
                    return 1;
                } else {
                    final long startTimeDiff = o1.getStartTime().getMillis() - o2.getStartTime().getMillis();
                    if (startTimeDiff < 0) {
                        return -1;
                    } else if (startTimeDiff > 0) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            }
        }
    };

    // A TimelineSourceEventAccumulator records attributes for a specific host and event type.
    // This cache maps sourceId -> categoryId -> accumulator
    //
    // TODO: There are still timing windows in the use of accumulators.  Enumerate them and
    // either fix them or prove they are benign
    private final Map<Integer, SourceAccumulatorsAndUpdateDate> accumulators = new ConcurrentHashMap<Integer, SourceAccumulatorsAndUpdateDate>();

    private final MeterConfig config;
    private final TimelineDao timelineDAO;
    private final TimelineCoder timelineCoder;
    private final SampleCoder sampleCoder;
    private final BackgroundDBChunkWriter backgroundWriter;
    private final FileBackedBuffer backingBuffer;

    private final ShutdownSaveMode shutdownSaveMode;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private final AtomicBoolean replaying = new AtomicBoolean();

    private final AtomicLong eventsDiscarded = new AtomicLong(0L);
    private final AtomicLong eventsReceivedAfterShuttingDown = new AtomicLong();
    private final AtomicLong handledEventCount = new AtomicLong();
    private final AtomicLong addedSourceEventAccumulatorMapCount = new AtomicLong();
    private final AtomicLong addedSourceEventAccumulatorCount = new AtomicLong();
    private final AtomicLong getInMemoryChunksCallCount = new AtomicLong();
    private final AtomicLong accumulatorDeepCopyCount = new AtomicLong();
    private final AtomicLong inMemoryChunksReturnedCount = new AtomicLong();
    private final AtomicLong replayCount = new AtomicLong();
    private final AtomicLong replaySamplesFoundCount = new AtomicLong();
    private final AtomicLong replaySamplesOutsideTimeRangeCount = new AtomicLong();
    private final AtomicLong replaySamplesProcessedCount = new AtomicLong();
    private final AtomicLong forceCommitCallCount = new AtomicLong();
    private final AtomicLong purgedAccumsBecauseSourceNotUpdated = new AtomicLong();
    private final AtomicLong purgedAccumsBecauseCategoryNotUpdated = new AtomicLong();

    @Inject
    public TimelineEventHandler(final MeterConfig config, final TimelineDao timelineDAO, final TimelineCoder timelineCoder, final SampleCoder sampleCoder, final BackgroundDBChunkWriter backgroundWriter, final FileBackedBuffer fileBackedBuffer) {
        this.config = config;
        this.timelineDAO = timelineDAO;
        this.timelineCoder = timelineCoder;
        this.sampleCoder = sampleCoder;
        this.backgroundWriter = backgroundWriter;
        this.backingBuffer = fileBackedBuffer;
        this.shutdownSaveMode = ShutdownSaveMode.fromString(config.getShutdownSaveMode());
    }

    private void saveAccumulators() {
        for (final Map.Entry<Integer, SourceAccumulatorsAndUpdateDate> entry : accumulators.entrySet()) {
            final int sourceId = entry.getKey();
            final Map<Integer, TimelineSourceEventAccumulator> hostAccumulators = entry.getValue().getCategoryAccumulators();
            for (final Map.Entry<Integer, TimelineSourceEventAccumulator> accumulatorEntry : hostAccumulators.entrySet()) {
                final int categoryId = accumulatorEntry.getKey();
                final TimelineSourceEventAccumulator accumulator = accumulatorEntry.getValue();
                log.debug("Saving Timeline for sourceId [{}] and categoryId [{}]", sourceId, categoryId);
                accumulator.extractAndQueueTimelineChunks();
            }
        }
    }

    private void saveStartTimes(final StartTimes startTimes) {
        for (final Map.Entry<Integer, SourceAccumulatorsAndUpdateDate> entry : accumulators.entrySet()) {
            final int sourceId = entry.getKey();
            final Map<Integer, TimelineSourceEventAccumulator> hostAccumulators = entry.getValue().getCategoryAccumulators();
            for (final Map.Entry<Integer, TimelineSourceEventAccumulator> accumulatorEntry : hostAccumulators.entrySet()) {
                final int categoryId = accumulatorEntry.getKey();
                final TimelineSourceEventAccumulator accumulator = accumulatorEntry.getValue();
                log.debug("Saving Timeline start time for sourceId [{}] and category [{}]", sourceId, categoryId);
                startTimes.addTime(sourceId, categoryId, accumulator.getStartTime());
            }
        }
    }

    public synchronized void purgeOldSourcesAndAccumulators(final DateTime purgeIfBeforeDate) {
        final List<Integer> oldSourceIds = new ArrayList<Integer>();
        for (final Map.Entry<Integer, SourceAccumulatorsAndUpdateDate> entry : accumulators.entrySet()) {
            final int sourceId = entry.getKey();
            final SourceAccumulatorsAndUpdateDate accumulatorsAndDate = entry.getValue();
            final DateTime lastUpdatedDate = accumulatorsAndDate.getLastUpdateDate();
            if (lastUpdatedDate.isBefore(purgeIfBeforeDate)) {
                oldSourceIds.add(sourceId);
                purgedAccumsBecauseSourceNotUpdated.incrementAndGet();
                for (final TimelineSourceEventAccumulator categoryAccumulator : accumulatorsAndDate.getCategoryAccumulators().values()) {
                    categoryAccumulator.extractAndQueueTimelineChunks();
                }
            } else {
                final List<Integer> categoryIdsToPurge = new ArrayList<Integer>();
                final Map<Integer, TimelineSourceEventAccumulator> categoryMap = accumulatorsAndDate.getCategoryAccumulators();
                for (final Map.Entry<Integer, TimelineSourceEventAccumulator> eventEntry : categoryMap.entrySet()) {
                    final int categoryId = eventEntry.getKey();
                    final TimelineSourceEventAccumulator categoryAccumulator = eventEntry.getValue();
                    final DateTime latestTime = categoryAccumulator.getLatestSampleAddTime();
                    if (latestTime != null && latestTime.isBefore(purgeIfBeforeDate)) {
                        purgedAccumsBecauseCategoryNotUpdated.incrementAndGet();
                        categoryAccumulator.extractAndQueueTimelineChunks();
                        categoryIdsToPurge.add(categoryId);
                    }
                }
                for (final int categoryId : categoryIdsToPurge) {
                    categoryMap.remove(categoryId);
                }
            }
        }
        for (final int sourceIdToPurge : oldSourceIds) {
            accumulators.remove(sourceIdToPurge);
        }
    }

    /**
     * Main entry point to the timeline subsystem. Record a series of sample for a given source, at a given timestamp.
     *
     * @param sourceName     name of the source
     * @param eventType      event category
     * @param eventTimestamp event timestamp
     * @param samples        samples to record
     * @param context        the call context
     */
    public void record(final String sourceName, final String eventType, final DateTime eventTimestamp,
                       final Map<String, Object> samples, final InternalCallContext context) {
        if (shuttingDown.get()) {
            eventsReceivedAfterShuttingDown.incrementAndGet();
            return;
        }
        try {
            handledEventCount.incrementAndGet();

            // Find the sourceId
            final int sourceId = timelineDAO.getOrAddSource(sourceName, context);

            // Extract and parse samples
            final Map<Integer, ScalarSample> scalarSamples = new LinkedHashMap<Integer, ScalarSample>();
            convertSamplesToScalarSamples(eventType, samples, scalarSamples, context);

            if (scalarSamples.isEmpty()) {
                eventsDiscarded.incrementAndGet();
                return;
            }

            final SourceSamplesForTimestamp sourceSamples = new SourceSamplesForTimestamp(sourceId, eventType, eventTimestamp, scalarSamples);
            if (!replaying.get() && config.storeSamplesLocallyTemporary()) {
                // Start by saving locally the samples
                backingBuffer.append(sourceSamples);
            }
            // Then add them to the in-memory accumulator
            processSamples(sourceSamples, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TimelineSourceEventAccumulator getOrAddSourceEventAccumulator(final int sourceId, final int categoryId, final DateTime firstSampleTime) {
        return this.getOrAddSourceEventAccumulator(sourceId, categoryId, firstSampleTime, (int) config.getTimelineLength().getMillis());
    }

    public synchronized TimelineSourceEventAccumulator getOrAddSourceEventAccumulator(final int sourceId, final int categoryId, final DateTime firstSampleTime, final int timelineLengthMillis) {
        SourceAccumulatorsAndUpdateDate sourceAccumulatorsAndUpdateDate = accumulators.get(sourceId);
        if (sourceAccumulatorsAndUpdateDate == null) {
            addedSourceEventAccumulatorMapCount.incrementAndGet();
            sourceAccumulatorsAndUpdateDate = new SourceAccumulatorsAndUpdateDate(new HashMap<Integer, TimelineSourceEventAccumulator>(), new DateTime());
            accumulators.put(sourceId, sourceAccumulatorsAndUpdateDate);
        }
        sourceAccumulatorsAndUpdateDate.markUpdated();
        final Map<Integer, TimelineSourceEventAccumulator> hostCategoryAccumulators = sourceAccumulatorsAndUpdateDate.getCategoryAccumulators();
        TimelineSourceEventAccumulator accumulator = hostCategoryAccumulators.get(categoryId);
        if (accumulator == null) {
            addedSourceEventAccumulatorCount.incrementAndGet();
            accumulator = new TimelineSourceEventAccumulator(timelineDAO, timelineCoder, sampleCoder, backgroundWriter, sourceId, categoryId, firstSampleTime, timelineLengthMillis);
            hostCategoryAccumulators.put(categoryId, accumulator);
            log.debug("Created new Timeline for sourceId [{}] and category [{}]", sourceId, categoryId);
        }
        return accumulator;
    }

    @VisibleForTesting
    public void processSamples(final SourceSamplesForTimestamp hostSamples, final InternalTenantContext context) throws ExecutionException, IOException {
        final int sourceId = hostSamples.getSourceId();
        final String category = hostSamples.getCategory();
        final int categoryId = timelineDAO.getEventCategoryId(category, context);
        final DateTime timestamp = hostSamples.getTimestamp();
        final TimelineSourceEventAccumulator accumulator = getOrAddSourceEventAccumulator(sourceId, categoryId, timestamp);
        accumulator.addSourceSamples(hostSamples);
    }

    public Collection<? extends TimelineChunk> getInMemoryTimelineChunks(final Integer sourceId, @Nullable final DateTime filterStartTime,
                                                                         @Nullable final DateTime filterEndTime, final InternalTenantContext context) throws IOException, ExecutionException {
        return getInMemoryTimelineChunks(sourceId, ImmutableList.copyOf(timelineDAO.getMetrics(context).keySet()), filterStartTime, filterEndTime, context);
    }

    public Collection<? extends TimelineChunk> getInMemoryTimelineChunks(final Integer sourceId, final Integer metricId, @Nullable final DateTime filterStartTime,
                                                                         @Nullable final DateTime filterEndTime, final InternalTenantContext context) throws IOException, ExecutionException {
        return getInMemoryTimelineChunks(sourceId, ImmutableList.<Integer>of(metricId), filterStartTime, filterEndTime, context);
    }

    public synchronized Collection<? extends TimelineChunk> getInMemoryTimelineChunks(final Integer sourceId, final List<Integer> metricIds,
                                                                                      @Nullable final DateTime filterStartTime, @Nullable final DateTime filterEndTime,
                                                                                      final InternalTenantContext context) throws IOException, ExecutionException {
        getInMemoryChunksCallCount.incrementAndGet();
        // Check first if there is an in-memory accumulator for this host
        final SourceAccumulatorsAndUpdateDate sourceAccumulatorsAndDate = accumulators.get(sourceId);
        if (sourceAccumulatorsAndDate == null) {
            return ImmutableList.of();
        }

        // Now, filter each accumulator for this host
        final List<TimelineChunk> samplesBySourceName = new ArrayList<TimelineChunk>();
        for (final TimelineSourceEventAccumulator accumulator : sourceAccumulatorsAndDate.getCategoryAccumulators().values()) {
            // Check if the time filters apply
            if ((filterStartTime != null && accumulator.getEndTime().isBefore(filterStartTime)) || (filterEndTime != null && accumulator.getStartTime().isAfter(filterEndTime))) {
                // Nope - ignore this accumulator
                continue;
            }

            samplesBySourceName.addAll(accumulator.getInMemoryTimelineChunks(metricIds));
        }
        inMemoryChunksReturnedCount.addAndGet(samplesBySourceName.size());
        Collections.sort(samplesBySourceName, CHUNK_COMPARATOR);
        return samplesBySourceName;
    }

    @VisibleForTesting
    void convertSamplesToScalarSamples(final String eventType, final Map<String, Object> inputSamples,
                                       final Map<Integer, ScalarSample> outputSamples, final InternalCallContext context) {
        if (inputSamples == null) {
            return;
        }
        final Integer eventCategoryId = timelineDAO.getOrAddEventCategory(eventType, context);

        for (final String attributeName : inputSamples.keySet()) {
            final Integer metricId = timelineDAO.getOrAddMetric(eventCategoryId, attributeName, context);
            final Object sample = inputSamples.get(attributeName);

            outputSamples.put(metricId, ScalarSample.fromObject(sample));
        }
    }

    public void replay(final String spoolDir, final InternalCallContext context) {
        replayCount.incrementAndGet();
        log.info("Starting replay of files in {}", spoolDir);
        final Replayer replayer = new Replayer(spoolDir);
        StartTimes lastStartTimes = null;
        if (shutdownSaveMode == ShutdownSaveMode.SAVE_START_TIMES) {
            lastStartTimes = timelineDAO.getLastStartTimes(context);
            if (lastStartTimes == null) {
                log.info("Did not find startTimes");
            } else {
                log.info("Retrieved startTimes from the db");
            }
        }
        final StartTimes startTimes = lastStartTimes;
        final DateTime minStartTime = lastStartTimes == null ? null : startTimes.getMinStartTime();
        final long found = replaySamplesFoundCount.get();
        final long outsideTimeRange = replaySamplesOutsideTimeRangeCount.get();
        final long processed = replaySamplesProcessedCount.get();

        try {
            // Read all files in the spool directory and delete them after process, if
            // startTimes  is null.
            replaying.set(true);
            final int filesSkipped = replayer.readAll(startTimes == null, minStartTime, new Function<SourceSamplesForTimestamp, Void>() {
                @Override
                public Void apply(@Nullable final SourceSamplesForTimestamp hostSamples) {
                    if (hostSamples != null) {
                        replaySamplesFoundCount.incrementAndGet();
                        boolean useSamples = true;
                        try {
                            final int sourceId = hostSamples.getSourceId();
                            final String category = hostSamples.getCategory();
                            final int categoryId = timelineDAO.getEventCategoryId(category, context);
                            // If startTimes is non-null and the samples come from before the first time for
                            // the given host and event category, ignore the samples
                            if (startTimes != null) {
                                final DateTime timestamp = hostSamples.getTimestamp();
                                final DateTime categoryStartTime = startTimes.getStartTimeForSourceIdAndCategoryId(sourceId, categoryId);
                                if (timestamp == null ||
                                    timestamp.isBefore(startTimes.getMinStartTime()) ||
                                    (categoryStartTime != null && timestamp.isBefore(categoryStartTime))) {
                                    replaySamplesOutsideTimeRangeCount.incrementAndGet();
                                    useSamples = false;
                                }
                            }
                            if (useSamples) {
                                replaySamplesProcessedCount.incrementAndGet();
                                processSamples(hostSamples, context);
                            }
                        } catch (Exception e) {
                            log.warn("Got exception replaying sample, data potentially lost! {}", hostSamples.toString());
                        }
                    }

                    return null;
                }
            });
            if (shutdownSaveMode == ShutdownSaveMode.SAVE_START_TIMES) {
                timelineDAO.deleteLastStartTimes(context);
                log.info("Deleted old startTimes");
            }
            log.info(String.format("Replay completed; %d files skipped, samples read %d, samples outside time range %d, samples used %d",
                                   filesSkipped, replaySamplesFoundCount.get() - found, replaySamplesOutsideTimeRangeCount.get() - outsideTimeRange, replaySamplesProcessedCount.get() - processed));
        } catch (RuntimeException e) {
            // Catch the exception to make the collector start properly
            log.error("Ignoring error when replaying the data", e);
        } finally {
            replaying.set(false);
        }
    }

    public void forceCommit() {
        forceCommitCallCount.incrementAndGet();
        saveAccumulators();
        discardBackingBuffer();
        log.info("Timelines committed");
    }

    public void commitAndShutdown(final InternalCallContext context) {
        shuttingDown.set(true);
        final boolean doingFastShutdown = shutdownSaveMode == ShutdownSaveMode.SAVE_START_TIMES;
        if (doingFastShutdown) {
            final StartTimes startTimes = new StartTimes();
            saveStartTimes(startTimes);
            timelineDAO.insertLastStartTimes(startTimes, context);
            log.info("During shutdown, saved timeline start times in the db");
        } else {
            saveAccumulators();
            log.info("During shutdown, saved timeline accumulators");
        }
        performShutdown();
        discardBackingBuffer();
    }

    private void discardBackingBuffer() {
        if (config.storeSamplesLocallyTemporary()) {
            backingBuffer.discard();
        }
    }

    private void performShutdown() {
        backgroundWriter.initiateShutdown();
        while (!backgroundWriter.getShutdownFinished()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        purgeThread.shutdown();
    }

    private synchronized void purgeFilesAndAccumulators() {
        this.purgeFilesAndAccumulators(new DateTime().minus(config.getTimelineLength().getMillis()), new DateTime().minus(2 * config.getTimelineLength().getMillis()));
    }

    // TODO: We have a bad interaction between startTimes and purging: If the system is down
    // for two hours, we may not want it to purge everything.  Figure out what to do about this.
    private synchronized void purgeFilesAndAccumulators(final DateTime purgeAccumulatorsIfBefore, final DateTime purgeFilesIfBefore) {
        purgeOldSourcesAndAccumulators(purgeAccumulatorsIfBefore);
        final Replayer replayer = new Replayer(config.getSpoolDir());
        replayer.purgeOldFiles(purgeFilesIfBefore);
    }

    public void startPurgeThread() {
        purgeThread.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                purgeFilesAndAccumulators();
            }
        }, config.getTimelineLength().getMillis(),
                                           config.getTimelineLength().getMillis(),
                                           TimeUnit.MILLISECONDS);
    }

    // We use the lastUpdateDate to purge sources and their accumulators from the map
    private static class SourceAccumulatorsAndUpdateDate {

        private final Map<Integer, TimelineSourceEventAccumulator> categoryAccumulators;
        private DateTime lastUpdateDate;

        public SourceAccumulatorsAndUpdateDate(final Map<Integer, TimelineSourceEventAccumulator> categoryAccumulators, final DateTime lastUpdateDate) {
            this.categoryAccumulators = categoryAccumulators;
            this.lastUpdateDate = lastUpdateDate;
        }

        public Map<Integer, TimelineSourceEventAccumulator> getCategoryAccumulators() {
            return categoryAccumulators;
        }

        public DateTime getLastUpdateDate() {
            return lastUpdateDate;
        }

        public void markUpdated() {
            lastUpdateDate = new DateTime();
        }
    }

    @VisibleForTesting
    public Collection<TimelineSourceEventAccumulator> getAccumulators() {
        final List<TimelineSourceEventAccumulator> inMemoryAccumulator = new ArrayList<TimelineSourceEventAccumulator>();
        for (final SourceAccumulatorsAndUpdateDate sourceEventAccumulatorMap : accumulators.values()) {
            inMemoryAccumulator.addAll(sourceEventAccumulatorMap.getCategoryAccumulators().values());
        }

        return inMemoryAccumulator;
    }

    @VisibleForTesting
    public FileBackedBuffer getBackingBuffer() {
        return backingBuffer;
    }

    public long getEventsDiscarded() {
        return eventsDiscarded.get();
    }

    public long getSourceEventAccumulatorCount() {
        return accumulators.size();
    }

    public long getEventsReceivedAfterShuttingDown() {
        return eventsReceivedAfterShuttingDown.get();
    }

    public long getHandledEventCount() {
        return handledEventCount.get();
    }

    public long getAddedSourceEventAccumulatorMapCount() {
        return addedSourceEventAccumulatorMapCount.get();
    }

    public long getAddedSourceEventAccumulatorCount() {
        return addedSourceEventAccumulatorCount.get();
    }

    public long getGetInMemoryChunksCallCount() {
        return getInMemoryChunksCallCount.get();
    }

    public long getAccumulatorDeepCopyCount() {
        return accumulatorDeepCopyCount.get();
    }

    public long getInMemoryChunksReturnedCount() {
        return inMemoryChunksReturnedCount.get();
    }

    public long getReplayCount() {
        return replayCount.get();
    }

    public long getReplaySamplesFoundCount() {
        return replaySamplesFoundCount.get();
    }

    public long getReplaySamplesOutsideTimeRangeCount() {
        return replaySamplesOutsideTimeRangeCount.get();
    }

    public long getReplaySamplesProcessedCount() {
        return replaySamplesProcessedCount.get();
    }

    public long getForceCommitCallCount() {
        return forceCommitCallCount.get();
    }

    public long getPurgedAccumsBecauseSourceNotUpdated() {
        return purgedAccumsBecauseSourceNotUpdated.get();
    }

    public long getPurgedAccumsBecauseCategoryNotUpdated() {
        return purgedAccumsBecauseCategoryNotUpdated.get();
    }
}
