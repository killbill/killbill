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

package com.ning.billing.meter.timeline.aggregator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.sqlobject.stringtemplate.StringTemplate3StatementLocator;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.chunks.TimelineChunkMapper;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.config.MeterConfig;

import com.google.inject.Inject;

/**
 * This class runs a thread that periodically looks for unaggregated timelines.
 * When it finds them, it combines them intelligently as if they were originally
 * a single sequence of times.
 */
public class TimelineAggregator {

    private static final Logger log = LoggerFactory.getLogger(TimelineAggregator.class);

    private final IDBI dbi;
    private final TimelineDao timelineDao;
    private final TimelineCoder timelineCoder;
    private final SampleCoder sampleCoder;
    private final MeterConfig config;
    private final TimelineAggregatorSqlDao aggregatorSqlDao;
    private final TimelineChunkMapper timelineChunkMapper;
    private final InternalCallContextFactory internalCallContextFactory;

    private final ScheduledExecutorService aggregatorThread = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, AtomicLong> aggregatorCounters = new LinkedHashMap<String, AtomicLong>();

    private final AtomicBoolean isAggregating = new AtomicBoolean(false);

    private final AtomicLong aggregationRuns = new AtomicLong();
    private final AtomicLong foundNothingRuns = new AtomicLong();
    private final AtomicLong aggregatesCreated = makeCounter("aggsCreated");
    private final AtomicLong timelineChunksConsidered = makeCounter("chunksConsidered");
    private final AtomicLong timelineChunkBatchesProcessed = makeCounter("batchesProcessed");
    private final AtomicLong timelineChunksCombined = makeCounter("chunksCombined");
    private final AtomicLong timelineChunksQueuedForCreation = makeCounter("chunksQueued");
    private final AtomicLong timelineChunksWritten = makeCounter("chunksWritten");
    private final AtomicLong timelineChunksInvalidatedOrDeleted = makeCounter("chunksInvalidatedOrDeleted");
    private final AtomicLong timelineChunksBytesCreated = makeCounter("bytesCreated");
    private final AtomicLong msSpentAggregating = makeCounter("msSpentAggregating");
    private final AtomicLong msSpentSleeping = makeCounter("msSpentSleeping");
    private final AtomicLong msWritingDb = makeCounter("msWritingDb");

    // These lists support batching of aggregated chunk writes and updates or deletes of the chunks aggregated
    private final List<TimelineChunk> chunksToWrite = new ArrayList<TimelineChunk>();
    private final List<Long> chunkIdsToInvalidateOrDelete = new ArrayList<Long>();

    @Inject
    public TimelineAggregator(final IDBI dbi, final TimelineDao timelineDao, final TimelineCoder timelineCoder,
                              final SampleCoder sampleCoder, final MeterConfig config, final InternalCallContextFactory internalCallContextFactory) {
        this.dbi = dbi;
        this.timelineDao = timelineDao;
        this.timelineCoder = timelineCoder;
        this.sampleCoder = sampleCoder;
        this.config = config;
        this.aggregatorSqlDao = dbi.onDemand(TimelineAggregatorSqlDao.class);
        this.timelineChunkMapper = new TimelineChunkMapper();
        this.internalCallContextFactory = internalCallContextFactory;
    }

    private int aggregateTimelineCandidates(final List<TimelineChunk> timelineChunkCandidates, final int aggregationLevel, final int chunksToAggregate) {
        final TimelineChunk firstCandidate = timelineChunkCandidates.get(0);
        final int sourceId = firstCandidate.getSourceId();
        final int metricId = firstCandidate.getMetricId();
        log.debug("For sourceId {}, metricId {}, looking to aggregate {} candidates in {} chunks",
                  new Object[]{sourceId, metricId, timelineChunkCandidates.size(), chunksToAggregate});
        int aggregatesCreated = 0;
        int chunkIndex = 0;
        while (timelineChunkCandidates.size() >= chunkIndex + chunksToAggregate) {
            final List<TimelineChunk> chunkCandidates = timelineChunkCandidates.subList(chunkIndex, chunkIndex + chunksToAggregate);
            chunkIndex += chunksToAggregate;
            timelineChunksCombined.addAndGet(chunksToAggregate);
            try {
                aggregateHostSampleChunks(chunkCandidates, aggregationLevel);
            } catch (IOException e) {
                log.error(String.format("IOException aggregating {} chunks, sourceId %s, metricId %s, looking to aggregate %s candidates in %s chunks",
                                        new Object[]{firstCandidate.getSourceId(), firstCandidate.getMetricId(), timelineChunkCandidates.size(), chunksToAggregate}), e);
            }
            aggregatesCreated++;
        }

        return aggregatesCreated;
    }

