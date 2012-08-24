/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.beatrix.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.entitlement.api.user.EntitlementUserApi;

import com.google.inject.Inject;

public class PaymentChecker {

    private static final Logger log = LoggerFactory.getLogger(PaymentChecker.class);

    //private final EntitlementUserApi entitlementApi;

    @Inject
    public PaymentChecker(final EntitlementUserApi entitlementApi) {
        //this.entitlementApi = entitlementApi;
    }

}
