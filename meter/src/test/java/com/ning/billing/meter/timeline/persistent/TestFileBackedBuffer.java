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
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuite;
import com.ning.billing.meter.timeline.BackgroundDBChunkWriter;
import com.ning.billing.meter.timeline.MockTimelineDao;
import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.codec.DefaultSampleCoder;
import com.ning.billing.meter.timeline.codec.SampleCoder;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;
import com.ning.billing.meter.timeline.times.DefaultTimelineCoder;
import com.ning.billing.meter.timeline.times.TimelineCoder;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.MeterConfig;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableMap;

public class TestFileBackedBuffer extends MeterTestSuite {

    private static final Logger log = LoggerFactory.getLogger(TestFileBackedBuffer.class);

    private static final UUID HOST_UUID = UUID.randomUUID();
    private static final String KIND_A = "kindA";
    private static final String KIND_B = "kindB";
    private static final Map<String, Object> EVENT = ImmutableMap.<String, Object>of(KIND_A, 12, KIND_B, 42);
    // ~105 bytes per event, 10 1MB buffers -> need at least 100,000 events to spill over
    private static final int NB_EVENTS = 100000;
    private static final File basePath = new File(System.getProperty("java.io.tmpdir"), "TestFileBackedBuffer-" + System.currentTimeMillis());
    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();
    private static final SampleCoder sampleCoder = new DefaultSampleCoder();

    private final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
    private final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(new ClockMock(), nonEntityDao, new CacheControllerDispatcher());
    private final TimelineDao dao = new MockTimelineDao();
    private TimelineEventHandler timelineEventHandler;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        Assert.assertTrue(basePath.mkdir());
        System.setProperty("killbill.usage.timelines.spoolDir", basePath.getAbsolutePath());
        System.setProperty("killbill.usage.timelines.length", "60s");
        final MeterConfig config = new ConfigurationObjectFactory(System.getProperties()).build(MeterConfig.class);
        timelineEventHandler = new TimelineEventHandler(config, dao, timelineCoder, sampleCoder, new BackgroundDBChunkWriter(dao, config, internalCallContextFactory),
                                                        new FileBackedBuffer(config.getSpoolDir(), "TimelineEventHandler", 1024 * 1024, 10));

        dao.getOrAddSource(HOST_UUID.toString(), internalCallContext);
    }

    @Test(groups = "fast") // Not really fast, but doesn't require a database
    public void testAppend() throws Exception {
        log.info("Writing files to " + basePath);
        final List<File> binFiles = new ArrayList<File>();

        final List<DateTime> timestampsRecorded = new ArrayList<DateTime>();
        final List<String> categoriesRecorded = new ArrayList<String>();

        // Sanity check before the tests
        Assert.assertEquals(timelineEventHandler.getBackingBuffer().getFilesCreated(), 0);
        findBinFiles(binFiles, basePath);
        Assert.assertEquals(binFiles.size(), 0);

        // Send enough events to spill over to disk
        final DateTime startTime = new DateTime(DateTimeZone.UTC);
        for (int i = 0; i < NB_EVENTS; i++) {
            final String category = UUID.randomUUID().toString();
            final DateTime eventTimestamp = startTime.plusSeconds(i);
            timelineEventHandler.record(HOST_UUID.toString(), category, eventTimestamp, EVENT, internalCallContext);
            timestampsRecorded.add(eventTimestamp);
            categoriesRecorded.add(category);
        }

        // Check the files have been created (at least one per accumulator)
        final long bytesOnDisk = timelineEventHandler.getBackingBuffer().getBytesOnDisk();
        Assert.assertTrue(timelineEventHandler.getBackingBuffer().getFilesCreated() > 0);
        binFiles.clear();
        findBinFiles(binFiles, basePath);
        Assert.assertTrue(binFiles.size() > 0);

        log.info("Sent {} events and wrote {} bytes on disk ({} bytes/event)", new Object[]{NB_EVENTS, bytesOnDisk, bytesOnDisk / NB_EVENTS});

        // Replay the events. Note that size of timestamp recorded != eventsReplayed as some of the ones sent are still in memory
        final Replayer replayer = new Replayer(basePath.getAbsolutePath());
        final List<SourceSamplesForTimestamp> eventsReplayed = replayer.readAll();
        for (int i = 0; i < eventsReplayed.size(); i++) {
            // Looks like Jackson maps it back using the JVM timezone
            Assert.assertEquals(eventsReplayed.get(i).getTimestamp().toDateTime(DateTimeZone.UTC), timestampsRecorded.get(i));
            Assert.assertEquals(eventsReplayed.get(i).getCategory(), categoriesRecorded.get(i));
        }

        // Make sure files have been deleted
        binFiles.clear();
        findBinFiles(binFiles, basePath);
        Assert.assertEquals(binFiles.size(), 0);
    }

    private static void findBinFiles(final Collection<File> files, final File directory) {
        final File[] found = directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(final File pathname) {
                return pathname.getName().endsWith(".bin");
            }
        });
        if (found != null) {
            for (final File file : found) {
                if (file.isDirectory()) {
                    findBinFiles(files, file);
                } else {
                    files.add(file);
                }
            }
        }
    }
}