    /**
     * The sequence of events is:
     * <ul>
     * <li>Build the aggregated TimelineChunk object, and save it, setting not_valid to true, and
     * aggregation_level to 1.  This means that it won't be noticed by any of the dashboard
     * queries.  The save operation returns the new timeline_times_id</li>
     * <li>Then, in a single transaction, update the aggregated TimelineChunk object to have not_valid = 0,
     * and also delete the TimelineChunk objects that were the basis of the aggregation, and flush
     * any TimelineChunks that happen to be in the cache.</li>
     * <p/>
     *
     * @param timelineChunks the TimelineChunks to be aggregated
     */
    private void aggregateHostSampleChunks(final List<TimelineChunk> timelineChunks, final int aggregationLevel) throws IOException {
        final TimelineChunk firstTimesChunk = timelineChunks.get(0);
        final TimelineChunk lastTimesChunk = timelineChunks.get(timelineChunks.size() - 1);
        final int chunkCount = timelineChunks.size();
        final int sourceId = firstTimesChunk.getSourceId();
        final DateTime startTime = firstTimesChunk.getStartTime();
        final DateTime endTime = lastTimesChunk.getEndTime();
        final List<byte[]> timeParts = new ArrayList<byte[]>(chunkCount);
        try {
            final List<byte[]> sampleParts = new ArrayList<byte[]>(chunkCount);
            final List<Long> timelineChunkIds = new ArrayList<Long>(chunkCount);
            int sampleCount = 0;
            for (final TimelineChunk timelineChunk : timelineChunks) {
                timeParts.add(timelineChunk.getTimeBytesAndSampleBytes().getTimeBytes());
                sampleParts.add(timelineChunk.getTimeBytesAndSampleBytes().getSampleBytes());
                sampleCount += timelineChunk.getSampleCount();
                timelineChunkIds.add(timelineChunk.getChunkId());
            }
            final byte[] combinedTimeBytes = timelineCoder.combineTimelines(timeParts, sampleCount);
            final byte[] combinedSampleBytes = sampleCoder.combineSampleBytes(sampleParts);
            final int timeBytesLength = combinedTimeBytes.length;
            final int totalSize = 4 + timeBytesLength + combinedSampleBytes.length;
            log.debug("For sourceId {}, aggregationLevel {}, aggregating {} timelines ({} bytes, {} samples): {}",
                      new Object[]{firstTimesChunk.getSourceId(), firstTimesChunk.getAggregationLevel(), timelineChunks.size(), totalSize, sampleCount});
            timelineChunksBytesCreated.addAndGet(totalSize);
            final int totalSampleCount = sampleCount;
            final TimelineChunk chunk = new TimelineChunk(0, sourceId, firstTimesChunk.getMetricId(), startTime, endTime,
                                                          combinedTimeBytes, combinedSampleBytes, totalSampleCount, aggregationLevel + 1, false, false);
            chunksToWrite.add(chunk);
            chunkIdsToInvalidateOrDelete.addAll(timelineChunkIds);
            timelineChunksQueuedForCreation.incrementAndGet();

            if (chunkIdsToInvalidateOrDelete.size() >= config.getMaxChunkIdsToInvalidateOrDelete()) {
                performWrites();
            }
        } catch (Exception e) {
            log.error(String.format("Exception aggregating level %d, sourceId %d, metricId %d, startTime %s, endTime %s",
                                    aggregationLevel, sourceId, firstTimesChunk.getMetricId(), startTime, endTime), e);
        }
    }

