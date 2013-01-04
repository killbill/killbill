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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ning.billing.ObjectType;

import com.google.common.collect.ImmutableList;

public enum ControlTagType {

    AUTO_PAY_OFF(new UUID(0, 1), "Suspends payments until removed.", true, false, ImmutableList.<ObjectType>of(ObjectType.ACCOUNT)),
    AUTO_INVOICING_OFF(new UUID(0, 2), "Suspends invoicing until removed.", false, true, ImmutableList.<ObjectType>of(ObjectType.ACCOUNT)),
    OVERDUE_ENFORCEMENT_OFF(new UUID(0, 3), "Suspends overdue enforcement behaviour until removed.", false, false, ImmutableList.<ObjectType>of(ObjectType.ACCOUNT)),
    WRITTEN_OFF(new UUID(0, 4), "Indicates that an invoice is written off. No billing or payment effect.", false, false, ImmutableList.<ObjectType>of(ObjectType.INVOICE)),
    MANUAL_PAY(new UUID(0, 5), "Indicates that Killbill doesn't process payments for that account (external payments only)", true, false, ImmutableList.<ObjectType>of(ObjectType.ACCOUNT));

    private final UUID id;
    private final String description;
    private final boolean autoPaymentOff;
    private final boolean autoInvoicingOff;
    private final List<ObjectType> applicableObjectTypes;

    ControlTagType(final UUID id, final String description, final boolean autoPaymentOff, final boolean autoInvoicingOff,
                   final List<ObjectType> applicableObjectTypes) {
        this.id = id;
        this.description = description;
        this.autoPaymentOff = autoPaymentOff;
        this.autoInvoicingOff = autoInvoicingOff;
        this.applicableObjectTypes = applicableObjectTypes;
    }

    public UUID getId() {
        return id;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean getAutoPaymentOff() {
        return this.autoPaymentOff;
    }

    public boolean getAutoInvoicingOff() {
        return this.autoInvoicingOff;
    }

    public List<ObjectType> getApplicableObjectTypes() {
        return applicableObjectTypes;
    }

    public static ControlTagType getTypeFromId(final UUID targetId) {
        for (ControlTagType cur : values()) {
            if (cur.getId().equals(targetId)) {
                return cur;
            }
        }
        return null;
    }

    /**
     * Convenience method to return whether an account is configured with auto_pay_off
     * (information of which control tag is configured with which behavior stays here)
     *
     * @param input tagDefinitionIds for all the tags associated to a given account
     * @return whether that account is set with auto_pay_off
     */
    public static boolean isAutoPayOff(final Collection<UUID> input) {
        for (UUID cur : input) {
           for (ControlTagType controlTag : values()) {
               if (controlTag.getId().equals(cur) && controlTag.getAutoPaymentOff()) {
                   return true;
               }
           }
        }
        return false;
    }

    /**
     * Convenience method to return whether an account is configured with auto_invoicing_off
     * (information of which control tag is configured with which behavior stays here)
     *
     * @param input tagDefinitionIds for all the tags associated to a given account
     * @return whether that account is set with auto_invoicing_off
     */
    public static boolean isAutoInvoicingOff(final Collection<UUID> input) {
        for (UUID cur : input) {
            for (ControlTagType controlTag : values()) {
                if (controlTag.getId().equals(cur) && controlTag.getAutoInvoicingOff()) {
                    return true;
                }
            }
        }
        return false;
    }
}
