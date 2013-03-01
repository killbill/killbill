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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.util.DateTimeUtils;
import com.ning.billing.meter.timeline.util.Hex;

public class DefaultTimelineCoder implements TimelineCoder {

    public static final Logger log = LoggerFactory.getLogger(TimelineCoder.class);
    public static final int MAX_SHORT_REPEAT_COUNT = 0xFFFF;
    public static final int MAX_BYTE_REPEAT_COUNT = 0xFF;

    /**
     * Convert the array of unix times to a compressed timeline, and return the byte array
     * representing that compressed timeline
     *
     * @param times an int array giving the unix times to be compressed
     * @return the compressed timeline
     */
    @Override
    public byte[] compressDateTimes(final List<DateTime> times) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final DataOutputStream dataStream = new DataOutputStream(outputStream);
        try {
            int lastTime = 0;
            int lastDelta = 0;
            int repeatCount = 0;
            for (final DateTime time : times) {
                final int newTime = DateTimeUtils.unixSeconds(time);
                if (lastTime == 0) {
                    lastTime = newTime;
                    writeTime(0, lastTime, dataStream);
                    continue;
                } else if (newTime < lastTime) {
                    log.warn("In TimelineCoder.compressTimes(), newTime {} is < lastTime {}; ignored", newTime, lastTime);
                    continue;
                }
                final int delta = newTime - lastTime;
                final boolean deltaWorks = delta <= TimelineOpcode.MAX_DELTA_TIME;
                final boolean sameDelta = repeatCount > 0 && delta == lastDelta;
                if (deltaWorks) {
                    if (sameDelta) {
                        repeatCount++;
                        if (repeatCount == MAX_SHORT_REPEAT_COUNT) {
                            writeRepeatedDelta(delta, repeatCount, dataStream);
                            repeatCount = 0;
                        }
                    } else {
                        if (repeatCount > 0) {
                            writeRepeatedDelta(lastDelta, repeatCount, dataStream);
                        }
                        repeatCount = 1;
                    }
                    lastDelta = delta;
                } else {
                    if (repeatCount > 0) {
                        writeRepeatedDelta(lastDelta, repeatCount, dataStream);
                    }
                    writeTime(0, newTime, dataStream);
                    repeatCount = 0;
                    lastDelta = 0;
                }
                lastTime = newTime;
            }
            if (repeatCount > 0) {
                writeRepeatedDelta(lastDelta, repeatCount, dataStream);
            }
            dataStream.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("Exception compressing times list of length {}", times.size(), e);
            return null;
        }
    }

    @Override
    public byte[] combineTimelines(final List<byte[]> timesList, final Integer sampleCount) {
        final byte[] timeBytes = combineTimelines(timesList);
        final int combinedSampleCount = countTimeBytesSamples(timeBytes);
        if (sampleCount != null && sampleCount != combinedSampleCount) {
            final StringBuilder builder = new StringBuilder();
            builder.append("In compressTimelineTimes(), combined sample count is ")
                   .append(combinedSampleCount)
                   .append(", but sample count is ")
                   .append(sampleCount)
                   .append(", combined TimeBytes ")
                   .append(Hex.encodeHex(timeBytes))
                   .append(", ")
                   .append(timesList.size())
                   .append(" chunks");
            for (final byte[] bytes : timesList) {
                builder.append(", ")
                       .append(Hex.encodeHex(bytes));
            }
            log.error(builder.toString());
        }
        return timeBytes;
    }

    private byte[] combineTimelines(final List<byte[]> timesList) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final DataOutputStream dataStream = new DataOutputStream(outputStream);
        try {
            int lastTime = 0;
            int lastDelta = 0;
            int repeatCount = 0;
            int chunkCounter = 0;
            for (byte[] times : timesList) {
                final ByteArrayInputStream byteStream = new ByteArrayInputStream(times);
                final DataInputStream byteDataStream = new DataInputStream(byteStream);
                int byteCursor = 0;
                while (true) {
                    // Part 1: Get the opcode, and come up with newTime, newCount and newDelta
                    final int opcode = byteDataStream.read();
                    if (opcode == -1) {
                        break;
                    }
                    byteCursor++;
                    int newTime = 0;
                    int newCount = 0;
                    int newDelta = 0;
                    boolean useNewDelta = false;
                    boolean nonDeltaTime = false;
                    if (opcode == TimelineOpcode.FULL_TIME.getOpcodeIndex()) {
                        newTime = byteDataStream.readInt();
                        if (newTime < lastTime) {
                            log.warn("In TimelineCoder.combineTimeLines(), the fulltime read is %d, but the lastTime is %d; setting newTime to lastTime",
                                     newTime, lastTime);
                            newTime = lastTime;
                        }
                        byteCursor += 4;
                        if (lastTime == 0) {
                            writeTime(0, newTime, dataStream);
                            lastTime = newTime;
                            lastDelta = 0;
                            repeatCount = 0;
                            continue;
                        } else if (newTime - lastTime <= TimelineOpcode.MAX_DELTA_TIME) {
                            newDelta = newTime - lastTime;
                            useNewDelta = true;
                            newCount = 1;
                        } else {
                            nonDeltaTime = true;
                        }
                    } else if (opcode <= TimelineOpcode.MAX_DELTA_TIME) {
                        newTime = lastTime + opcode;
                        newDelta = opcode;
                        useNewDelta = true;
                        newCount = 1;
                    } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex()) {
                        newCount = byteDataStream.read();
                        newDelta = byteDataStream.read();
                        useNewDelta = true;
                        byteCursor += 2;
                        if (lastTime != 0) {
                            newTime = lastTime + newDelta * newCount;
                        } else {
                            throw new IllegalStateException(String.format("In TimelineCoder.combineTimelines, lastTime is 0 byte opcode = %d, byteCursor %d, chunkCounter %d, chunk %s",
                                                                          opcode, byteCursor, chunkCounter, new String(Hex.encodeHex(times))));
                        }
                    } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex()) {
                        newCount = byteDataStream.readUnsignedShort();
                        newDelta = byteDataStream.read();
                        useNewDelta = true;
                        byteCursor += 3;
                        if (lastTime != 0) {
                            newTime = lastTime + newDelta * newCount;
                        }
                    } else {
                        throw new IllegalStateException(String.format("In TimelineCoder.combineTimelines, Unrecognized byte opcode = %d, byteCursor %d, chunkCounter %d, chunk %s",
                                                                      opcode, byteCursor, chunkCounter, new String(Hex.encodeHex(times))));
                    }
                    // Part 2: Combine existing state represented in lastTime, lastDelta and repeatCount with newTime, newCount and newDelta
                    if (lastTime == 0) {
                        log.error("In combineTimelines(), lastTime is 0; byteCursor {}, chunkCounter {}, times {}", new Object[]{byteCursor, chunkCounter, new String(Hex.encodeHex(times))});
                    } else if (repeatCount > 0) {
                        if (lastDelta == newDelta && newCount > 0) {
                            repeatCount += newCount;
                            lastTime = newTime;
                        } else {
                            writeRepeatedDelta(lastDelta, repeatCount, dataStream);
                            if (useNewDelta) {
                                lastDelta = newDelta;
                                repeatCount = newCount;
                                lastTime = newTime;
                            } else {
                                writeTime(lastTime, newTime, dataStream);
                                lastTime = newTime;
                                lastDelta = 0;
                                repeatCount = 0;
                            }
                        }
                    } else if (nonDeltaTime) {
                        writeTime(lastTime, newTime, dataStream);
                        lastTime = newTime;
                        lastDelta = 0;
                        repeatCount = 0;
                    } else if (lastDelta == 0) {
                        lastTime = newTime;
                        repeatCount = newCount;
                        lastDelta = newDelta;
                    }
                }
                chunkCounter++;
            }

            if (repeatCount > 0) {
                writeRepeatedDelta(lastDelta, repeatCount, dataStream);
            }
            dataStream.flush();

            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("In combineTimesLines(), exception combining timelines", e);
            return new byte[0];
        }
    }

    @Override
    public List<DateTime> decompressDateTimes(final byte[] compressedTimes) {
        final List<DateTime> dateTimeList = new ArrayList<DateTime>(compressedTimes.length * 4);
        final ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedTimes);
        final DataInputStream byteDataStream = new DataInputStream(byteStream);
        int opcode;
        int lastTime = 0;
        try {
            while (true) {
                opcode = byteDataStream.read();
                if (opcode == -1) {
                    break;
                }

                if (opcode == TimelineOpcode.FULL_TIME.getOpcodeIndex()) {
                    lastTime = byteDataStream.readInt();
                    dateTimeList.add(DateTimeUtils.dateTimeFromUnixSeconds(lastTime));
                } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex()) {
                    final int repeatCount = byteDataStream.readUnsignedByte();
                    final int delta = byteDataStream.readUnsignedByte();
                    for (int i = 0; i < repeatCount; i++) {
                        lastTime = lastTime + delta;
                        dateTimeList.add(DateTimeUtils.dateTimeFromUnixSeconds(lastTime));
                    }
                } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex()) {
                    final int repeatCount = byteDataStream.readUnsignedShort();
                    final int delta = byteDataStream.readUnsignedByte();
                    for (int i = 0; i < repeatCount; i++) {
                        lastTime = lastTime + delta;
                        dateTimeList.add(DateTimeUtils.dateTimeFromUnixSeconds(lastTime));
                    }
                } else {
                    // The opcode is itself a singleton delta
                    lastTime = lastTime + opcode;
                    dateTimeList.add(DateTimeUtils.dateTimeFromUnixSeconds(lastTime));
                }
            }
        } catch (IOException e) {
            log.error("In decompressTimes(), exception decompressing", e);
        }

        return dateTimeList;
    }

    @Override
    public int countTimeBytesSamples(final byte[] timeBytes) {
        int count = 0;
        try {
            final ByteArrayInputStream byteStream = new ByteArrayInputStream(timeBytes);
            final DataInputStream byteDataStream = new DataInputStream(byteStream);
            int opcode;
            while ((opcode = byteDataStream.read()) != -1) {
                if (opcode == TimelineOpcode.FULL_TIME.getOpcodeIndex()) {
                    byteDataStream.readInt();
                    count++;
                } else if (opcode <= TimelineOpcode.MAX_DELTA_TIME) {
                    count++;
                } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex()) {
                    count += byteDataStream.read();
                    //noinspection ResultOfMethodCallIgnored
                    byteDataStream.read();
                } else if (opcode == TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex()) {
                    count += byteDataStream.readUnsignedShort();
                    //noinspection ResultOfMethodCallIgnored
                    byteDataStream.read();
                } else {
                    throw new IllegalStateException(String.format("In TimelineCoder.countTimeBytesSamples(), unrecognized opcode %d", opcode));
                }
            }
            return count;
        } catch (IOException e) {
            log.error("IOException while counting timeline samples", e);
            return count;
        }
    }

    private void writeRepeatedDelta(final int delta, final int repeatCount, final DataOutputStream dataStream) throws IOException {
        if (repeatCount > 1) {
            if (repeatCount > MAX_BYTE_REPEAT_COUNT) {
                dataStream.writeByte(TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex());
                dataStream.writeShort(repeatCount);
            } else if (repeatCount == 2) {
                dataStream.writeByte(delta);
            } else {
                dataStream.writeByte(TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex());
                dataStream.writeByte(repeatCount);
            }
        }
        dataStream.writeByte(delta);
    }

    private void writeTime(final int lastTime, final int newTime, final DataOutputStream dataStream) throws IOException {
        if (newTime > lastTime) {
            final int delta = (newTime - lastTime);
            if (delta <= TimelineOpcode.MAX_DELTA_TIME) {
                dataStream.writeByte(delta);
            } else {
                dataStream.writeByte(TimelineOpcode.FULL_TIME.getOpcodeIndex());
                dataStream.writeInt(newTime);
            }
        } else if (newTime == lastTime) {
            dataStream.writeByte(0);
        }
    }
}
