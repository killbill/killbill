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

import com.ning.billing.account.dao.ITagDescriptionDao;
import org.joda.time.DateTime;

import java.util.UUID;

public class TagDescription extends EntityBase {
    private ITagDescriptionDao dao;

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

    public TagDescription withName(String name) {
        this.name = name;
        return this;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public TagDescription withAddedBy(String addedBy) {
        this.addedBy = addedBy;
        return this;
    }

    public DateTime getCreated() {
        return created;
    }

    public TagDescription withCreated(DateTime created) {
        this.created = created;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public TagDescription withDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean getGenerateInvoice() {
        return generateInvoice;
    }

    public TagDescription withGenerateInvoice(boolean generateInvoice) {
        this.generateInvoice = generateInvoice;
        return this;
    }

    public boolean getProcessPayment() {
        return processPayment;
    }

    public TagDescription withProcessPayment(boolean processPayment) {
        this.processPayment = processPayment;
        return this;
    }

    @Override
    protected void saveObject() {
        dao.create(this);
    }

    @Override
    protected void updateObject() {
        dao.update(this);
    }

    @Override
    public void load() {
        TagDescription that = dao.load(id.toString());
        this.name = that.name;
        this.addedBy = that.addedBy;
        this.created = that.created;
        this.description = that.description;
        this.generateInvoice = that.generateInvoice;
        this.processPayment = that.processPayment;
    }
}
