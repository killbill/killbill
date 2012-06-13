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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.skife.jdbi.v2.sqlobject.stringtemplate.ExternalizedSqlViaStringTemplate3;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextBinder;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.TagDefinition;

@ExternalizedSqlViaStringTemplate3
@RegisterMapper(TagDefinitionSqlDao.TagDefinitionMapper.class)
public interface TagDefinitionSqlDao extends EntitySqlDao<TagDefinition>, Transactional<TagDefinitionSqlDao>, Transmogrifier {
    @Override
    @SqlUpdate
    public void create(@TagDefinitionBinder final TagDefinition entity, @CallContextBinder final CallContext context);

    @SqlUpdate
    public void deleteAllTagsForDefinition(@Bind("name") final String definitionName, @CallContextBinder final CallContext context);

    @SqlUpdate
    public void deleteTagDefinition(@Bind("name") final String definitionName, @CallContextBinder final CallContext context);

    @SqlQuery
    public int tagDefinitionUsageCount(@Bind("name") final String definitionName);

    @SqlQuery
    public TagDefinition getByName(@Bind("name") final String definitionName);

    public class TagDefinitionMapper implements ResultSetMapper<TagDefinition> {
        @Override
        public TagDefinition map(final int index, final ResultSet result, final StatementContext context) throws SQLException {
            final UUID id = UUID.fromString(result.getString("id"));
            final String name = result.getString("name");
            final String description = result.getString("description");
            return new DefaultTagDefinition(id, name, description, false);
        }
    }

    @BindingAnnotation(TagDefinitionBinder.TagDefinitionBinderFactory.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    public @interface TagDefinitionBinder {
        public static class TagDefinitionBinderFactory implements BinderFactory {
            public Binder build(final Annotation annotation) {
                return new Binder<TagDefinitionBinder, TagDefinition>() {
                    public void bind(final SQLStatement q, final TagDefinitionBinder bind, final TagDefinition tagDefinition) {
                        q.bind("id", tagDefinition.getId().toString());
                        q.bind("name", tagDefinition.getName());
                        q.bind("description", tagDefinition.getDescription());
                    }
                };
            }
        }
    }
}
