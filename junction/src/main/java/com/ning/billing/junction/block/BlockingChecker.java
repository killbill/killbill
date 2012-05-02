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

package com.ning.billing.junction.block;

import java.util.UUID;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApiException;

public interface BlockingChecker {

    public void checkBlockedChange(Blockable blockable)  throws BlockingApiException;

    public void checkBlockedEntitlement(Blockable blockable)  throws BlockingApiException;

    public void checkBlockedBilling(Blockable blockable)  throws BlockingApiException;

    public void checkBlockedChange(UUID bundleId, Blockable.Type type) throws BlockingApiException;

    public void checkBlockedEntitlement(UUID bundleId, Blockable.Type type) throws BlockingApiException;

    public void checkBlockedBilling(UUID bundleId, Blockable.Type type) throws BlockingApiException;
}
