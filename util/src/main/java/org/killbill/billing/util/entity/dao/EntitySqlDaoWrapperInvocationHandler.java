/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.entity.dao;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.EntityAudit;
import org.killbill.billing.util.dao.EntityHistoryModelDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.clock.Clock;
import org.killbill.commons.profiling.Profiling;
import org.killbill.commons.profiling.Profiling.WithProfilingCallback;
import org.killbill.commons.profiling.ProfilingFeature.ProfilingFeatureType;
import org.skife.jdbi.v2.Binding;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.exceptions.DBIException;
import org.skife.jdbi.v2.exceptions.StatementException;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlBatch;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.unstable.BindIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;

/**
 * Wraps an instance of EntitySqlDao, performing extra work around each method (Sql query)
 *
 * @param <S> EntitySqlDao type of the wrapped instance
 * @param <M> EntityModel associated with S
 * @param <E> Entity associated with M
 */
public class EntitySqlDaoWrapperInvocationHandler<S extends EntitySqlDao<M, E>, M extends EntityModelDao<E>, E extends Entity> implements InvocationHandler {

    private final Logger logger = LoggerFactory.getLogger(EntitySqlDaoWrapperInvocationHandler.class);

    private final Map<String, Annotation[][]> parameterAnnotationsByMethod = new ConcurrentHashMap<String, Annotation[][]>();

    private final Class<S> sqlDaoClass;
    private final S sqlDao;
    private final Handle handle;

    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Profiling<Object, Throwable> prof;

