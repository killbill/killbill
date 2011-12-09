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
import org.joda.time.DateTime;

public class DefaultTag extends EntityBase implements Tag {
    private final UUID tagDescriptionId;
    private final boolean processPayment;
    private final boolean generateInvoice;
    private final String addedBy;
    private final DateTime dateAdded;
    private final String name;

    public DefaultTag(UUID id, UUID tagDescriptionId, String name, boolean processPayment, boolean generateInvoice,
                      String addedBy, DateTime dateAdded) {
        super(id);
        this.tagDescriptionId = tagDescriptionId;
        this.name = name;
        this.processPayment = processPayment;
        this.generateInvoice = generateInvoice;
        this.addedBy = addedBy;
        this.dateAdded = dateAdded;
    }

    public DefaultTag(UUID id, TagDescription tagDescription, String addedBy, DateTime dateAdded) {
        this(id, tagDescription.getId(), tagDescription.getName(), tagDescription.getProcessPayment(),
                tagDescription.getGenerateInvoice(), addedBy, dateAdded);
    }

    public DefaultTag(TagDescription tagDescription, String addedBy, DateTime dateAdded) {
        this(UUID.randomUUID(), tagDescription.getId(), tagDescription.getName(), tagDescription.getProcessPayment(),
                tagDescription.getGenerateInvoice(), addedBy, dateAdded);
    }

    @Override
    public UUID getTagDescriptionId() {
        return tagDescriptionId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean getProcessPayment() {
        return processPayment;
    }

    @Override
    public boolean getGenerateInvoice() {
        return generateInvoice;
    }

    @Override
    public String getAddedBy() {
        return addedBy;
    }

    @Override
    public DateTime getDateAdded() {
        return dateAdded;
    }
}
