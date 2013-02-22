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

package com.ning.billing.meter.timeline.times;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;
import com.ning.billing.meter.timeline.util.DateTimeUtils;
import com.ning.billing.meter.timeline.util.Hex;

public class TestDefaultTimelineCoder extends MeterTestSuiteNoDB {

    private static final TimelineCoder timelineCoder = new DefaultTimelineCoder();

    @Test(groups = "fast")
    public void testBasicEncodeDecode() throws Exception {
        final DateTime firstTime = DateTimeUtils.dateTimeFromUnixSeconds(1000000);
        final List<DateTime> unencodedTimes = makeSomeTimes(firstTime);

        final byte[] compressedTimes = timelineCoder.compressDateTimes(unencodedTimes);
        //System.out.printf("Compressed times: %s\n", new String(Hex.encodeHex(compressedTimes)));
        final List<DateTime> decompressedTimes = timelineCoder.decompressDateTimes(compressedTimes);
        Assert.assertEquals(decompressedTimes.size(), unencodedTimes.size());
        for (int i = 0; i < unencodedTimes.size(); i++) {
            Assert.assertEquals(decompressedTimes.get(i), unencodedTimes.get(i));
        }
    }

    private List<DateTime> makeSomeTimes(final DateTime firstTime) {
        final List<DateTime> times = new ArrayList<DateTime>();
        Collections.addAll(times, firstTime, firstTime.plusSeconds(5), firstTime.plusSeconds(5), firstTime.plusSeconds(5),
                           firstTime.plusSeconds(1000), firstTime.plusSeconds(1000), firstTime.plusSeconds(2030), firstTime.plusSeconds(2060));
        return times;
    }

    @Test(groups = "fast")
    public void testRepeats() throws Exception {
        final DateTime firstTime = DateTimeUtils.dateTimeFromUnixSeconds(1293846);
        final List<DateTime> unencodedTimes = makeSomeTimes(firstTime);

        final byte[] compressedTimes = timelineCoder.compressDateTimes(unencodedTimes);
        final List<DateTime> decompressedTimes = timelineCoder.decompressDateTimes(compressedTimes);
        Assert.assertEquals(decompressedTimes.size(), unencodedTimes.size());
        for (int i = 0; i < unencodedTimes.size(); i++) {
            Assert.assertEquals(decompressedTimes.get(i), unencodedTimes.get(i));
        }
    }

