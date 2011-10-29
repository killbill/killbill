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

import java.util.UUID;

import com.ning.billing.catalog.api.Currency;

public class Account implements IAccount {

    private final UUID id;
    private final String key;

    public Account(String key) {
        this(UUID.randomUUID(), key);
    }


    public Account(UUID id, String key) {
        super();
        this.id = id;
        this.key = key;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getEmail() {
        return null;
    }

    @Override
    public String getPhone() {
        return null;
    }


    @Override
    public int getBillCycleDay() {
        return 0;
    }

    @Override
    public Currency getCurrency() {
        return null;
    }

    @Override
    public void setPrivate(String name, String value) {
    }

    @Override
    public String getPrivate(String name) {
        return null;
    }
}
