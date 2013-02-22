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
import java.io.DataInputStream;
import java.io.IOException;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.meter.timeline.util.DateTimeUtils;

public class DefaultTimelineCursor implements TimelineCursor {

    private static final Logger log = LoggerFactory.getLogger(DefaultTimelineCursor.class);

    private final DataInputStream timelineDataStream;
    private int sampleCount;
    private int sampleNumber;
    private int byteCursor;
    private int lastValue;
    private int delta;
    private int repeatCount;

    public DefaultTimelineCursor(final byte[] times, final int sampleCount) {
        this.timelineDataStream = new DataInputStream(new ByteArrayInputStream(times));
        this.sampleCount = sampleCount;
        this.sampleNumber = 0;
        this.byteCursor = 0;
        this.lastValue = 0;
        this.delta = 0;
        this.repeatCount = 0;
    }

    private int getNextTimeInternal() {
        try {
            if (repeatCount > 0) {
                repeatCount--;
                lastValue += delta;
            } else {
                final int nextOpcode = timelineDataStream.read();
                byteCursor++;
                if (nextOpcode == -1) {
                    return nextOpcode;
                }
                if (nextOpcode == TimelineOpcode.FULL_TIME.getOpcodeIndex()) {
                    lastValue = timelineDataStream.readInt();
                    byteCursor += 4;
                } else if (nextOpcode == TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex()) {
                    repeatCount = timelineDataStream.readUnsignedByte() - 1;
                    delta = timelineDataStream.read();
                    byteCursor += 2;
                    lastValue += delta;
                } else if (nextOpcode == TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex()) {
                    repeatCount = timelineDataStream.readUnsignedShort() - 1;
                    delta = timelineDataStream.read();
                    byteCursor += 3;
                    lastValue += delta;
                } else if (nextOpcode <= TimelineOpcode.MAX_DELTA_TIME) {
                    lastValue += nextOpcode;
                } else {
                    throw new IllegalStateException(String.format("In TimeIterator.getNextTime(), unknown opcode %x at offset %d", nextOpcode, byteCursor));
                }
            }
            sampleNumber++;
            if (sampleNumber > sampleCount) {
                log.error("In TimeIterator.getNextTime(), after update, sampleNumber %d > sampleCount %d", sampleNumber, sampleCount);
            }
            return lastValue;
        } catch (IOException e) {
            log.error("IOException in TimeIterator.getNextTime()", e);
            return -1;
        }
    }

    @Override
    public void skipToSampleNumber(final int finalSampleNumber) {
        if (finalSampleNumber > sampleCount) {
            log.error("In TimeIterator.skipToSampleNumber(), finalSampleCount {} > sampleCount {}", finalSampleNumber, sampleCount);
        }
        while (sampleNumber < finalSampleNumber) {
            try {
                if (repeatCount > 0) {
                    final int countToSkipInRepeat = Math.min(finalSampleNumber - sampleNumber, repeatCount);
                    sampleNumber += countToSkipInRepeat;
                    repeatCount -= countToSkipInRepeat;
                    lastValue += countToSkipInRepeat * delta;
                } else {
                    final int nextOpcode = timelineDataStream.read();
                    if (nextOpcode == -1) {
                        return;
                    }
                    byteCursor++;
                    if (nextOpcode == TimelineOpcode.FULL_TIME.getOpcodeIndex()) {
                        lastValue = timelineDataStream.readInt();
                        byteCursor += 4;
                        sampleNumber++;
                    } else if (nextOpcode == TimelineOpcode.REPEATED_DELTA_TIME_BYTE.getOpcodeIndex()) {
                        repeatCount = timelineDataStream.readUnsignedByte() - 1;
                        delta = timelineDataStream.read();
                        byteCursor += 2;
                        lastValue += delta;
                        sampleNumber++;
                    } else if (nextOpcode == TimelineOpcode.REPEATED_DELTA_TIME_SHORT.getOpcodeIndex()) {
                        repeatCount = timelineDataStream.readUnsignedShort() - 1;
                        delta = timelineDataStream.read();
                        byteCursor += 3;
                        lastValue += delta;
                        sampleNumber++;
                    } else if (nextOpcode <= TimelineOpcode.MAX_DELTA_TIME) {
                        lastValue += nextOpcode;
                        sampleNumber++;
                    } else {
                        throw new IllegalStateException(String.format("In TimeIterator.skipToSampleNumber(), unknown opcode %x at offset %d", nextOpcode, byteCursor));
                    }
                }
            } catch (IOException e) {
                log.error("IOException in TimeIterator.getNextTime()", e);
            }
        }
    }

    @Override
    public DateTime getNextTime() {
        final int nextTime = getNextTimeInternal();
        if (nextTime == -1) {
            throw new IllegalStateException(String.format("In DecodedSampleOutputProcessor.getNextTime(), got -1 from timeCursor.getNextTimeInternal()"));
        } else {
            return DateTimeUtils.dateTimeFromUnixSeconds(nextTime);
        }
    }
}
