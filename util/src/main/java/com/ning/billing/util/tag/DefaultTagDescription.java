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

package com.ning.billing.util.tag;

import java.util.UUID;
import org.joda.time.DateTime;
import com.ning.billing.util.entity.EntityBase;

public class DefaultTagDescription extends EntityBase implements TagDescription {
    private String name;
    private String description;
    private boolean generateInvoice;
    private boolean processPayment;
    private String createdBy;
    private DateTime creationDate;

    public DefaultTagDescription(String name, String description,
                                 boolean processPayment, boolean generateInvoice,
                                 String createdBy, DateTime creationDate) {
        this(UUID.randomUUID(), name, description, processPayment, generateInvoice, createdBy, creationDate);
    }

    public DefaultTagDescription(UUID id, String name, String description,
                                 boolean processPayment, boolean generateInvoice,
                                 String createdBy, DateTime creationDate) {
        super(id);
        this.name = name;
        this.description = description;
        this.processPayment = processPayment;
        this.generateInvoice = generateInvoice;
        this.createdBy = createdBy;
        this.creationDate = creationDate;
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public DateTime getCreationDate() {
        return creationDate;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean getGenerateInvoice() {
        return generateInvoice;
    }

    @Override
    public boolean getProcessPayment() {
        return processPayment;
    }
}
