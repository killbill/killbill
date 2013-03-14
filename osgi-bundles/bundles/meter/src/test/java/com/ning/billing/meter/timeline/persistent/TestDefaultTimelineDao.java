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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteWithEmbeddedDB;
import com.ning.billing.meter.timeline.categories.CategoryRecordIdAndMetric;
import com.ning.billing.meter.timeline.chunks.TimelineChunk;
import com.ning.billing.meter.timeline.consumer.TimelineChunkConsumer;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;

public class TestDefaultTimelineDao extends MeterTestSuiteWithEmbeddedDB {

    private static final TimelineChunkConsumer FAIL_CONSUMER = new TimelineChunkConsumer() {
        @Override
        public void processTimelineChunk(final TimelineChunk chunk) {
            Assert.fail("Shouldn't find any sample");
        }
    };

    @Test(groups = "slow")
    public void testGetSampleKindsByHostName() throws Exception {
        final TimelineDao dao = new DefaultTimelineDao(getDBI());
        final DateTime startTime = new DateTime(DateTimeZone.UTC);
        final DateTime endTime = startTime.plusSeconds(2);

        // Create the host
        final String hostName = UUID.randomUUID().toString();
        final Integer hostId = dao.getOrAddSource(hostName, internalCallContext);
        Assert.assertNotNull(hostId);

        // Create a timeline times (needed for the join in the dashboard query)
        final Integer eventCategoryId = 123;

        // Create the samples
        final String sampleOne = UUID.randomUUID().toString();
        final Integer sampleOneId = dao.getOrAddMetric(eventCategoryId, sampleOne, internalCallContext);
        Assert.assertNotNull(sampleOneId);
        final String sampleTwo = UUID.randomUUID().toString();
        final Integer sampleTwoId = dao.getOrAddMetric(eventCategoryId, sampleTwo, internalCallContext);
        Assert.assertNotNull(sampleTwoId);

        // Basic retrieval tests
        final BiMap<Integer, CategoryRecordIdAndMetric> sampleKinds = dao.getMetrics(internalCallContext);
        Assert.assertEquals(sampleKinds.size(), 2);
        Assert.assertEquals(sampleKinds.get(sampleOneId).getEventCategoryId(), (int) eventCategoryId);
        Assert.assertEquals(sampleKinds.get(sampleOneId).getMetric(), sampleOne);
        Assert.assertEquals(sampleKinds.get(sampleTwoId).getEventCategoryId(), (int) eventCategoryId);
        Assert.assertEquals(sampleKinds.get(sampleTwoId).getMetric(), sampleTwo);
        Assert.assertEquals(dao.getCategoryIdAndMetric(sampleOneId, internalCallContext).getEventCategoryId(), (int) eventCategoryId);
        Assert.assertEquals(dao.getCategoryIdAndMetric(sampleOneId, internalCallContext).getMetric(), sampleOne);
        Assert.assertEquals(dao.getCategoryIdAndMetric(sampleTwoId, internalCallContext).getEventCategoryId(), (int) eventCategoryId);
        Assert.assertEquals(dao.getCategoryIdAndMetric(sampleTwoId, internalCallContext).getMetric(), sampleTwo);

        dao.insertTimelineChunk(new TimelineChunk(0, hostId, sampleOneId, startTime, endTime, new byte[0], new byte[0], 0), internalCallContext);
        dao.insertTimelineChunk(new TimelineChunk(0, hostId, sampleTwoId, startTime, endTime, new byte[0], new byte[0], 0), internalCallContext);

        // Random sampleKind for random host
        dao.insertTimelineChunk(new TimelineChunk(0, Integer.MAX_VALUE - 100, Integer.MAX_VALUE, startTime, endTime, new byte[0], new byte[0], 0), internalCallContext);

        // Test dashboard query
        final AtomicInteger chunksSeen = new AtomicInteger(0);
        dao.getSamplesBySourceIdsAndMetricIds(ImmutableList.<Integer>of(hostId), ImmutableList.<Integer>of(sampleOneId, sampleTwoId), startTime, startTime.plusSeconds(2), new TimelineChunkConsumer() {
            @Override
            public void processTimelineChunk(final TimelineChunk chunk) {
                chunksSeen.incrementAndGet();
                Assert.assertEquals((Integer) chunk.getSourceId(), hostId);
                Assert.assertTrue(chunk.getMetricId() == sampleOneId || chunk.getMetricId() == sampleTwoId);
            }
        }, internalCallContext);
        Assert.assertEquals(chunksSeen.get(), 2);

        // Dummy queries
        dao.getSamplesBySourceIdsAndMetricIds(ImmutableList.<Integer>of(Integer.MAX_VALUE), null, startTime, startTime.plusDays(1), FAIL_CONSUMER, internalCallContext);
        dao.getSamplesBySourceIdsAndMetricIds(ImmutableList.<Integer>of(hostId), ImmutableList.<Integer>of(Integer.MAX_VALUE), startTime, startTime.plusDays(1), FAIL_CONSUMER, internalCallContext);
        dao.getSamplesBySourceIdsAndMetricIds(ImmutableList.<Integer>of(hostId), ImmutableList.<Integer>of(sampleOneId, sampleTwoId), startTime.plusDays(1), startTime.plusDays(2), FAIL_CONSUMER, internalCallContext);
    }
}
