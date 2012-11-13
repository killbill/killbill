/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.entity.dao;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.exceptions.StatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistoryModelDao;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.Entity;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Wraps an instance of EntitySqlDao, performing extra work around each method (Sql query)
 *
 * @param <S> EntitySqlDao type of the wrapped instance
 * @param <M> EntityModel associated with S
 * @param <E> Entity associated with M
 */
public class EntitySqlDaoWrapperInvocationHandler<S extends EntitySqlDao<M, E>, M extends EntityModelDao<E>, E extends Entity> implements InvocationHandler {

    private final Logger logger = LoggerFactory.getLogger(EntitySqlDaoWrapperInvocationHandler.class);

    private final Class<S> sqlDaoClass;
    private final S sqlDao;

    public EntitySqlDaoWrapperInvocationHandler(final Class<S> sqlDaoClass, final S sqlDao) {
        this.sqlDaoClass = sqlDaoClass;
        this.sqlDao = sqlDao;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            return invokeSafely(proxy, method, args);
        } catch (Throwable t) {
            if (t.getCause() != null && t.getCause().getCause() != null && DBIException.class.isAssignableFrom(t.getCause().getClass())) {
                // Likely the JDBC exception or a Billing exception we have thrown in the transaction
                // If it's a JDBC error, try to extract the SQL statement
                if (t.getCause() instanceof StatementException) {
                    final StatementContext statementContext = ((StatementException) t.getCause()).getStatementContext();
                    if (statementContext != null) {
                        final PreparedStatement statement = statementContext.getStatement();
                        if (statement != null) {
                            // Note: we rely on the JDBC driver to have a sane toString() method...
                            errorDuringTransaction(t.getCause().getCause(), method, statement.toString());
                            // Never reached
                            return null;
                        }
                    }
                }
                errorDuringTransaction(t.getCause().getCause(), method);
            } else if (t.getCause() != null) {
                // t is likely not interesting (java.lang.reflect.InvocationTargetException)
                errorDuringTransaction(t.getCause(), method);
            } else {
                errorDuringTransaction(t, method);
            }
        }

