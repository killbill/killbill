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

public class TagBuilder {
    private UUID id = UUID.randomUUID();
    private UUID tagDescriptionId;
    private String name;
    private boolean processPayment;
    private boolean generateInvoice;
    private String addedBy;
    private DateTime dateAdded;

    public TagBuilder id(UUID id) {
        this.id = id;
        return this;
    }

    public TagBuilder tagDescriptionId(UUID tagDescriptionId) {
        this.tagDescriptionId = tagDescriptionId;
        return this;
    }

    public TagBuilder name(String name) {
        this.name = name;
        return this;
    }

    public TagBuilder processPayment(boolean processPayment) {
        this.processPayment = processPayment;
        return this;
    }

    public TagBuilder generateInvoice(boolean generateInvoice) {
        this.generateInvoice = generateInvoice;
        return this;
    }

    public TagBuilder addedBy(String addedBy) {
        this.addedBy = addedBy;
        return this;
    }

    public TagBuilder dateAdded(DateTime dateAdded) {
        this.dateAdded = dateAdded;
        return this;
    }

    public Tag build() {
        return new DefaultTag(id, tagDescriptionId, name, processPayment, generateInvoice, addedBy, dateAdded);
    }
}
