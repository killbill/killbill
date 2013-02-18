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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.timeline.sources.SourceSamplesForTimestamp;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class TestReplayer extends MeterTestSuiteNoDB {

    private static final File basePath = new File(System.getProperty("java.io.tmpdir"), "TestReplayer-" + System.currentTimeMillis());

    private static final class MockReplayer extends Replayer {

        private final List<File> expectedFiles;
        private int seen = 0;

        public MockReplayer(final String path, final List<File> expectedFiles) {
            super(path);
            this.expectedFiles = expectedFiles;
        }

        @Override
        public void read(final File file, final Function<SourceSamplesForTimestamp, Void> fn) throws IOException {
            Assert.assertEquals(file, expectedFiles.get(seen));
            seen++;
        }

        public int getSeen() {
            return seen;
        }
    }

    private final StreamyBytesPersistentOutputStream outputStream = new StreamyBytesPersistentOutputStream(basePath.toString(), "pweet", null, true);

    @Test(groups = "fast")
    public void testStringOrdering() throws Exception {
        final File file1 = new File("aaa.bbb.12345.bin");
        final File file2 = new File("aaa.bbb.12346.bin");
        final File file3 = new File("aaa.bbb.02345.bin");

        final List<File> sortedCopy = Replayer.FILE_ORDERING.sortedCopy(ImmutableList.<File>of(file2, file1, file3));
        Assert.assertEquals(sortedCopy.get(0), file3);
        Assert.assertEquals(sortedCopy.get(1), file1);
        Assert.assertEquals(sortedCopy.get(2), file2);
    }

    @Test(groups = "slow")
    public void testOrdering() throws Exception {
        Assert.assertTrue(basePath.mkdir());

        final List<String> filePathsCreated = new ArrayList<String>();
        final List<File> filesCreated = new ArrayList<File>();
        final int expected = 50;

        for (int i = 0; i < expected; i++) {
            filePathsCreated.add(outputStream.getFileName());
            Thread.sleep(17);
        }

        // Create the files in the opposite ordering to make sure we can re-read them in order
        for (int i = expected - 1; i >= 0; i--) {
            final File file = new File(filePathsCreated.get(i));
            Assert.assertTrue(file.createNewFile());
            filesCreated.add(file);
        }

        final MockReplayer replayer = new MockReplayer(basePath.toString(), Lists.reverse(filesCreated));
        replayer.readAll();

        Assert.assertEquals(replayer.getSeen(), expected);
    }
}
