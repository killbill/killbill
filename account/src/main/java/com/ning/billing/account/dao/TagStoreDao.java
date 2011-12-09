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

package com.ning.billing.account.dao;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import com.ning.billing.account.api.DefaultTag;
import com.ning.billing.account.api.DefaultTagDescription;
import com.ning.billing.account.api.Tag;
import com.ning.billing.account.api.TagDescription;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagStoreDao.TagMapper.class)
public interface TagStoreDao extends EntityCollectionDao<Tag> {
    @Override
    @SqlBatch
    public void save(@Bind("objectId") final String objectId,
                     @Bind("objectType") final String objectType,
                     @TagBinder final List<Tag> entities);

    public class TagMapper implements ResultSetMapper<Tag> {
        @Override
        public Tag map(int index, ResultSet result, StatementContext context) throws SQLException {
            UUID tagDescriptionId = UUID.fromString(result.getString("tag_description_id"));
            String name = result.getString("tag_description_name");
            String description = result.getString("tag_description");
            boolean processPayment = result.getBoolean("process_payment");
            boolean generateInvoice = result.getBoolean("generate_invoice");
            String createdBy = result.getString("created_by");
            DateTime creationDate = new DateTime(result.getDate("creation_date"));
            TagDescription tagDescription = new DefaultTagDescription(tagDescriptionId, name, description, processPayment, generateInvoice, createdBy, creationDate);

            UUID id = UUID.fromString(result.getString("id"));
            String addedBy = result.getString("added_by");
            DateTime dateAdded = new DateTime(result.getTimestamp("date_added"));
            return new DefaultTag(id, tagDescription, addedBy, dateAdded);
        }
    }

    @BindingAnnotation(TagBinder.TagBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface TagBinder {
        public static class TagBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<TagBinder, Tag>() {
                    public void bind(SQLStatement q, TagBinder bind, Tag tag) {
                        q.bind("id", tag.getId().toString());
                        q.bind("tagDescriptionId", tag.getTagDescriptionId().toString());
                        q.bind("dateAdded", tag.getDateAdded().toDate());
                        q.bind("addedBy", tag.getAddedBy());
                    }
                };
            }
        }
    }
}