    public EntitySqlDaoWrapperInvocationHandler(final Class<S> sqlDaoClass,
                                                final S sqlDao,
                                                final Handle handle,
                                                final Clock clock,
                                                // Special DAO that don't require caching can invoke EntitySqlDaoWrapperInvocationHandler with no caching (e.g NoCachingTenantDao)
                                                @Nullable final CacheControllerDispatcher cacheControllerDispatcher,
                                                final InternalCallContextFactory internalCallContextFactory) {
        this.sqlDaoClass = sqlDaoClass;
        this.sqlDao = sqlDao;
        this.handle = handle;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.internalCallContextFactory = internalCallContextFactory;
        this.prof = new Profiling<Object, Throwable>();
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            return prof.executeWithProfiling(ProfilingFeatureType.DAO, getProfilingId(null, method), new WithProfilingCallback<Object, Throwable>() {
                @Override
                public Object execute() throws Throwable {
                    return invokeSafely(method, args);
                }
            });
        } catch (final Throwable t) {
            if (t.getCause() != null && t.getCause().getCause() != null && DBIException.class.isAssignableFrom(t.getCause().getClass())) {
                // Likely a JDBC error, try to extract the SQL statement and JDBI bindings
                if (t.getCause() instanceof StatementException) {
                    final StatementContext statementContext = ((StatementException) t.getCause()).getStatementContext();

                    if (statementContext != null) {
                        // Grumble, we need to rely on the suxxor toString() method as nothing is exposed
                        final Binding binding = statementContext.getBinding();

                        final PreparedStatement statement = statementContext.getStatement();
                        if (statement != null) {
                            // Note: we rely on the JDBC driver to have a sane toString() method...
                            errorDuringTransaction(t.getCause().getCause(), method, statement.toString() + "\n" + binding.toString());
                        } else {
                            errorDuringTransaction(t.getCause().getCause(), method, binding.toString());
                        }

                        // Never reached
                        return null;
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
            errorMessageBuilder.append(" [SQL DefaultState: ")
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

    private Object invokeSafely(final Method method, final Object[] args) throws Throwable {
        final Audited auditedAnnotation = method.getAnnotation(Audited.class);

        final boolean isROQuery = method.getAnnotation(SqlQuery.class) != null;
        Preconditions.checkState(auditedAnnotation != null || isROQuery, "Non-@SqlQuery method %s without @Audited annotation", method);

        // This can't be AUDIT'ed and CACHABLE'd at the same time as we only cache 'get'
        if (auditedAnnotation != null) {
            return invokeWithAuditAndHistory(auditedAnnotation, method, args);
        } else {
            return invokeRaw(method, args);
        }
    }

    private Object invokeRaw(final Method method, final Object[] args) throws Throwable {
        return prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, getProfilingId("raw", method), new WithProfilingCallback<Object, Throwable>() {
            @Override
            public Object execute() throws Throwable {
                // Real jdbc call
                final Object result = executeJDBCCall(method, args);
                // This is *almost* the default invocation except that we want to intercept getById calls to populate the caches; the pattern is to always fetch
                // the object after it was created, which means this method is (by pattern) first called right after object creation and contains all the goodies we care
                // about (record_id, account_record_id, object_id, tenant_record_id)
                //
                if (result != null && method.getName().equals("getById")) {
                    populateCacheOnGetByIdInvocation((M) result);
                }
                return result;
            }
        });
    }

    private Object invokeWithAuditAndHistory(final Audited auditedAnnotation, final Method method, final Object[] args) throws Throwable {
        final InternalCallContext contextMaybeWithoutAccountRecordId = retrieveContextFromArguments(args);
        final List<String> entityIds = retrieveEntityIdsFromArguments(method, args);
        Preconditions.checkState(!entityIds.isEmpty(), "@Audited Sql method must have entities (@Bind(\"id\")) as arguments");
        // We cannot always infer the TableName from the signature
        TableName tableName = retrieveTableNameFromArgumentsIfPossible(Arrays.asList(args));
        final ChangeType changeType = auditedAnnotation.value();
        final boolean isBatchQuery = method.getAnnotation(SqlBatch.class) != null;

        // Get the current state before deletion for the history tables
        final Map<Long, M> deletedAndUpdatedEntities = new HashMap<Long, M>();
        // Real jdbc call
        final Object obj = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, getProfilingId("raw", method), new WithProfilingCallback<Object, Throwable>() {
            @Override
            public Object execute() throws Throwable {
                return executeJDBCCall(method, args);
            }
        });

        if (entityIds.isEmpty() ) {
            return obj;
        }

        InternalCallContext context = null;
        // Retrieve record_id(s) for audit and history tables
        final List<Long> entityRecordIds = new LinkedList<Long>();
        if (changeType == ChangeType.INSERT) {
            Preconditions.checkNotNull(tableName, "Insert query should have an EntityModelDao as argument: %s", args);

            if (isBatchQuery) {
                entityRecordIds.addAll((Collection<? extends Long>) obj);
            } else {
                entityRecordIds.add((Long) obj);
            }

            // Snowflake
            if (TableName.ACCOUNT.equals(tableName)) {
                Preconditions.checkState(entityIds.size() == 1, "Bulk insert of accounts isn't supported");
                // AccountModelDao in practice
                final TimeZoneAwareEntity accountModelDao = retrieveTimeZoneAwareEntityFromArguments(args);
                context = internalCallContextFactory.createInternalCallContext(accountModelDao, entityRecordIds.get(0), contextMaybeWithoutAccountRecordId);
            }
        } else {
            // Rehydrate entry with latest state
            final List<M> retrievedEntities = sqlDao.getByIdsIncludedDeleted(entityIds, contextMaybeWithoutAccountRecordId);
            printSQLWarnings();
            for (final M entity : retrievedEntities) {
                deletedAndUpdatedEntities.put(entity.getRecordId(), entity);
                entityRecordIds.add(entity.getRecordId());
                if (tableName == null) {
                    tableName = entity.getTableName();
                } else {
                    Preconditions.checkState(tableName == entity.getTableName(), "Entities with different TableName");
                }
            }
        }
        Preconditions.checkState(entityIds.size() == entityRecordIds.size(), "SqlDao method has %s as ids but found %s as recordIds", entityIds, entityRecordIds);

        // Context validations
        if (context != null) {
            // context was already updated, see above (createAccount code path). Just make sure we don't attempt to bulk create
            Preconditions.checkState(entityIds.size() == 1, "Bulk insert of accounts isn't supported");
        } else {
            context = contextMaybeWithoutAccountRecordId;
            final boolean tableWithoutAccountRecordId = tableName == TableName.TENANT || tableName == TableName.TENANT_BROADCASTS || tableName == TableName.TENANT_KVS || tableName == TableName.TAG_DEFINITIONS || tableName == TableName.SERVICE_BRODCASTS || tableName == TableName.NODE_INFOS;
            Preconditions.checkState(context.getAccountRecordId() != null || tableWithoutAccountRecordId,
                                     "accountRecordId should be set for tableName=%s and changeType=%s", tableName, changeType);
        }

        final Collection<M> reHydratedEntities = updateHistoryAndAudit(entityRecordIds, deletedAndUpdatedEntities, tableName, changeType, context);
        if (method.getReturnType().equals(Void.TYPE)) {
            // Return early
            return null;
        } else if (isBatchQuery) {
            // Return the raw jdbc response (generated keys)
            return obj;
        } else {
            // PERF: override the return value with the reHydrated entity to avoid an extra 'get' in the transaction,
            // (see EntityDaoBase#createAndRefresh for an example, but it works for updates as well).
            Preconditions.checkState(entityRecordIds.size() == 1, "Invalid number of entityRecordIds: %s", entityRecordIds);

            if (!reHydratedEntities.isEmpty()) {
                Preconditions.checkState(reHydratedEntities.size() == 1, "Invalid number of entities: %s", reHydratedEntities);
                return Iterables.<M>getFirst(reHydratedEntities, null);
            } else {
                // Updated entity not retrieved yet, we have to go back to the database
                final M entity = sqlDao.getByRecordId(entityRecordIds.get(0), context);
                printSQLWarnings();
                return entity;
            }
        }
    }

    private Object executeJDBCCall(final Method method, final Object[] args) throws IllegalAccessException, InvocationTargetException {
        final Object invoke = method.invoke(sqlDao, args);
        printSQLWarnings();
        return invoke;
    }

    private void printSQLWarnings() {
        if (logger.isDebugEnabled()) {
            try {
                SQLWarning warning = handle.getConnection().getWarnings();
                while (warning != null) {
                    logger.debug("[SQL WARNING] {}", warning);
                    warning = warning.getNextWarning();
                }
                handle.getConnection().clearWarnings();
            } catch (final SQLException e) {
                logger.debug("Error whilst retrieving SQL warnings", e);
            }
        }
    }

    private void populateCacheOnGetByIdInvocation(final M model) {
        populateCaches(cacheControllerDispatcher, model);
    }

    public static void populateCaches(final CacheControllerDispatcher cacheControllerDispatcher, final EntityModelDao model) {
        final CacheController<String, Long> cacheRecordId = cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID);
        cacheRecordId.putIfAbsent(getKey(model.getId().toString(), CacheType.RECORD_ID, model.getTableName()), model.getRecordId());

        final CacheController<String, UUID> cacheObjectId = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        cacheObjectId.putIfAbsent(getKey(model.getRecordId().toString(), CacheType.OBJECT_ID, model.getTableName()), model.getId());

        if (model.getTenantRecordId() != null) {
            final CacheController<String, Long> cacheTenantRecordId = cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID);
            cacheTenantRecordId.putIfAbsent(getKey(model.getId().toString(), CacheType.TENANT_RECORD_ID, model.getTableName()), model.getTenantRecordId());
        }

        if (model.getAccountRecordId() != null) {
            final CacheController<String, Long> cacheAccountRecordId = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID);
            cacheAccountRecordId.putIfAbsent(getKey(model.getId().toString(), CacheType.ACCOUNT_RECORD_ID, model.getTableName()), model.getAccountRecordId());
        }
    }

    private static String getKey(final String rawKey, final CacheType cacheType, final TableName tableName) {
        return cacheType.isKeyPrefixedWithTableName() ?
               tableName + CacheControllerDispatcher.CACHE_KEY_SEPARATOR + rawKey :
               rawKey;
    }

    // Update history and audit tables.
    // PERF: if the latest entities had to be fetched from the database, return them. Otherwise, return null.
    private Collection<M> updateHistoryAndAudit(final List<Long> entityRecordIds,
                                                final Map<Long, M> deletedAndUpdatedEntities,
                                                final TableName tableName,
                                                final ChangeType changeType,
                                                final InternalCallContext context) throws Throwable {
        // Arbitrary large batch size resulting in not too many round trips but also avoiding
        // too large of a set causing failures -- https://github.com/killbill/killbill/issues/1390
        int MAX_BATCH_SIZE = 10000;

        final Object reHydratedEntitiesOrNull = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, getProfilingId("history/audit", null), new WithProfilingCallback<Object, Throwable>() {

            @Override
            public Collection<M> execute() {
                if (tableName.getHistoryTableName() == null) {
                    insertAudits(entityRecordIds, tableName, changeType, context);
                    return deletedAndUpdatedEntities.values();
                } else {
                    // Make sure to re-hydrate the objects first (especially needed for create calls)
                    final Collection<M> reHydratedEntities = new ArrayList<>(entityRecordIds.size());
                    if (deletedAndUpdatedEntities.isEmpty()) {
                        int nbBatch = entityRecordIds.size() / MAX_BATCH_SIZE;
                        if (entityRecordIds.size() % MAX_BATCH_SIZE != 0) {
                            nbBatch +=1;
                        }
                        for (int i = 0; i < nbBatch; i++) {
                            final int start = i * MAX_BATCH_SIZE;
                            final int end = i == (nbBatch - 1) ? entityRecordIds.size() : start + MAX_BATCH_SIZE;
                            final List<Long> entityBatchRecordIds = entityRecordIds.subList(start, end);
                            reHydratedEntities.addAll(sqlDao.getByRecordIds(entityBatchRecordIds, context));
                            printSQLWarnings();
                        }
                    } else {
                        reHydratedEntities.addAll(deletedAndUpdatedEntities.values());
                    }
                    Preconditions.checkState(reHydratedEntities.size() == entityRecordIds.size(), "Wrong number of reHydratedEntities=%s (entityRecordIds=%s)", reHydratedEntities, entityRecordIds);

                    final Collection<Long> auditTargetRecordIds = insertHistories(reHydratedEntities, changeType, context);
                    // Note: audit entries point to the history record id
                    Preconditions.checkState(auditTargetRecordIds.size() == entityRecordIds.size(), "Wrong number of auditTargetRecordIds=%s (entityRecordIds=%s)", auditTargetRecordIds, entityRecordIds);
                    insertAudits(auditTargetRecordIds, tableName, changeType, context);

                    return reHydratedEntities;
                }
            }
        });
        //noinspection unchecked
        return (Collection<M>) reHydratedEntitiesOrNull;
    }

    private List<String> retrieveEntityIdsFromArguments(final Method method, final Object[] args) {
        final Annotation[][] parameterAnnotations = getAnnotations(method);

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

            for (final Annotation annotation : parameterAnnotations[i]) {
                if (arg instanceof String && Bind.class.equals(annotation.annotationType()) && ("id").equals(((Bind) annotation).value())) {
                    return ImmutableList.<String>of((String) arg);
                } else if (arg instanceof Collection && BindIn.class.equals(annotation.annotationType()) && ("ids").equals(((BindIn) annotation).value())) {
                    return ImmutableList.<String>copyOf((Collection) arg);
                }
            }
        }
        return ImmutableList.<String>of();
    }

    private Annotation[][] getAnnotations(final Method method) {
        // Expensive to compute
        final String methodString = method.toString();

        // Method.getParameterAnnotations() generates lots of garbage objects
        Annotation[][] parameterAnnotations = parameterAnnotationsByMethod.get(methodString);
        if (parameterAnnotations == null) {
            parameterAnnotations = method.getParameterAnnotations();
            parameterAnnotationsByMethod.put(methodString, parameterAnnotations);
        }
        return parameterAnnotations;
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
        throw new IllegalStateException("No InternalCallContext specified in args: " + Arrays.toString(args));
    }

    private TableName retrieveTableNameFromArgumentsIfPossible(final Iterable args) {
        TableName tableName = null;
        for (final Object arg : args) {
            TableName argTableName = null;
            if (arg instanceof EntityModelDao) {
                argTableName = ((EntityModelDao) arg).getTableName();
            } else if (arg instanceof Iterable) {
                argTableName = retrieveTableNameFromArgumentsIfPossible((Iterable) arg);
            }

            if (tableName == null) {
                tableName = argTableName;
            } else if (argTableName != null) {
                Preconditions.checkState(tableName == argTableName, "SqlDao method with different TableName in args: %s", args);
            }
        }
        return tableName;
    }

    private TimeZoneAwareEntity retrieveTimeZoneAwareEntityFromArguments(final Object[] args) {
        for (final Object arg : args) {
            if (!(arg instanceof TimeZoneAwareEntity)) {
                continue;
            }
            return (TimeZoneAwareEntity) arg;
        }
        throw new IllegalStateException("TimeZoneAwareEntity should have been found among " + args);
    }

    private List<Long> insertHistories(final Iterable<M> reHydratedEntityModelDaos, final ChangeType changeType, final InternalCallContext context) {
        final Collection<EntityHistoryModelDao<M, E>> histories = new LinkedList<EntityHistoryModelDao<M, E>>();
        for (final M reHydratedEntityModelDao : reHydratedEntityModelDaos) {
            final EntityHistoryModelDao<M, E> history = new EntityHistoryModelDao<M, E>(reHydratedEntityModelDao, reHydratedEntityModelDao.getRecordId(), changeType, null, context.getCreatedDate());
            histories.add(history);
        }

        final List<Long> recordIds = sqlDao.addHistoriesFromTransaction(histories, context);
        printSQLWarnings();
        return recordIds;
    }

    // Bulk insert all audit logs for this operation
    private void insertAudits(final Iterable<Long> auditTargetRecordIds,
                              final TableName tableName,
                              final ChangeType changeType,
                              final InternalCallContext context) {
        final TableName destinationTableName = MoreObjects.firstNonNull(tableName.getHistoryTableName(), tableName);

        final Collection<EntityAudit> audits = new LinkedList<EntityAudit>();
        for (final Long auditTargetRecordId : auditTargetRecordIds) {
            final EntityAudit audit = new EntityAudit(destinationTableName, auditTargetRecordId, changeType, context.getCreatedDate());
            audits.add(audit);
        }

        sqlDao.insertAuditsFromTransaction(audits, context);
        printSQLWarnings();
    }

    private String getProfilingId(@Nullable final String prefix, @Nullable final Method method) {
        final StringBuilder stringBuilder = new StringBuilder().append(sqlDaoClass.getSimpleName());

        if (prefix != null) {
            stringBuilder.append(" (")
                         .append(prefix)
                         .append(")");
        }

        if (method != null) {
            stringBuilder.append(": ").append(method.getName());
        }

        return stringBuilder.toString();
    }
}
