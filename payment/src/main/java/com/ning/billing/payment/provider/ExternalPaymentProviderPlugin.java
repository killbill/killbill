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

package com.ning.billing.payment.provider;

import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

/**
 * Special plugin used to record external payments (i.e. payments not issued by Killbill), such as checks.
 * <p/>
 * The implementation is very similar to the no-op plugin, which it extends. This can potentially be an issue
 * if Killbill is processing a lot of external payments as they are all kept in memory.
 * TODO: do something about it
 */
public class ExternalPaymentProviderPlugin extends DefaultNoOpPaymentProviderPlugin {

    public static final String PLUGIN_NAME = "__EXTERNAL_PAYMENT__";

    @Inject
    public ExternalPaymentProviderPlugin(final Clock clock) {
        super(clock);
    }
}
