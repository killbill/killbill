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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuite;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.meter.timeline.samples.ScalarSample;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.MeterConfig;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableMap;

public class TestTimelineEventHandler extends MeterTestSuite {

    private static final File basePath = new File(System.getProperty("java.io.tmpdir"), "TestTimelineEventHandler-" + System.currentTimeMillis());
    private static final String EVENT_TYPE = "eventType";
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    private final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
    private final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(new ClockMock(), nonEntityDao, new CacheControllerDispatcher());

    private final TimelineDao dao = new MockTimelineDao();

    @Test(groups = "fast")
    public void testDownsizingValues() throws Exception {
        Assert.assertTrue(basePath.mkdir());
        System.setProperty("killbill.usage.timelines.spoolDir", basePath.getAbsolutePath());
        final MeterConfig config = new ConfigurationObjectFactory(System.getProperties()).build(MeterConfig.class);
        final int eventTypeId = dao.getOrAddEventCategory(EVENT_TYPE, internalCallContext);
        final int int2shortId = dao.getOrAddMetric(eventTypeId, "int2short", internalCallContext);
        final int long2intId = dao.getOrAddMetric(eventTypeId, "long2int", internalCallContext);
        final int long2shortId = dao.getOrAddMetric(eventTypeId, "long2short", internalCallContext);
        final int int2intId = dao.getOrAddMetric(eventTypeId, "int2int", internalCallContext);
        final int long2longId = dao.getOrAddMetric(eventTypeId, "long2long", internalCallContext);
        final int hostId = 1;
        final TimelineEventHandler handler = new TimelineEventHandler(config, dao, timelineCoder, sampleCoder,
                                                                      new BackgroundDBChunkWriter(dao, config, internalCallContextFactory), new MockFileBackedBuffer());

        // Test downsizing of values
        final Map<String, Object> event = ImmutableMap.<String, Object>of(
                "int2short", new Integer(1),
                "long2int", new Long(Integer.MAX_VALUE),
                "long2short", new Long(2),
                "int2int", Integer.MAX_VALUE,
                "long2long", Long.MAX_VALUE);
        final Map<Integer, ScalarSample> output = convertEventToSamples(handler, event, EVENT_TYPE);

        Assert.assertEquals(output.get(int2shortId).getSampleValue(), (short) 1);
        Assert.assertEquals(output.get(int2shortId).getSampleValue().getClass(), Short.class);
        Assert.assertEquals(output.get(long2intId).getSampleValue(), Integer.MAX_VALUE);
        Assert.assertEquals(output.get(long2intId).getSampleValue().getClass(), Integer.class);
        Assert.assertEquals(output.get(long2shortId).getSampleValue(), (short) 2);
        Assert.assertEquals(output.get(long2shortId).getSampleValue().getClass(), Short.class);
        Assert.assertEquals(output.get(int2intId).getSampleValue(), Integer.MAX_VALUE);
        Assert.assertEquals(output.get(int2intId).getSampleValue().getClass(), Integer.class);
        Assert.assertEquals(output.get(long2longId).getSampleValue(), Long.MAX_VALUE);
        Assert.assertEquals(output.get(long2longId).getSampleValue().getClass(), Long.class);
    }

    private Map<Integer, ScalarSample> convertEventToSamples(final TimelineEventHandler handler, final Map<String, Object> event, final String eventType) {
        final Map<Integer, ScalarSample> output = new HashMap<Integer, ScalarSample>();
        handler.convertSamplesToScalarSamples(eventType, event, output, internalCallContext);
        return output;
    }

    private void processOneEvent(final TimelineEventHandler handler, final int hostId, final String eventType, final String sampleKind, final DateTime timestamp) throws Exception {
        final Map<String, Object> rawEvent = ImmutableMap.<String, Object>of(sampleKind, new Integer(1));
        final Map<Integer, ScalarSample> convertedEvent = convertEventToSamples(handler, rawEvent, eventType);
        handler.processSamples(new SourceSamplesForTimestamp(hostId, eventType, timestamp, convertedEvent), internalCallContext);
    }

    private void sleep(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test(groups = "fast")
    public void testPurgeAccumulators() throws Exception {
        System.setProperty("arecibo.collector.timelines.spoolDir", basePath.getAbsolutePath());
        final MeterConfig config = new ConfigurationObjectFactory(System.getProperties()).build(MeterConfig.class);
        final TimelineEventHandler handler = new TimelineEventHandler(config, dao, timelineCoder, sampleCoder, new BackgroundDBChunkWriter(dao, config, internalCallContextFactory), new MockFileBackedBuffer());
        Assert.assertEquals(handler.getAccumulators().size(), 0);
        processOneEvent(handler, 1, "eventType1", "sampleKind1", new DateTime());
        sleep(20);
        final DateTime purgeBeforeTime = new DateTime();
        sleep(20);
        processOneEvent(handler, 1, "eventType2", "sampleKind2", new DateTime());
        Assert.assertEquals(handler.getAccumulators().size(), 2);
        handler.purgeOldSourcesAndAccumulators(purgeBeforeTime);
        final Collection<TimelineSourceEventAccumulator> accumulators = handler.getAccumulators();
        Assert.assertEquals(accumulators.size(), 1);
    }
}
