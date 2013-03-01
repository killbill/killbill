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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.codec.TimelineChunkAccumulator;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.meter.timeline.samples.NullSample;
import com.ning.billing.meter.timeline.samples.RepeatSample;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.callcontext.InternalCallContextFactory;

/**
 * This class represents a collection of timeline chunks, one for each
 * metric belonging to one event category, each over a specific time period,
 * from a single source.  This class is used to accumulate samples
 * to be written to the database; a separate streaming class with
 * much less overhead is used to "play back" the samples read from
 * the db in response to queries.
 * <p/>
 * All subordinate timelines contain the same number of samples.
 * <p/>
 * When enough samples have accumulated, typically one hour's worth,
 * in-memory samples are made into TimelineChunks, one chunk for each metricId
 * maintained by the accumulator.
 * <p/>
 * These new chunks are organized as PendingChunkMaps, kept in a local list and also
 * handed off to a PendingChunkMapConsumer to written to the db by a background process.  At some
 * in the future, that background process will call markPendingChunkMapConsumed(),
 * passing the id of a PendingChunkMap.  This causes the PendingChunkMap
 * to be removed from the local list maintained by the TimelineSourceEventAccumulator.
 * <p/>
 * Queries that cause the TimelineSourceEventAccumulator instance to return memory
 * chunks also return any chunks in PendingChunkMaps in the local list of pending chunks.
 */
public class TimelineSourceEventAccumulator {

    private static final Logger log = LoggerFactory.getLogger(TimelineSourceEventAccumulator.class);
    private static final DateTimeFormatter dateFormatter = ISODateTimeFormat.dateTime();
    private static final NullSample nullSample = new NullSample();
    private static final boolean checkEveryAccess = Boolean.parseBoolean(System.getProperty("killbill.usage.checkEveryAccess"));
    private static final Random rand = new Random(0);

    private final Map<Integer, SampleSequenceNumber> metricIdCounters = new HashMap<Integer, SampleSequenceNumber>();
    private final List<PendingChunkMap> pendingChunkMaps = new ArrayList<PendingChunkMap>();
    private long pendingChunkMapIdCounter = 1;

    private final BackgroundDBChunkWriter backgroundWriter;
    private final TimelineCoder timelineCoder;
    private final SampleCoder sampleCoder;
    private final Integer timelineLengthMillis;
    private final int sourceId;
    private final int eventCategoryId;
    // This is the time when we want to end the chunk.  Setting the value randomly
    // when the TimelineSourceEventAccumulator  is created provides a mechanism to
    // distribute the db writes
    private DateTime chunkEndTime = null;
    private DateTime startTime = null;
    private DateTime endTime = null;
    private DateTime latestSampleAddTime;
    private long sampleSequenceNumber = 0;
    private int sampleCount = 0;

    /**
     * Maps the sample kind id to the accumulator for that sample kind
     */
    private final Map<Integer, TimelineChunkAccumulator> timelines = new ConcurrentHashMap<Integer, TimelineChunkAccumulator>();

    /**
     * Holds the sampling times of the samples
     */
    private final List<DateTime> times = new ArrayList<DateTime>();

    public TimelineSourceEventAccumulator(final TimelineDao dao, final TimelineCoder timelineCoder, final SampleCoder sampleCoder,
                                          final BackgroundDBChunkWriter backgroundWriter, final int sourceId, final int eventCategoryId,
                                          final DateTime firstSampleTime, final Integer timelineLengthMillis) {
        this.timelineLengthMillis = timelineLengthMillis;
        this.backgroundWriter = backgroundWriter;
        this.timelineCoder = timelineCoder;
        this.sampleCoder = sampleCoder;
        this.sourceId = sourceId;
        this.eventCategoryId = eventCategoryId;
        // Set the end-of-chunk time by tossing a random number, to evenly distribute the db writeback load.
        this.chunkEndTime = timelineLengthMillis != null ? firstSampleTime.plusMillis(rand.nextInt(timelineLengthMillis)) : null;
    }

