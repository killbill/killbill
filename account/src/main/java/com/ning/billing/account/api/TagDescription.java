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

import org.joda.time.DateTime;

import java.util.UUID;

public class TagDescription extends EntityBase {
    private String name;
    private String addedBy;
    private DateTime created;
    private String description;
    private boolean generateInvoice;
    private boolean processPayment;

    public TagDescription() {
        super();
    }

    public TagDescription(UUID id) {
        super(id);
    }
    
    public String getName() {
        return name;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public DateTime getCreated() {
        return created;
    }

    public String getDescription() {
        return description;
    }

    public boolean getGenerateInvoice() {
        return generateInvoice;
    }

    public boolean getProcessPayment() {
        return processPayment;
    }
}
