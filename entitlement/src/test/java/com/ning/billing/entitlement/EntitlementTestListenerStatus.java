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

package com.ning.billing.entitlement;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.api.TestListenerStatus;

public class EntitlementTestListenerStatus implements TestListenerStatus {

    private final Logger log = LoggerFactory.getLogger(EntitlementTestListenerStatus.class);

    private boolean isListenerFailed;
    private String listenerFailedMsg;


    @Inject
    public EntitlementTestListenerStatus() {
        isListenerFailed = false;
    }


    @Override
    public void failed(final String msg) {
        this.isListenerFailed = true;
        this.listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        this.isListenerFailed = false;
        this.listenerFailedMsg = null;
    }

    public void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }
}