    /*
     * This constructor is used for testing; it writes chunks as soon as they are
     * created, but because the chunkEndTime is way in the future, doesn't initiate
     * chunk writes.
     */
    public TimelineSourceEventAccumulator(final TimelineDao timelineDAO, final TimelineCoder timelineCoder, final SampleCoder sampleCoder,
                                          final Integer sourceId, final int eventTypeId, final DateTime firstSampleTime, final InternalCallContextFactory internalCallContextFactory) {
        this(timelineDAO, timelineCoder, sampleCoder, new BackgroundDBChunkWriter(timelineDAO, null, true, internalCallContextFactory), sourceId, eventTypeId, firstSampleTime, Integer.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    // TODO - we can probably do better than synchronize the whole method
    public synchronized void addSourceSamples(final SourceSamplesForTimestamp samples) {
        final DateTime timestamp = samples.getTimestamp();

        if (chunkEndTime != null && chunkEndTime.isBefore(timestamp)) {
            extractAndQueueTimelineChunks();
            startTime = timestamp;
            chunkEndTime = timestamp.plusMillis(timelineLengthMillis);
        }

        if (startTime == null) {
            startTime = timestamp;
        }
        if (endTime == null) {
            endTime = timestamp;
        } else if (timestamp.isBefore(endTime)) {
            // Note: we allow multiple events at the same time
            // TODO Do we really want that?
            log.warn("Adding samples for source {}, timestamp {} is before the end time {}; ignored",
                     new Object[]{sourceId, dateFormatter.print(timestamp), dateFormatter.print(endTime)});
            return;
        }
        sampleSequenceNumber++;
        latestSampleAddTime = new DateTime();
        for (final Map.Entry<Integer, ScalarSample> entry : samples.getSamples().entrySet()) {
            final Integer metricId = entry.getKey();
            final SampleSequenceNumber counter = metricIdCounters.get(metricId);
            if (counter != null) {
                counter.setSequenceNumber(sampleSequenceNumber);
            } else {
                metricIdCounters.put(metricId, new SampleSequenceNumber(sampleSequenceNumber));
            }
            final ScalarSample sample = entry.getValue();
            TimelineChunkAccumulator timeline = timelines.get(metricId);
            if (timeline == null) {
                timeline = new TimelineChunkAccumulator(sourceId, metricId, sampleCoder);
                if (sampleCount > 0) {
                    addPlaceholders(timeline, sampleCount);
                }
                timelines.put(metricId, timeline);
            }
            final ScalarSample compressedSample = sampleCoder.compressSample(sample);
            timeline.addSample(compressedSample);
        }
        for (final Map.Entry<Integer, SampleSequenceNumber> entry : metricIdCounters.entrySet()) {
            final SampleSequenceNumber counter = entry.getValue();
            if (counter.getSequenceNumber() < sampleSequenceNumber) {
                counter.setSequenceNumber(sampleSequenceNumber);
                final int metricId = entry.getKey();
                final TimelineChunkAccumulator timeline = timelines.get(metricId);
                timeline.addSample(nullSample);
            }
        }
        // Now we can update the state
        endTime = timestamp;
        sampleCount++;
        times.add(timestamp);

        if (checkEveryAccess) {
            checkSampleCounts(sampleCount);
        }
    }

    private void addPlaceholders(final TimelineChunkAccumulator timeline, int countToAdd) {
        final int maxRepeatSamples = RepeatSample.MAX_SHORT_REPEAT_COUNT;
        while (countToAdd >= maxRepeatSamples) {
            timeline.addPlaceholder((byte) maxRepeatSamples);
            countToAdd -= maxRepeatSamples;
        }
        if (countToAdd > 0) {
            timeline.addPlaceholder((byte) countToAdd);
        }
    }

    /**
     * This method queues a map of TimelineChunks extracted from the TimelineChunkAccumulators
     * to be written to the db.  When memory chunks are requested, any queued chunk will be included
     * in the list.
     */
    public synchronized void extractAndQueueTimelineChunks() {
        if (times.size() > 0) {
            final Map<Integer, TimelineChunk> chunkMap = new HashMap<Integer, TimelineChunk>();
            final byte[] timeBytes = timelineCoder.compressDateTimes(times);
            for (final Map.Entry<Integer, TimelineChunkAccumulator> entry : timelines.entrySet()) {
                final int metricId = entry.getKey();
                final TimelineChunkAccumulator accumulator = entry.getValue();
                final TimelineChunk chunk = accumulator.extractTimelineChunkAndReset(startTime, endTime, timeBytes);
                chunkMap.put(metricId, chunk);
            }
            times.clear();
            sampleCount = 0;
            final long counter = pendingChunkMapIdCounter++;
            final PendingChunkMap newChunkMap = new PendingChunkMap(this, counter, chunkMap);
            pendingChunkMaps.add(newChunkMap);
            backgroundWriter.addPendingChunkMap(newChunkMap);
        }
    }

    public synchronized void markPendingChunkMapConsumed(final long pendingChunkMapId) {
        final PendingChunkMap pendingChunkMap = pendingChunkMaps.size() > 0 ? pendingChunkMaps.get(0) : null;
        if (pendingChunkMap == null) {
            log.error("In TimelineSourceEventAccumulator.markPendingChunkMapConsumed(), could not find the map for {}", pendingChunkMapId);
        } else if (pendingChunkMapId != pendingChunkMap.getPendingChunkMapId()) {
            log.error("In TimelineSourceEventAccumulator.markPendingChunkMapConsumed(), the next map has id {}, but we're consuming id {}",
                      pendingChunkMap.getPendingChunkMapId(), pendingChunkMapId);
        } else {
            pendingChunkMaps.remove(0);
        }
    }

    public synchronized Collection<TimelineChunk> getInMemoryTimelineChunks(final List<Integer> metricIds) throws IOException {
        final List<TimelineChunk> timelineChunks = new ArrayList<TimelineChunk>();

        // Get all the older chunks from the staging area of the BackgroundDBChunkWriter
        for (final PendingChunkMap pendingChunkMap : pendingChunkMaps) {
            for (final Integer metricId : metricIds) {
                final TimelineChunk timelineChunkForMetricId = pendingChunkMap.getChunkMap().get(metricId);
                if (timelineChunkForMetricId != null) {
                    timelineChunks.add(timelineChunkForMetricId);
                }
            }
        }

        // Get the data in this accumulator, not yet in the staging area
        // This is very similar to extractAndQueueTimelineChunks() above, but without changing the global state
        final byte[] timeBytes = timelineCoder.compressDateTimes(times);
        for (final Integer metricId : metricIds) {
            final TimelineChunkAccumulator chunkAccumulator = timelines.get(metricId);
            if (chunkAccumulator != null) {
                // Extract the timeline for this chunk by copying it and reading encoded bytes
                final TimelineChunkAccumulator chunkAccumulatorCopy = chunkAccumulator.deepCopy();
                final TimelineChunk timelineChunk = chunkAccumulatorCopy.extractTimelineChunkAndReset(startTime, endTime, timeBytes);
                timelineChunks.add(timelineChunk);
            }
        }

        return timelineChunks;
    }

    /**
     * Make sure all timelines have the sample count passed in; otherwise log
     * discrepancies and return false
     *
     * @param assertedCount The sample count that all timelines are supposed to have
     * @return true if all timelines have the right count; false otherwise
     */
    public boolean checkSampleCounts(final int assertedCount) {
        boolean success = true;
        if (assertedCount != sampleCount) {
            log.error("For host {}, start time {}, the SourceTimeLines sampleCount {} is not equal to the assertedCount {}",
                      new Object[]{sourceId, dateFormatter.print(startTime), sampleCount, assertedCount});
            success = false;
        }
        for (final Map.Entry<Integer, TimelineChunkAccumulator> entry : timelines.entrySet()) {
            final int metricId = entry.getKey();
            final TimelineChunkAccumulator timeline = entry.getValue();
            final int lineSampleCount = timeline.getSampleCount();
            if (lineSampleCount != assertedCount) {
                log.error("For host {}, start time {}, sample kind id {}, the sampleCount {} is not equal to the assertedCount {}",
                          new Object[]{sourceId, dateFormatter.print(startTime), metricId, lineSampleCount, assertedCount});
                success = false;
            }
        }
        return success;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getEventCategoryId() {
        return eventCategoryId;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public Map<Integer, TimelineChunkAccumulator> getTimelines() {
        return timelines;
    }

    public List<DateTime> getTimes() {
        return times;
    }

    public DateTime getLatestSampleAddTime() {
        return latestSampleAddTime;
    }

    private static class SampleSequenceNumber {

        private long sequenceNumber;

        public SampleSequenceNumber(final long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }

        public void setSequenceNumber(final long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
        }
    }
}