    private void performWrites() {
        final InternalCallContext context = createCallContext();

        // This is the atomic operation: bulk insert the new aggregated TimelineChunk objects, and delete
        // or invalidate the ones that were aggregated.  This should be very fast.
        final long startWriteTime = System.currentTimeMillis();
        aggregatorSqlDao.begin();
        timelineDao.bulkInsertTimelineChunks(chunksToWrite, context);
        if (config.getDeleteAggregatedChunks()) {
            aggregatorSqlDao.deleteTimelineChunks(chunkIdsToInvalidateOrDelete, context);
        } else {
            aggregatorSqlDao.makeTimelineChunksInvalid(chunkIdsToInvalidateOrDelete, context);
        }
        aggregatorSqlDao.commit();
        msWritingDb.addAndGet(System.currentTimeMillis() - startWriteTime);

        timelineChunksWritten.addAndGet(chunksToWrite.size());
        timelineChunksInvalidatedOrDeleted.addAndGet(chunkIdsToInvalidateOrDelete.size());
        chunksToWrite.clear();
        chunkIdsToInvalidateOrDelete.clear();
        final long sleepMs = config.getAggregationSleepBetweenBatches().getMillis();
        if (sleepMs > 0) {
            final long timeBeforeSleep = System.currentTimeMillis();
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            msSpentSleeping.addAndGet(System.currentTimeMillis() - timeBeforeSleep);
        }
        timelineChunkBatchesProcessed.incrementAndGet();
    }

    /**
     * This method aggregates candidate timelines
     */
    public void getAndProcessTimelineAggregationCandidates() {
        if (!isAggregating.compareAndSet(false, true)) {
            log.info("Asked to aggregate, but we're already aggregating!");
            return;
        } else {
            log.debug("Starting aggregating");
        }

        aggregationRuns.incrementAndGet();
        final String[] chunkCountsToAggregate = config.getChunksToAggregate().split(",");
        for (int aggregationLevel = 0; aggregationLevel < config.getMaxAggregationLevel(); aggregationLevel++) {
            final long startingAggregatesCreated = aggregatesCreated.get();
            final Map<String, Long> initialCounters = captureAggregatorCounters();
            final int chunkCountIndex = aggregationLevel >= chunkCountsToAggregate.length ? chunkCountsToAggregate.length - 1 : aggregationLevel;
            final int chunksToAggregate = Integer.parseInt(chunkCountsToAggregate[chunkCountIndex]);
            streamingAggregateLevel(aggregationLevel, chunksToAggregate);
            final Map<String, Long> counterDeltas = subtractFromAggregatorCounters(initialCounters);
            final long netAggregatesCreated = aggregatesCreated.get() - startingAggregatesCreated;
            if (netAggregatesCreated == 0) {
                if (aggregationLevel == 0) {
                    foundNothingRuns.incrementAndGet();
                }
                log.debug("Created no new aggregates, so skipping higher-level aggregations");
                break;
            } else {
                final StringBuilder builder = new StringBuilder();
                builder
                        .append("For aggregation level ")
                        .append(aggregationLevel)
                        .append(", runs ")
                        .append(aggregationRuns.get())
                        .append(", foundNothingRuns ")
                        .append(foundNothingRuns.get());
                for (final Map.Entry<String, Long> entry : counterDeltas.entrySet()) {
                    builder.append(", ").append(entry.getKey()).append(": ").append(entry.getValue());
                }
                log.info(builder.toString());
            }
        }

        log.debug("Aggregation done");
        isAggregating.set(false);
    }

    private void streamingAggregateLevel(final int aggregationLevel, final int chunksToAggregate) {
        final List<TimelineChunk> sourceTimelineCandidates = new ArrayList<TimelineChunk>();
        final TimelineChunkConsumer aggregationConsumer = new TimelineChunkConsumer() {

            int lastSourceId = 0;
            int lastMetricId = 0;

            @Override
            public void processTimelineChunk(final TimelineChunk candidate) {
                timelineChunksConsidered.incrementAndGet();
                final int sourceId = candidate.getSourceId();
                final int metricId = candidate.getMetricId();
                if (lastSourceId == 0) {
                    lastSourceId = sourceId;
                    lastMetricId = metricId;
                }
                if (lastSourceId != sourceId || lastMetricId != metricId) {
                    aggregatesCreated.addAndGet(aggregateTimelineCandidates(sourceTimelineCandidates, aggregationLevel, chunksToAggregate));
                    sourceTimelineCandidates.clear();
                    lastSourceId = sourceId;
                    lastMetricId = metricId;
                }
                sourceTimelineCandidates.add(candidate);
            }
        };
        final long startTime = System.currentTimeMillis();
        try {
            dbi.withHandle(new HandleCallback<Void>() {

                @Override
                public Void withHandle(final Handle handle) throws Exception {
                    // MySQL needs special setup to make it stream the results. See:
                    // http://javaquirks.blogspot.com/2007/12/mysql-streaming-result-set.html
                    // http://stackoverflow.com/questions/2447324/streaming-large-result-sets-with-mysql
                    final Query<Map<String, Object>> query = handle.createQuery("getStreamingAggregationCandidates")
                                                                   .setFetchSize(Integer.MIN_VALUE)
                                                                   .bind("aggregationLevel", aggregationLevel)
                                                                   .bind("tenantRecordId", createCallContext().getTenantRecordId());
                    query.setStatementLocator(new StringTemplate3StatementLocator(TimelineAggregatorSqlDao.class));
                    ResultIterator<TimelineChunk> iterator = null;
                    try {
                        iterator = query
                                .map(timelineChunkMapper)
                                .iterator();
                        while (iterator.hasNext()) {
                            aggregationConsumer.processTimelineChunk(iterator.next());
                        }
                    } catch (Exception e) {
                        log.error(String.format("Exception during aggregation of level %d", aggregationLevel), e);
                    } finally {
                        if (iterator != null) {
                            iterator.close();
                        }
                    }
                    return null;
                }

            });
            if (sourceTimelineCandidates.size() >= chunksToAggregate) {
                aggregatesCreated.addAndGet(aggregateTimelineCandidates(sourceTimelineCandidates, aggregationLevel, chunksToAggregate));
            }
            if (chunkIdsToInvalidateOrDelete.size() > 0) {
                performWrites();
            }
        } finally {
            msSpentAggregating.addAndGet(System.currentTimeMillis() - startTime);
        }
    }

