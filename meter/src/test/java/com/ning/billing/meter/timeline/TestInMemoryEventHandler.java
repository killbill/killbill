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

import java.io.File;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuite;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.persistent.FileBackedBuffer;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.MeterConfig;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableMap;

public class TestInMemoryEventHandler extends MeterTestSuite {

    private static final UUID HOST_UUID = UUID.randomUUID();
    private static final String EVENT_TYPE = "eventType";
    private static final String SAMPLE_KIND_A = "kindA";
    private static final String SAMPLE_KIND_B = "kindB";
    private static final Map<String, Object> EVENT = ImmutableMap.<String, Object>of(SAMPLE_KIND_A, 12, SAMPLE_KIND_B, 42);
    private static final int NB_EVENTS = 5;
    private static final File basePath = new File(System.getProperty("java.io.tmpdir"), "TestInMemoryCollectorEventProcessor-" + System.currentTimeMillis());
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    private final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
    private final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(new ClockMock(), nonEntityDao, new CacheControllerDispatcher());


    private final TimelineDao dao = new MockTimelineDao();
    private TimelineEventHandler timelineEventHandler;
    private int eventTypeId = 0;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        Assert.assertTrue(basePath.mkdir());
        System.setProperty("killbill.usage.timelines.spoolDir", basePath.getAbsolutePath());
        final MeterConfig config = new ConfigurationObjectFactory(System.getProperties()).build(MeterConfig.class);
        timelineEventHandler = new TimelineEventHandler(config, dao, timelineCoder, sampleCoder, new BackgroundDBChunkWriter(dao, config, internalCallContextFactory),
                                                        new FileBackedBuffer(config.getSpoolDir(), "TimelineEventHandler", 1024 * 1024, 10));

        dao.getOrAddSource(HOST_UUID.toString(), internalCallContext);
        eventTypeId = dao.getOrAddEventCategory(EVENT_TYPE, internalCallContext);
    }

    @Test(groups = "fast")
    public void testInMemoryFilters() throws Exception {
        final DateTime startTime = new DateTime(DateTimeZone.UTC);
        for (int i = 0; i < NB_EVENTS; i++) {
            timelineEventHandler.record(HOST_UUID.toString(), EVENT_TYPE, startTime, EVENT, internalCallContext);
        }
        final DateTime endTime = new DateTime(DateTimeZone.UTC);

        final Integer hostId = dao.getSourceId(HOST_UUID.toString(), internalCallContext);
        Assert.assertNotNull(hostId);
        final Integer sampleKindAId = dao.getMetricId(eventTypeId, SAMPLE_KIND_A, internalCallContext);
        Assert.assertNotNull(sampleKindAId);
        final Integer sampleKindBId = dao.getMetricId(eventTypeId, SAMPLE_KIND_B, internalCallContext);
        Assert.assertNotNull(sampleKindBId);

        // One per host and type
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, null, null, internalCallContext).size(), 2);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, startTime, null, internalCallContext).size(), 2);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, null, endTime, internalCallContext).size(), 2);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, startTime, endTime, internalCallContext).size(), 2);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindAId, startTime, endTime, internalCallContext).size(), 1);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindBId, startTime, endTime, internalCallContext).size(), 1);
        // Wider ranges should be supported
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindBId, startTime.minusSeconds(1), endTime, internalCallContext).size(), 1);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindBId, startTime, endTime.plusSeconds(1), internalCallContext).size(), 1);
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, sampleKindBId, startTime.minusSeconds(1), endTime.plusSeconds(1), internalCallContext).size(), 1);
        // Buggy kind
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, Integer.MAX_VALUE, startTime, endTime, internalCallContext).size(), 0);
        // Buggy start date
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, startTime.plusMinutes(1), endTime, internalCallContext).size(), 0);
        // Buggy end date
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(hostId, startTime, endTime.minusMinutes(1), internalCallContext).size(), 0);
        // Buggy host
        Assert.assertEquals(timelineEventHandler.getInMemoryTimelineChunks(Integer.MAX_VALUE, startTime, endTime, internalCallContext).size(), 0);
    }
}