    @Test(groups = "fast")
    public void testCombiningTimelinesByteRepeats() throws Exception {
        final int firstTime = 1293846;

        final List<DateTime> unencodedTimes1 = new ArrayList<DateTime>();
        final List<DateTime> unencodedTimes2 = new ArrayList<DateTime>();
        final int sampleCount = 10;
        for (int i = 0; i < sampleCount; i++) {
            unencodedTimes1.add(DateTimeUtils.dateTimeFromUnixSeconds(firstTime + i * 100));
            unencodedTimes2.add(DateTimeUtils.dateTimeFromUnixSeconds(firstTime + sampleCount * 100 + i * 100));
        }
        final byte[] compressedTimes1 = timelineCoder.compressDateTimes(unencodedTimes1);
        final byte[] compressedTimes2 = timelineCoder.compressDateTimes(unencodedTimes2);
        Assert.assertEquals(compressedTimes1.length, 8);
        Assert.assertEquals(compressedTimes1[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(compressedTimes1[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
        Assert.assertEquals(compressedTimes1[6] & 0xff, 9);
        Assert.assertEquals(compressedTimes1[7] & 0xff, 100);
        Assert.assertEquals(compressedTimes2.length, 8);
        Assert.assertEquals(compressedTimes2[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(compressedTimes2[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
        Assert.assertEquals(compressedTimes2[6] & 0xff, 9);
        Assert.assertEquals(compressedTimes2[7] & 0xff, 100);
        final List<byte[]> timesList = new ArrayList<byte[]>();
        timesList.add(compressedTimes1);
        timesList.add(compressedTimes2);
        final byte[] combinedTimes = timelineCoder.combineTimelines(timesList, null);
        Assert.assertEquals(combinedTimes.length, 8);
        Assert.assertEquals(combinedTimes[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(combinedTimes[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
        Assert.assertEquals(combinedTimes[6] & 0xff, 19);
        Assert.assertEquals(combinedTimes[7] & 0xff, 100);
        // Check for 19, not 20, since the first full time took one
        Assert.assertEquals(combinedTimes[6], 19);
        Assert.assertEquals(timelineCoder.countTimeBytesSamples(combinedTimes), 20);
    }

    @Test(groups = "fast")
    public void testCombiningTimelinesShortRepeats() throws Exception {
        final int sampleCount = 240;
        final int firstTime = 1293846;
        final List<DateTime> unencodedTimes1 = new ArrayList<DateTime>();
        final List<DateTime> unencodedTimes2 = new ArrayList<DateTime>();
        for (int i = 0; i < sampleCount; i++) {
            unencodedTimes1.add(DateTimeUtils.dateTimeFromUnixSeconds(firstTime + i * 100));
            unencodedTimes2.add(DateTimeUtils.dateTimeFromUnixSeconds(firstTime + sampleCount * 100 + i * 100));
        }
        final byte[] compressedTimes1 = timelineCoder.compressDateTimes(unencodedTimes1);
        final byte[] compressedTimes2 = timelineCoder.compressDateTimes(unencodedTimes2);
        Assert.assertEquals(compressedTimes1.length, 8);
        Assert.assertEquals(compressedTimes1[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(compressedTimes1[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
        Assert.assertEquals(compressedTimes1[6] & 0xff, sampleCount - 1);
        Assert.assertEquals(compressedTimes1[7] & 0xff, 100);
        Assert.assertEquals(compressedTimes2.length, 8);
        Assert.assertEquals(compressedTimes2[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(compressedTimes2[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
        Assert.assertEquals(compressedTimes2[6] & 0xff, sampleCount - 1);
        Assert.assertEquals(compressedTimes2[7] & 0xff, 100);
        final List<byte[]> timesList = new ArrayList<byte[]>();
        timesList.add(compressedTimes1);
        timesList.add(compressedTimes2);
        final byte[] combinedTimes = timelineCoder.combineTimelines(timesList, null);
        Assert.assertEquals(combinedTimes.length, 9);
        Assert.assertEquals(combinedTimes[0] & 0xff, TimelineOpcode.FULL_TIME.getOpcodeIndex());
        Assert.assertEquals(combinedTimes[5] & 0xff, TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex());
        Assert.assertEquals(combinedTimes[6] & 0xff, 1);
        Assert.assertEquals(combinedTimes[7] & 0xff, sampleCount * 2 - 1 - 256);
        Assert.assertEquals(combinedTimes[8], 100);
    }

    @Test(groups = "fast")
    public void testCombiningShortFragments() throws Exception {
        final byte[] fragment0 = new byte[]{(byte) -1, (byte) 0, (byte) 15, (byte) 66, (byte) 84, (byte) 20};
        final byte[] fragment1 = new byte[]{(byte) -1, (byte) 0, (byte) 15, (byte) 66, (byte) -122, (byte) 30};
        final byte[] fragment2 = new byte[]{(byte) -1, (byte) 0, (byte) 15, (byte) 66, (byte) -62, (byte) 30};
        final byte[] fragment3 = new byte[]{(byte) -1, (byte) 0, (byte) 15, (byte) 66, (byte) -2, (byte) 30};
        final byte[][] fragmentArray = new byte[][]{fragment0, fragment1, fragment2, fragment3};
        final byte[] combined = timelineCoder.combineTimelines(Arrays.asList(fragmentArray), null);
        final List<DateTime> restoredTimes = timelineCoder.decompressDateTimes(combined);
        final List<List<DateTime>> fragmentIntTimes = new ArrayList<List<DateTime>>();
        final List<DateTime> allFragmentTimes = new ArrayList<DateTime>();
        int totalLength = 0;
        for (final byte[] aFragmentArray : fragmentArray) {
            final List<DateTime> fragmentTimes = timelineCoder.decompressDateTimes(aFragmentArray);
            fragmentIntTimes.add(fragmentTimes);
            totalLength += fragmentTimes.size();
            for (final DateTime time : fragmentTimes) {
                allFragmentTimes.add(time);
            }
        }
        Assert.assertEquals(restoredTimes.size(), totalLength);
        for (int i = 0; i < totalLength; i++) {
            Assert.assertEquals(restoredTimes.get(i), allFragmentTimes.get(i));
        }
    }

    @Test(groups = "fast")
    public void testCombiningTimelinesRandomRepeats() throws Exception {
        final int[] increments = new int[]{30, 45, 10, 30, 20};
        final int[] repetitions = new int[]{1, 2, 3, 4, 5, 240, 250, 300};
        final int firstTimeInt = 1000000;
        final DateTime startTime = DateTimeUtils.dateTimeFromUnixSeconds(firstTimeInt);
        final List<DateTime> dateTimes = new ArrayList<DateTime>();
        final Random rand = new Random(0);
        DateTime nextTime = startTime;
        int count = 0;
        for (int i = 0; i < 20; i++) {
            final int increment = increments[rand.nextInt(increments.length)];
            final int repetition = repetitions[rand.nextInt(repetitions.length)];
            for (int r = 0; i < repetition; i++) {
                nextTime = nextTime.plusSeconds(increment);
                dateTimes.add(nextTime);
                count++;
            }
        }
        final byte[] allCompressedTime = timelineCoder.compressDateTimes(dateTimes);
        final List<DateTime> restoredTimes = timelineCoder.decompressDateTimes(allCompressedTime);
        Assert.assertEquals(restoredTimes.size(), dateTimes.size());
        for (int i = 0; i < count; i++) {
            Assert.assertEquals(restoredTimes.get(i), dateTimes.get(i));
        }
        for (int fragmentLength = 2; fragmentLength < count / 2; fragmentLength++) {
            final List<byte[]> fragments = new ArrayList<byte[]>();
            final int fragmentCount = (int) Math.ceil((double) count / (double) fragmentLength);
            for (int fragCounter = 0; fragCounter < fragmentCount; fragCounter++) {
                final int fragIndex = fragCounter * fragmentLength;
                final List<DateTime> fragment = dateTimes.subList(fragIndex, Math.min(count, fragIndex + fragmentLength));
                fragments.add(timelineCoder.compressDateTimes(fragment));
            }
            final byte[] combined = timelineCoder.combineTimelines(fragments, null);
            final List<DateTime> restoredDateTimes = timelineCoder.decompressDateTimes(combined);
            //Assert.assertEquals(intTimes.length, count);
            for (int i = 0; i < count; i++) {
                Assert.assertEquals(restoredDateTimes.get(i), dateTimes.get(i));
            }
        }
    }

    @Test(groups = "fast")
    public void test65KRepeats() throws Exception {
        final int count = 0;
        final List<DateTime> dateTimes = new ArrayList<DateTime>();
        DateTime time = DateTimeUtils.dateTimeFromUnixSeconds(1000000);
        for (int i = 0; i < 20; i++) {
            time = time.plusSeconds(200);
            dateTimes.add(time);
        }
        for (int i = 0; i < 0xFFFF + 100; i++) {
            time = time.plusSeconds(100);
            dateTimes.add(time);
        }
        final byte[] timeBytes = timelineCoder.compressDateTimes(dateTimes);
        final String hex = new String(Hex.encodeHex(timeBytes));
        // Here are the compressed samples: ff000f4308fe13c8fdffff64fe6464
        // Translation:
        // [ff 00 0f 43 08] means absolution time 1000000
        // [fe 13 c8] means repeat 19 times delta 200 seconds
        // [fd ff ff 64] means repeat 65525 times delta 100 seconds
        // [fe 64 64] means repeat 100 times delta 100 seconds
        Assert.assertEquals(timeBytes, Hex.decodeHex("ff000f4308fe13c8fdffff64fe6464".toCharArray()));
        final List<DateTime> restoredSamples = timelineCoder.decompressDateTimes(timeBytes);
        Assert.assertEquals(restoredSamples.size(), dateTimes.size());
        for (int i = 0; i < count; i++) {
            Assert.assertEquals(restoredSamples.get(i), DateTimeUtils.unixSeconds(dateTimes.get(i)));
        }
    }

    @Test(groups = "fast")
    public void testCombiningTimesError() throws Exception {
        final byte[] times1 = Hex.decodeHex("ff10000001fe0310ff1000011bfe0310".toCharArray());
        final byte[] times2 = Hex.decodeHex("ff10000160".toCharArray());
        final List<byte[]> timesList = new ArrayList<byte[]>();
        timesList.add(times1);
        timesList.add(times2);
        final byte[] combinedTimes = timelineCoder.combineTimelines(timesList, null);
        final String hexCombinedTimes = new String(Hex.encodeHex(combinedTimes));
        //System.out.printf("Combined times: %s\n", hexCombinedTimes);
        Assert.assertEquals(hexCombinedTimes, "ff10000001fe0310eafe031015");
    }

    @Test(groups = "fast")
    public void testTimeCursorWithZeroDeltaWithNext() throws Exception {
        // This caused a TimeCursor problem
        // FF 4F 91 D5 BC FE 02 1E 00 FE 02 1E FF 79 0B 44 22
        // FF 4F 91 D5 BC FE 02 1E 00 FE 02 1E
        // FF 4F 91 D5 BC          Absolute time
        // FE 02 1E                Repeated delta time: count 2, delta: 30
        // 00                      Delta 0.  Why did this happen?
        // FE 02 1E                Repeated delta time: count 2, delta: 30
        // FF 79 0B 44 22          Absolute time
        // Total samples: 6
        final int sampleCount = 7;
        final byte[] times = Hex.decodeHex("FF4F91D5BCFE021E00FE021EFF790B4422".toCharArray());
        final DefaultTimelineCursor cursor = new DefaultTimelineCursor(times, sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            final DateTime nextTime = cursor.getNextTime();
            Assert.assertNotNull(nextTime);
        }
        try {
            final DateTime lastTime = cursor.getNextTime();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "fast")
    public void testTimeCursorWithZeroDeltaWithSampleSkip() throws Exception {
        // This caused a TimeCursor problem
        // FF 4F 91 D5 BC FE 02 1E 00 FE 02 1E FF 79 0B 44 22
        // FF 4F 91 D5 BC FE 02 1E 00 FE 02 1E
        // FF 4F 91 D5 BC          Absolute time
        // FE 02 1E                Repeated delta time: count 2, delta: 30
        // 00                      Delta 0.  Why did this happen?
        // FE 02 1E                Repeated delta time: count 2, delta: 30
        // FF 79 0B 44 22          Absolute time
        // Total samples: 6
        final int sampleCount = 7;
        final byte[] times = Hex.decodeHex("FF4F91D5BCFE021E00FE021EFF790B4422".toCharArray());
        final DefaultTimelineCursor cursor = new DefaultTimelineCursor(times, sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            final DateTime nextTime = cursor.getNextTime();
            Assert.assertNotNull(nextTime);
            cursor.skipToSampleNumber(i + 1);
        }
        try {
            final DateTime lastTime = cursor.getNextTime();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "fast")
    public void testTimeCursorThatShowedError() throws Exception {
        // 39 bytes are: ff4f90f67afd03ce1e1ffe1a1e1d01fe771e1d01fd01df1e1d1ffe761e1d01fe771e1d01fe571e
        // 1944 samples; error at 1934
        final int sampleCount = 1944;
        //final byte[] times = Hex.decodeHex("ff4f90f67afd03ce1e1ffe1a1e1d01fe771e1d01fd01df1e1d1ffe761e1d01fe771e1d01fe571e".toCharArray());
        final byte[] times = Hex.decodeHex("00000018FF4F8FE521FD023D1E1FFEF01E1D01FE771E1D01FD03E21EFE07980F".toCharArray());
        Assert.assertEquals(times.length, 32);
        final DefaultTimelineCursor cursor = new DefaultTimelineCursor(times, sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            final DateTime nextTime = cursor.getNextTime();
            Assert.assertNotNull(nextTime);
            cursor.skipToSampleNumber(i + 1);
        }

        try {
            final DateTime lastTime = cursor.getNextTime();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError1() throws Exception {
        checkCombinedTimelines("ff4f91fb14fe631e00fe151e", "ff4f920942");
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError2() throws Exception {
        checkCombinedTimelines("ff4f922618fe111e78fe111efe02005a", "ff4f923428");
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError3() throws Exception {
        checkCombinedTimelines("ff4f9224ecfe091e", "ff4f922618fe071e78fe111e78fe111e78fe111e78fe111efe02005afe121e78fe031e",
                               "ff4f923428fe0d1e7dfe111e78fe111e78fe111e78fe0b1e00fe061e78fe111e", "ff4f92427cfe111e78fe111e78fe111e78fe111e82fe041e1d01fe0c1e78fe0e1e");
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError4() throws Exception {
        checkCombinedTimelines("ff4f95ba83fe021e", "ff4f95d595", "ff4f95e297fe021e");
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError5() throws Exception {
        checkCombinedTimelines("ff00000100", "ff00000200");
    }

    @Test(groups = "fast")
    public void testTimeCombineTimesError6() throws Exception {
        checkCombinedTimelines("ff4f95ac73fe471e00fe301e", "ff4f95ba83fe471e00fe311e", "ff4f95d595", "ff4f95e297fe091e");
        checkCombinedTimelines("ff4f95ac7afe461e1d01fe301e", "ff4f95ba8afe471e00fe041e1ffe2b1e", "ff4f95d59d", "ff4f95e281fe0a1e");
        checkCombinedTimelines("ff4f95aca4fe461e00fe311e", "ff4f95bab4fe461e00fe261e1f1dfe0a1e", "ff4f95d5a8", "ff4f95e28cfe091e");
        checkCombinedTimelines("ff4f95ac88fe471e00fe311e", "ff4f95bab6fe461e00fe321e", "ff4f95d5aa", "ff4f95e28efe091eff4f95e4e6fe0a1e");
        checkCombinedTimelines("ff4f95e394ff4f95e4fcfe0e1e5afe341e00fe221e", "ff4f95f12cfe551e00fe221e", "ff4f95ff3cfe551e00fe231e", "ff4f960d6afe541e00fe231e");
        checkCombinedTimelines("ff4f95e396ff4f95e4fefe0e1e5afe341e00fe271e", "ff4f95f1c4fe501e00fe281e", "ff4f95fff2fe4f1e00fe281e", "ff4f960e02fe4f1e00fe291e");
    }

    private void checkCombinedTimelines(final String... timelines) throws Exception {
        final List<byte[]> timeParts = new ArrayList<byte[]>();
        for (final String timeline : timelines) {
            timeParts.add(Hex.decodeHex(timeline.toCharArray()));
        }
        int sampleCount = 0;
        int byteCount = 0;
        for (final byte[] timePart : timeParts) {
            byteCount += timePart.length;
            sampleCount += timelineCoder.countTimeBytesSamples(timePart);
        }
        final byte[] concatedTimes = new byte[byteCount];
        int offset = 0;
        for (final byte[] timePart : timeParts) {
            final int length = timePart.length;
            System.arraycopy(timePart, 0, concatedTimes, offset, length);
            offset += length;
        }
        final byte[] newCombined = timelineCoder.combineTimelines(timeParts, null);
        final int newCombinedLength = timelineCoder.countTimeBytesSamples(newCombined);
        final DefaultTimelineCursor concatedCursor = new DefaultTimelineCursor(concatedTimes, sampleCount);
        final DefaultTimelineCursor combinedCursor = new DefaultTimelineCursor(newCombined, sampleCount);
        for (int i = 0; i < sampleCount; i++) {
            final DateTime concatedTime = concatedCursor.getNextTime();
            final DateTime combinedTime = combinedCursor.getNextTime();
            Assert.assertEquals(combinedTime, concatedTime);
        }
        Assert.assertEquals(newCombinedLength, sampleCount);
    }
}
