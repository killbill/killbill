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
import com.ning.billing.util.entity.EntityCollectionBase;

public class DefaultTagStore extends EntityCollectionBase<Tag> implements TagStore {
    public DefaultTagStore(UUID objectId, String objectType) {
        super(objectId, objectType);
    }

    @Override
    public String getEntityKey(Tag entity) {
        return entity.getName();
    }

    @Override
    /***
     * Collates the contents of the TagStore to determine if payments should be processed
     * @return true is no tags contraindicate payment processing
     */
    public boolean processPayment() {
        for (Tag tag : entities.values()) {
            if (!tag.getProcessPayment()) {
                return false;
            }
        }
        return true;
    }

    /***
     * Collates the contents of the TagStore to determine if invoices should be generated
     * @return true is no tags contraindicate invoice generation
     */
    @Override
    public boolean generateInvoice() {
        for (Tag tag : entities.values()) {
            if (!tag.getGenerateInvoice()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void remove(String tagName) {
        entities.remove(entities.get(tagName));
    }

    @Override
    public boolean containsTag(String tagName) {
        for (Tag tag : entities.values()) {
            if (tag.getName() == tagName) {
                return true;
            }
        }

        return false;
    }
}