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

package com.ning.billing.util.tag.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

public class TagMapper implements ResultSetMapper<Tag> {
    @Override
    public Tag map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
        String name = result.getString("tag_definition_name");

        UUID id = UUID.fromString(result.getString("id"));
        String addedBy = result.getString("added_by");
        DateTime addedDate = new DateTime(result.getTimestamp("added_date"));

        Tag tag;
        try {
            ControlTagType controlTagType = ControlTagType.valueOf(name);
            tag = new DefaultControlTag(id, addedBy, addedDate, controlTagType);
        } catch (Throwable t) {
            String description = result.getString("tag_description");
            String createdBy = result.getString("created_by");

            UUID tagDefinitionId = UUID.fromString(result.getString("tag_definition_id"));
            TagDefinition tagDefinition = new DefaultTagDefinition(tagDefinitionId, name, description, createdBy);
            tag = new DescriptiveTag(id, tagDefinition, addedBy, addedDate);
        }

        return tag;
    }
}
