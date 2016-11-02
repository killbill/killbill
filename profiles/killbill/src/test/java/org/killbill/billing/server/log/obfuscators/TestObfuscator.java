/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.server.log.obfuscators;

import java.util.regex.Pattern;

import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.collect.ImmutableList;

public class TestObfuscator extends ServerTestSuiteNoDB {

    private final Obfuscator obfuscator = new Obfuscator() {
        @Override
        public String obfuscate(final String originalString, final ILoggingEvent event) {
            return null;
        }
    };

    @Test(groups = "fast")
    public void testObfuscateWithOnePattern() throws Exception {
        final Pattern pattern = Pattern.compile("number=([^;]+)");
        final ImmutableList<Pattern> patterns = ImmutableList.<Pattern>of(pattern);
        Assert.assertEquals(obfuscator.obfuscate("number=1234;number=12345;number=123456;number=1234567;number=12345678;number=123456789", patterns, Mockito.mock(ILoggingEvent.class)),
                            "number=****;number=*****;number=******;number=*******;number=********;number=*********");

    }

    @Test(groups = "fast")
    public void testObfuscateWithMultiplePatterns() throws Exception {
        final Pattern pattern1 = Pattern.compile("number=([^;]+)");
        final Pattern pattern2 = Pattern.compile("numberB=([^;]+)");
        final ImmutableList<Pattern> patterns = ImmutableList.<Pattern>of(pattern1, pattern2);
        Assert.assertEquals(obfuscator.obfuscate("number=1234;numberB=12345;number=123456;numberB=1234567;number=12345678;numberB=123456789", patterns, Mockito.mock(ILoggingEvent.class)),
                            "number=****;numberB=*****;number=******;numberB=*******;number=********;numberB=*********");

    }

    @Test(groups = "fast")
    public void testObfuscateConfidentialData() {
        Assert.assertEquals(obfuscator.obfuscateConfidentialData("5137004986396403", "6403"), "************");
    }
}
