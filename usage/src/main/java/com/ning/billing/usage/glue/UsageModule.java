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

package com.ning.billing.usage.glue;

import java.io.IOException;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.config.SimplePropertyConfigSource;

import com.ning.billing.util.config.UsageConfig;
import com.ning.billing.usage.timeline.codec.DefaultSampleCoder;
import com.ning.billing.usage.timeline.codec.SampleCoder;
import com.ning.billing.usage.timeline.persistent.FileBackedBuffer;
import com.ning.billing.usage.timeline.persistent.TimelineDao;
import com.ning.billing.usage.timeline.times.DefaultTimelineCoder;
import com.ning.billing.usage.timeline.times.TimelineCoder;

import com.google.inject.AbstractModule;

public class UsageModule extends AbstractModule {

    private final ConfigSource configSource;

    public UsageModule() {
        this(new SimplePropertyConfigSource(System.getProperties()));
    }

    public UsageModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    protected UsageConfig installConfig() {
        final UsageConfig config = new ConfigurationObjectFactory(configSource).build(UsageConfig.class);
        bind(UsageConfig.class).toInstance(config);

        return config;
    }

    protected void configureFileBackedBuffer(final UsageConfig config) {
        // Persistent buffer for in-memory samples
        try {
            final boolean deleteFilesOnClose = config.getShutdownSaveMode().equals("save_all_timelines");
            final FileBackedBuffer fileBackedBuffer = new FileBackedBuffer(config.getSpoolDir(), "TimelineEventHandler", deleteFilesOnClose, config.getSegmentsSize(), config.getMaxNbSegments());
            bind(FileBackedBuffer.class).toInstance(fileBackedBuffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void configureDao() {
        bind(TimelineDao.class).toProvider(CachingDefaultTimelineDaoProvider.class).asEagerSingleton();
    }

    protected void configureTimelineObjects() {
        bind(TimelineCoder.class).to(DefaultTimelineCoder.class).asEagerSingleton();
        bind(SampleCoder.class).to(DefaultSampleCoder.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        final UsageConfig config = installConfig();

        configureFileBackedBuffer(config);
        configureDao();
        configureTimelineObjects();

        // TODO
        //configureTimelineAggregator();
        //configureBackgroundDBChunkWriter();
        //configureReplayer();
    }
}

