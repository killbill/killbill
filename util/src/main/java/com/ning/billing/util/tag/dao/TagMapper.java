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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.entity.MapperBase;
import com.ning.billing.util.tag.ControlTag;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;

public class TagMapper extends MapperBase implements ResultSetMapper<Tag> {
    @Override
    public Tag map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
        String name = result.getString("tag_definition_name");

        ControlTagType thisTagType = null;
        for (ControlTagType controlTagType : ControlTagType.values()) {
            if (name.equals(controlTagType.toString())) {
                thisTagType = controlTagType;
            }
        }

        if (thisTagType == null) {
            UUID id = UUID.fromString(result.getString("id"));
            String createdBy = result.getString("created_by");
            DateTime createdDate = new DateTime(result.getTimestamp("created_date"));

            return new DescriptiveTag(id, createdBy, createdDate, name);
        } else {
            return new DefaultControlTag(thisTagType);
        }
    }
}
