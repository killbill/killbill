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
import java.util.UUID;
import com.ning.billing.account.api.DefaultTag;
import com.ning.billing.account.api.DefaultTagDescription;
import com.ning.billing.account.api.TagDescription;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagDescriptionDao.TagDescriptionMapper.class)
public interface TagDescriptionDao extends EntityDao<TagDescription> {
    @Override
    @SqlUpdate
    public void save(@TagDescriptionBinder TagDescription entity);

    public class TagDescriptionMapper implements ResultSetMapper<TagDescription> {
        @Override
        public TagDescription map(int index, ResultSet result, StatementContext context) throws SQLException {
            UUID id = UUID.fromString(result.getString("id"));
            String name = result.getString("name");
            String description = result.getString("description");
            boolean processPayment = result.getBoolean("process_payment");
            boolean generateInvoice = result.getBoolean("generate_invoice");
            String createdBy = result.getString("created_by");
            DateTime creationDate = new DateTime(result.getTimestamp("creation_date"));
            return new DefaultTagDescription(id, name, description, processPayment, generateInvoice, createdBy, creationDate);
        }
    }

    @BindingAnnotation(TagDescriptionBinder.TagDescriptionBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface TagDescriptionBinder {
        public static class TagDescriptionBinderFactory implements BinderFactory {
            public Binder build(Annotation annotation) {
                return new Binder<TagDescriptionBinder, TagDescription>() {
                    public void bind(SQLStatement q, TagDescriptionBinder bind, TagDescription tagDescription) {
                        q.bind("id", tagDescription.getId().toString());
                        q.bind("name", tagDescription.getName());
                        q.bind("createdBy", tagDescription.getCreatedBy());
                        q.bind("creationDate", tagDescription.getCreationDate().toDate());
                        q.bind("description", tagDescription.getDescription());
                        q.bind("generateInvoice", tagDescription.getGenerateInvoice());
                        q.bind("processPayment", tagDescription.getProcessPayment());
                    }
                };
            }
        }
    }
}