        // Never reached
        return null;
    }

    // Nice method name to ease debugging while looking at log files
    private void errorDuringTransaction(final Throwable t, final Method method, final String extraErrorMessage) throws Throwable {
        final StringBuilder errorMessageBuilder = new StringBuilder("Error during transaction for sql entity {} and method {}");
        if (t instanceof SQLException) {
            final SQLException sqlException = (SQLException) t;
            errorMessageBuilder.append(" [SQL State: ")
                               .append(sqlException.getSQLState())
                               .append(", Vendor Error Code: ")
                               .append(sqlException.getErrorCode())
                               .append("]");
        }
        if (extraErrorMessage != null) {
            // This is usually the SQL statement
            errorMessageBuilder.append("\n").append(extraErrorMessage);
        }
        logger.warn(errorMessageBuilder.toString(), sqlDaoClass, method.getName());

        // This is to avoid throwing an exception wrapped in an UndeclaredThrowableException
        if (!(t instanceof RuntimeException)) {
            throw new RuntimeException(t);
        } else {
            throw t;
        }
    }

    private void errorDuringTransaction(final Throwable t, final Method method) throws Throwable {
        errorDuringTransaction(t, method, null);
    }

    private Object invokeSafely(final Object proxy, final Method method, final Object[] args) throws Throwable {
        final Audited annotation = method.getAnnotation(Audited.class);

        InternalCallContext context = null;
        List<String> entityIds = null;
        final Map<String, M> entities = new HashMap<String, M>();
        final Map<String, Long> entityRecordIds = new HashMap<String, Long>();
        if (annotation != null) {
            // There will be some work required after the statement is executed,
            // get the id before in case the change is a delete
            context = retrieveContextFromArguments(args);
            entityIds = retrieveEntityIdsFromArguments(method, args);
            for (final String entityId : entityIds) {
                entities.put(entityId, sqlDao.getById(entityId, context));
                entityRecordIds.put(entityId, sqlDao.getRecordId(entityId, context));
            }
        }

        // Real jdbc call
        final Object obj = method.invoke(sqlDao, args);

        // Update audit and history if needed
        if (annotation != null) {
            final ChangeType changeType = annotation.value();

            for (final String entityId : entityIds) {
                updateHistoryAndAudit(entityId, entities, entityRecordIds, changeType, context);
            }
        }

        return obj;
    }

    private void updateHistoryAndAudit(final String entityId, final Map<String, M> entities, final Map<String, Long> entityRecordIds,
                                       final ChangeType changeType, final InternalCallContext context) {
        // Make sure to re-hydrate the object (especially needed for create calls)
        final M reHydratedEntity = sqlDao.getById(entityId, context);
        final Long reHydratedEntityRecordId = sqlDao.getRecordId(entityId, context);
        final M entity = Objects.firstNonNull(reHydratedEntity, entities.get(entityId));
        final Long entityRecordId = Objects.firstNonNull(reHydratedEntityRecordId, entityRecordIds.get(entityId));
        final TableName tableName = entity.getTableName();

        // Note: audit entries point to the history record id
        final Long historyRecordId;
        if (tableName.getHistoryTableName() != null) {
            historyRecordId = insertHistory(entityRecordId, entity, changeType, context);
        } else {
            historyRecordId = entityRecordId;
        }

        insertAudits(tableName, historyRecordId, changeType, context);
    }

    private List<String> retrieveEntityIdsFromArguments(final Method method, final Object[] args) {

        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        int i = -1;
        for (final Object arg : args) {
            i++;

            // Assume the first argument of type Entity is our type of Entity (type U here)
            // This is true for e.g. create calls
            if (arg instanceof Entity) {
                return ImmutableList.<String>of(((Entity) arg).getId().toString());
            }

            // For Batch calls, the first argument will be of type List<Entity>
            if (arg instanceof Iterable) {
                final Builder<String> entityIds = extractEntityIdsFromBatchArgument((Iterable) arg);
                if (entityIds != null) {
                    return entityIds.build();
                }
            }

            // Otherwise, use the first String argument, annotated with @Bind("id")
            // This is true for e.g. update calls
            if (!(arg instanceof String)) {
                continue;
            }

            for (final Annotation annotation : parameterAnnotations[i]) {
                if (Bind.class.equals(annotation.annotationType()) && ("id").equals(((Bind) annotation).value())) {
                    return ImmutableList.<String>of((String) arg);
                }
            }
        }

        return null;
    }

    private Builder<String> extractEntityIdsFromBatchArgument(final Iterable arg) {
        final Iterator iterator = arg.iterator();
        final Builder<String> entityIds = new Builder<String>();
        while (iterator.hasNext()) {
            final Object object = iterator.next();
            if (!(object instanceof Entity)) {
                // No good - ignore
                return null;
            } else {
                entityIds.add(((Entity) object).getId().toString());
            }
        }

        return entityIds;
    }


    private InternalCallContext retrieveContextFromArguments(final Object[] args) {
        for (final Object arg : args) {
            if (!(arg instanceof InternalCallContext)) {
                continue;
            }
            return (InternalCallContext) arg;
        }
        return null;
    }

    private Long insertHistory(final Long entityRecordId, final M entityModelDao, final ChangeType changeType, final InternalCallContext context) {
        // TODO use clock
        final EntityHistoryModelDao<M, E> history = new EntityHistoryModelDao<M, E>(entityModelDao, entityRecordId, changeType, context.getCreatedDate());
        sqlDao.addHistoryFromTransaction(history, context);
        return sqlDao.getHistoryRecordId(entityRecordId, context);
    }

    private void insertAudits(final TableName tableName, final Long historyRecordId, final ChangeType changeType, final InternalCallContext context) {
        // TODO use clock
        final TableName destinationTableName = Objects.firstNonNull(tableName.getHistoryTableName(), tableName);
        final EntityAudit audit = new EntityAudit(destinationTableName, historyRecordId, changeType, context.getCreatedDate());
        sqlDao.insertAuditFromTransaction(audit, context);
    }
}
