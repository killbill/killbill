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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

public class Replayer {

    private static final Logger log = LoggerFactory.getLogger(Replayer.class);
    private static final SmileFactory smileFactory = new SmileFactory();
    private static final ObjectMapper smileMapper = new ObjectMapper(smileFactory);

    static {
        smileFactory.configure(SmileParser.Feature.REQUIRE_HEADER, false);
        smileMapper.registerModule(new JodaModule());
        smileMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @VisibleForTesting
    public static final Ordering<File> FILE_ORDERING = new Ordering<File>() {
        @Override
        public int compare(@Nullable final File left, @Nullable final File right) {
            if (left == null || right == null) {
                throw new NullPointerException();
            }

            // Order by the nano time
            return left.getAbsolutePath().compareTo(right.getAbsolutePath());
        }
    };

    private final String path;
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    public Replayer(final String path) {
        this.path = path;
    }

    // This method is only used by test code
    public List<SourceSamplesForTimestamp> readAll() {
        final List<SourceSamplesForTimestamp> samples = new ArrayList<SourceSamplesForTimestamp>();

        readAll(true, null, new Function<SourceSamplesForTimestamp, Void>() {
            @Override
            public Void apply(@Nullable final SourceSamplesForTimestamp input) {
                if (input != null) {
                    samples.add(input);
                }
                return null;
            }
        });

        return samples;
    }

    public void initiateShutdown() {
        shuttingDown.set(true);
    }

    public int readAll(final boolean deleteFiles, @Nullable final DateTime minStartTime, final Function<SourceSamplesForTimestamp, Void> fn) {
        final List<File> files = findCandidates();
        int filesSkipped = 0;
        for (final File file : FILE_ORDERING.sortedCopy(files)) {
            try {
                // Skip files whose last modification date is is earlier than the first start time.
                if (minStartTime != null && file.lastModified() < minStartTime.getMillis()) {
                    filesSkipped++;
                    continue;
                }
                read(file, fn);
                if (shuttingDown.get()) {
                    break;
                }

                if (deleteFiles) {
                    if (!file.delete()) {
                        log.warn("Unable to delete file: {}", file.getAbsolutePath());
                    }
                }
            } catch (IOException e) {
                log.warn("Exception replaying file: {}", file.getAbsolutePath(), e);
            }
        }
        return filesSkipped;
    }

    @VisibleForTesting
    public void read(final File file, final Function<SourceSamplesForTimestamp, Void> fn) throws IOException {
        final JsonParser smileParser = smileFactory.createJsonParser(file);
        if (smileParser.nextToken() != JsonToken.START_ARRAY) {
            return;
        }

        while (!shuttingDown.get() && smileParser.nextToken() != JsonToken.END_ARRAY) {
            final SourceSamplesForTimestamp sourceSamplesForTimestamp = smileParser.readValueAs(SourceSamplesForTimestamp.class);
            fn.apply(sourceSamplesForTimestamp);
        }

        smileParser.close();
    }


    public void purgeOldFiles(final DateTime purgeIfOlderDate) {
        final List<File> candidates = findCandidates();
        for (final File file : candidates) {
            if (file.lastModified() <= purgeIfOlderDate.getMillis()) {
                if (!file.delete()) {
                    log.warn("Unable to delete file: {}", file.getAbsolutePath());
                }
            }
        }
    }

    private List<File> findCandidates() {
        final File root = new File(path);
        final FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(final File file, final String s) {
                return s.endsWith("bin");
            }
        };

        final File [] foundFiles = root.listFiles(filter);
        return foundFiles == null ? ImmutableList.<File>of() : ImmutableList.<File>copyOf(foundFiles);
    }
}