    private AtomicLong makeCounter(final String counterName) {
        final AtomicLong counter = new AtomicLong();
        aggregatorCounters.put(counterName, counter);
        return counter;
    }

    private Map<String, Long> captureAggregatorCounters() {
        final Map<String, Long> counterValues = new LinkedHashMap<String, Long>();
        for (final Map.Entry<String, AtomicLong> entry : aggregatorCounters.entrySet()) {
            counterValues.put(entry.getKey(), entry.getValue().get());
        }
        return counterValues;
    }

    private Map<String, Long> subtractFromAggregatorCounters(final Map<String, Long> initialCounters) {
        final Map<String, Long> counterValues = new LinkedHashMap<String, Long>();
        for (final Map.Entry<String, AtomicLong> entry : aggregatorCounters.entrySet()) {
            final String key = entry.getKey();
            counterValues.put(key, entry.getValue().get() - initialCounters.get(key));
        }
        return counterValues;
    }

    public void runAggregationThread() {
        aggregatorThread.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                getAndProcessTimelineAggregationCandidates();
            }
        },
                                                config.getAggregationInterval().getMillis(),
                                                config.getAggregationInterval().getMillis(),
                                                TimeUnit.MILLISECONDS);
    }

    public void stopAggregationThread() {
        aggregatorThread.shutdown();
    }

    public long getAggregationRuns() {
        return aggregationRuns.get();
    }

    public long getFoundNothingRuns() {
        return foundNothingRuns.get();
    }

    public long getTimelineChunksConsidered() {
        return timelineChunksConsidered.get();
    }

    public long getTimelineChunkBatchesProcessed() {
        return timelineChunkBatchesProcessed.get();
    }

    public long getTimelineChunksCombined() {
        return timelineChunksCombined.get();
    }

    public long getTimelineChunksQueuedForCreation() {
        return timelineChunksQueuedForCreation.get();
    }

    public long getTimelineChunksWritten() {
        return timelineChunksWritten.get();
    }

    public long getTimelineChunksInvalidatedOrDeleted() {
        return timelineChunksInvalidatedOrDeleted.get();
    }

    public long getTimelineChunksBytesCreated() {
        return timelineChunksBytesCreated.get();
    }

    public long getMsSpentAggregating() {
        return msSpentAggregating.get();
    }

    public long getMsSpentSleeping() {
        return msSpentSleeping.get();
    }

    public long getMsWritingDb() {
        return msWritingDb.get();
    }

    public void initiateAggregation() {
        log.info("Starting user-initiated aggregation");
        Executors.newSingleThreadExecutor().execute(new Runnable() {

            @Override
            public void run() {
                getAndProcessTimelineAggregationCandidates();
            }
        });
    }

    private InternalCallContext createCallContext() {
        // TODO add teantRecordId and accountRecordId
        return internalCallContextFactory.createInternalCallContext(InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID, null, "TimelineAggregator", CallOrigin.INTERNAL, UserType.SYSTEM, null);
    }
}
