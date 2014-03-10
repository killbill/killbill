/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.osgi;

import java.util.regex.Matcher;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;

public class TestKillbillActivator extends GuicyKillbillTestSuiteNoDB {

    @Test(groups= "fast")
    public void testPluginNamePatternGood() {
        Matcher m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("a");
        Assert.assertTrue(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("abc1223");
        Assert.assertTrue(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("abc123-");
        Assert.assertTrue(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("abc123-zs");
        Assert.assertTrue(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("xyz_1");
        Assert.assertTrue(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("osgi-payment-plugin");
        Assert.assertTrue(m.matches());
    }


    @Test(groups= "fast")
    public void testPluginNamePatternBad() {
        Matcher m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("1abd");
        Assert.assertFalse(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("Tata");
        Assert.assertFalse(m.matches());


        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("Tata#");
        Assert.assertFalse(m.matches());

        m = KillbillActivator.PLUGIN_NAME_PATTERN.matcher("yo:");
        Assert.assertFalse(m.matches());
    }

    @Test(groups = "false")
    public void testPluginNameLength() {

        String pluginNameGood = "foofofoSuperFoo";
        Assert.assertTrue(pluginNameGood.length() < KillbillActivator.PLUGIN_NAME_MAX_LENGTH);

        String pluginNameBAd = "foofoofooSuperFoosupersuperLongreallyLong";
        Assert.assertFalse(pluginNameBAd.length() < KillbillActivator.PLUGIN_NAME_MAX_LENGTH);
    }
}
