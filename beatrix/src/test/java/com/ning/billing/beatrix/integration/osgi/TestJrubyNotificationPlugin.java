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

package com.ning.billing.beatrix.integration.osgi;

import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;
import com.ning.billing.util.tag.Tag;

public class TestJrubyNotificationPlugin extends TestOSGIBase {

    private final String BUNDLE_TEST_RESOURCE_PREFIX = "killbill-notification-test";
    private final String BUNDLE_TEST_RESOURCE = BUNDLE_TEST_RESOURCE_PREFIX + ".tar.gz";

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {

        // OSGIDataSourceConfig
        super.beforeClass();

        // This is extracted from surefire system configuration-- needs to be added explicitly in IntelliJ for correct running
        final String killbillVersion = System.getProperty("killbill.version");

        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion(BUNDLE_TEST_RESOURCE, osgiConfig, killbillVersion);
        setupTest.setupJrubyBundle();
    }

    @Test(groups = "slow")
    public void testOnEventForAccountCreation() throws Exception {

        // Once we create the account we give the hand to the jruby notification plugin
        // which will handle the ExtBusEvent and start updating the account, create tag definition and finally create a tag.
        // We wait for all that to occur and declare victory if we see the TagDefinition/Tag creation.
        busHandler.pushExpectedEvents(NextEvent.TAG_DEFINITION, NextEvent.TAG);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(4));
        assertListenerStatus();

        final List<Tag> tags = tagUserApi.getTagsForAccount(account.getId(), false, callContext);
        Assert.assertEquals(tags.size(), 1);
        //final Tag tag = tags.get(0);
    }
}
