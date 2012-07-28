package com.ning.billing.usage.timeline.samples;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSampleOpcode {

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
