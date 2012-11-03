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

package com.ning.billing.account.api;

import com.ning.billing.junction.api.Blockable;
import com.ning.billing.util.entity.Entity;

/**
 * The interface <code>Account</code> represents an account within Killbill.
 * <p>
 * An <code>Account</code> has a unique UUID and also an externalKey that it set when it is created.
 * The billCycleDay can be specified when creating the account, or it will be set automatically
 * by the system.
 *
 * @see com.ning.billing.account.api.AccountData
 */

public interface Account extends AccountData, Entity, Blockable {

    /**
     *
     * @return the mutable account data
     */
    public MutableAccountData toMutableAccountData();

    /**
     * The current account object will have its fields updated with those of the deleted account.
     * <p>
     * Some fields cannot be updated when they already have a value:
     *
     * @param delegate the input account used to update the fields
     * @return         the new account
     */
    public Account mergeWithDelegate(final Account delegate);
}
