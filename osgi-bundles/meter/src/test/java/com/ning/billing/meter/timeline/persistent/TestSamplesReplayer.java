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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.skife.jdbi.v2.DBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuite;
import com.ning.billing.meter.timeline.MockTimelineDao;
import com.ning.billing.meter.timeline.TimelineSourceEventAccumulator;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.samples.SampleOpcode;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.cache.CacheController;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableMap;

// Lightweight version of TestFileBackedBuffer
public class TestSamplesReplayer extends MeterTestSuite {

    // Total space: 255 * 3 = 765 bytes
    private static final int NB_EVENTS = 3;
    // One will still be in memory after the flush
    private static final int EVENTS_ON_DISK = NB_EVENTS - 1;
    private static final int HOST_ID = 1;
    private static final int EVENT_CATEGORY_ID = 123;
    private static final File basePath = new File(System.getProperty("java.io.tmpdir"), "TestSamplesReplayer-" + System.currentTimeMillis());
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    private final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
    private final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(new ClockMock(), nonEntityDao, new CacheControllerDispatcher());

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        Assert.assertTrue(basePath.mkdir());
    }

    @Test(groups = "fast")
    public void testIdentityFilter() throws Exception {
        // Need less than 765 + 1 (metadata) bytes
        final FileBackedBuffer fileBackedBuffer = new FileBackedBuffer(basePath.toString(), "test", 765, 1);

        // Create the host samples - this will take 255 bytes
        final Map<Integer, ScalarSample> eventMap = new HashMap<Integer, ScalarSample>();
        eventMap.putAll(ImmutableMap.<Integer, ScalarSample>of(
                1, new ScalarSample(SampleOpcode.BYTE, (byte) 0),
                2, new ScalarSample(SampleOpcode.SHORT, (short) 1),
                3, new ScalarSample(SampleOpcode.INT, 1000),
                4, new ScalarSample(SampleOpcode.LONG, 12345678901L),
                5, new ScalarSample(SampleOpcode.DOUBLE, Double.MAX_VALUE)
                                                              ));
        eventMap.putAll(ImmutableMap.<Integer, ScalarSample>of(
                6, new ScalarSample(SampleOpcode.FLOAT, Float.NEGATIVE_INFINITY),
                7, new ScalarSample(SampleOpcode.STRING, "pwet")
                                                              ));
        final DateTime firstTime = new DateTime(DateTimeZone.UTC).minusSeconds(NB_EVENTS * 30);

        // Write the samples to disk
        for (int i = 0; i < NB_EVENTS; i++) {
            final SourceSamplesForTimestamp samples = new SourceSamplesForTimestamp(HOST_ID, "something", firstTime.plusSeconds(30 * i), eventMap);
            fileBackedBuffer.append(samples);
        }

        // Try the replayer
        final Replayer replayer = new Replayer(new File(basePath.toString()).getAbsolutePath());
        final List<SourceSamplesForTimestamp> hostSamples = replayer.readAll();
        Assert.assertEquals(hostSamples.size(), EVENTS_ON_DISK);

        // Try to encode them again
        final MockTimelineDao dao = new MockTimelineDao();
        final TimelineSourceEventAccumulator accumulator = new TimelineSourceEventAccumulator(dao, timelineCoder, sampleCoder, HOST_ID,
                                                                                              EVENT_CATEGORY_ID, hostSamples.get(0).getTimestamp(), internalCallContextFactory);
        for (final SourceSamplesForTimestamp samplesFound : hostSamples) {
            accumulator.addSourceSamples(samplesFound);
        }
        Assert.assertTrue(accumulator.checkSampleCounts(EVENTS_ON_DISK));

        // This will check the SampleCode can encode value correctly
        accumulator.extractAndQueueTimelineChunks();
        Assert.assertEquals(dao.getTimelineChunks().keySet().size(), 7);
        for (final TimelineChunk chunk : dao.getTimelineChunks().values()) {
            Assert.assertEquals(chunk.getSourceId(), HOST_ID);
            Assert.assertEquals(chunk.getSampleCount(), EVENTS_ON_DISK);
        }
    }
}
