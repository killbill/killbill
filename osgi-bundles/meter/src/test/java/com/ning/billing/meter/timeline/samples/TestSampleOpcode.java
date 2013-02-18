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

package com.ning.billing.meter.timeline.samples;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;

public class TestSampleOpcode extends MeterTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetKnownOpcodeFromIndex() throws Exception {
        for (final SampleOpcode opcode : SampleOpcode.values()) {
            final SampleOpcode opcodeFromIndex = SampleOpcode.getOpcodeFromIndex(opcode.getOpcodeIndex());
            Assert.assertEquals(opcodeFromIndex, opcode);

            Assert.assertEquals(opcodeFromIndex.getOpcodeIndex(), opcode.getOpcodeIndex());
            Assert.assertEquals(opcodeFromIndex.getByteSize(), opcode.getByteSize());
            Assert.assertEquals(opcodeFromIndex.getNoArgs(), opcode.getNoArgs());
            Assert.assertEquals(opcodeFromIndex.getRepeater(), opcode.getRepeater());
            Assert.assertEquals(opcodeFromIndex.getReplacement(), opcode.getReplacement());
        }
    }

    @Test(groups = "fast", expectedExceptions = IllegalArgumentException.class)
    public void testgetUnknownOpcodeFromIndex() throws Exception {
        SampleOpcode.getOpcodeFromIndex(Integer.MAX_VALUE);
    }
}
