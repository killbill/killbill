/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.tag.dao;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.tag.ControlTagType;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class SystemTags {

    // Invoice
    public static final UUID PARK_TAG_DEFINITION_ID = new UUID(1, 1);
    public static final String PARK_TAG_DEFINITION_NAME = "__PARK__";

    // Note! TagSqlDao.sql.stg needs to be kept in sync (see userAndSystemTagDefinitions)
    private static final List<TagDefinitionModelDao> SYSTEM_DEFINED_TAG_DEFINITIONS = ImmutableList.<TagDefinitionModelDao>of(new TagDefinitionModelDao(PARK_TAG_DEFINITION_ID, null, null, PARK_TAG_DEFINITION_NAME, "Accounts with invalid invoicing state", ObjectType.ACCOUNT.name()));

    public static Collection<TagDefinitionModelDao> get(final boolean includeSystemTags) {
        final Collection<TagDefinitionModelDao> all = includeSystemTags ?
                                                      new LinkedList<TagDefinitionModelDao>(SYSTEM_DEFINED_TAG_DEFINITIONS) :
                                                      new LinkedList<TagDefinitionModelDao>();
        for (final ControlTagType controlTag : ControlTagType.values()) {
            all.add(new TagDefinitionModelDao(controlTag));
        }
        return all;
    }

    public static TagDefinitionModelDao lookup(final String tagDefinitionName) {
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.name().equals(tagDefinitionName)) {
                return new TagDefinitionModelDao(t);
            }
        }

        for (final TagDefinitionModelDao t : SYSTEM_DEFINED_TAG_DEFINITIONS) {
            if (t.getName().equals(tagDefinitionName)) {
                return t;
            }
        }

        return null;
    }

    public static boolean isSystemTag(final UUID tagDefinitionId) {
        return Iterables.any(SYSTEM_DEFINED_TAG_DEFINITIONS, new Predicate<TagDefinitionModelDao>() {
            @Override
            public boolean apply(final TagDefinitionModelDao input) {
                return input.getId().equals(tagDefinitionId);
            }
        });
    }

    public static TagDefinitionModelDao lookup(final UUID tagDefinitionId) {
        for (final ControlTagType t : ControlTagType.values()) {
            if (t.getId().equals(tagDefinitionId)) {
                return new TagDefinitionModelDao(t);
            }
        }

        for (final TagDefinitionModelDao t : SYSTEM_DEFINED_TAG_DEFINITIONS) {
            if (t.getId().equals(tagDefinitionId)) {
                return t;
            }
        }

        return null;
    }
}
