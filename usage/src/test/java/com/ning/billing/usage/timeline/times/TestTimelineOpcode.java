package com.ning.billing.usage.timeline.times;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestTimelineOpcode {

    @Test(groups = "fast")
    public void testMaxDeltaTime() throws Exception {
        for (final TimelineOpcode opcode : TimelineOpcode.values()) {
            Assert.assertTrue(opcode.getOpcodeIndex() >= TimelineOpcode.MAX_DELTA_TIME);
        }
    }
}
