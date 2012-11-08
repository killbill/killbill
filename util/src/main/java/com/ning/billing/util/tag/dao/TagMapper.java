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

import com.ning.billing.ObjectType;
import com.ning.billing.util.dao.MapperBase;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TagMapper extends MapperBase implements ResultSetMapper<Tag> {

    @Override
    public Tag map(final int index, final ResultSet result, final StatementContext context) throws SQLException {

        final UUID tagDefinitionId = getUUID(result, "tag_definition_id");
        ControlTagType thisTagType = null;
        for (final ControlTagType controlTagType : ControlTagType.values()) {
            if (tagDefinitionId.equals(controlTagType.getId())) {
                thisTagType = controlTagType;
                break;
            }
        }

        final ObjectType objectType = ObjectType.valueOf(result.getString("object_type"));
        final UUID objectId = getUUID(result, "object_id");
        final UUID id = getUUID(result, "id");
        final DateTime createdDate = getDateTime(result, "created_date");
        if (thisTagType == null) {
            return new DescriptiveTag(id, tagDefinitionId, objectType, objectId, createdDate);
        } else {
            return new DefaultControlTag(id, thisTagType, objectType, objectId, createdDate);
        }
    }
}
