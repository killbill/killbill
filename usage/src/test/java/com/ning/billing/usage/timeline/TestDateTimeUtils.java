package com.ning.billing.usage.timeline;

import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

public class TestDateTimeUtils {

    private final Clock clock = new ClockMock();

    @Test(groups = "fast")
    public void testRoundTrip() throws Exception {
        final DateTime utcNow = clock.getUTCNow();
        final int unixSeconds = DateTimeUtils.unixSeconds(utcNow);
        final DateTime dateTimeFromUnixSeconds = DateTimeUtils.dateTimeFromUnixSeconds(unixSeconds);

        Assert.assertEquals(Seconds.secondsBetween(dateTimeFromUnixSeconds, utcNow).getSeconds(), 0);
    }
}
