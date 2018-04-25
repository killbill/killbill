/*
 * Copyright 2010-2012 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.cache.Cachable;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CachableKey;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
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
import org.skife.jdbi.v2.unstable.BindIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
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
                    return invokeSafely(proxy, method, args);
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

    private Object invokeSafely(final Object proxy, final Method method, final Object[] args) throws Throwable {

        final Audited auditedAnnotation = method.getAnnotation(Audited.class);
        final Cachable cachableAnnotation = method.getAnnotation(Cachable.class);

        // This can't be AUDIT'ed and CACHABLE'd at the same time as we only cache 'get'
        if (auditedAnnotation != null) {
            return invokeWithAuditAndHistory(auditedAnnotation, method, args);
        } else if (cachableAnnotation != null && cacheControllerDispatcher != null) {
            return invokeWithCaching(cachableAnnotation, method, args);
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

    private Object invokeWithCaching(final Cachable cachableAnnotation, final Method method, final Object[] args)
            throws Throwable {
        final ObjectType objectType = getObjectType();
        final CacheType cacheType = cachableAnnotation.value();
        final CacheController<Object, Object> cache = cacheControllerDispatcher.getCacheController(cacheType);
        // TODO Change NonEntityDao to take in TableName instead to cache things like TenantBroadcastModelDao (no ObjectType)
        if (cache != null && objectType != null) {
            // Find all arguments marked with @CachableKey
            final Map<Integer, Object> keyPieces = new LinkedHashMap<Integer, Object>();
            final Annotation[][] annotations = getAnnotations(method);
            for (int i = 0; i < annotations.length; i++) {
                for (int j = 0; j < annotations[i].length; j++) {
                    final Annotation annotation = annotations[i][j];
                    if (CachableKey.class.equals(annotation.annotationType())) {
                        // CachableKey position starts at 1
                        keyPieces.put(((CachableKey) annotation).value() - 1, args[i]);
                        break;
                    }
                }
            }

            // Build the Cache key
            final String cacheKey = buildCacheKey(keyPieces);

            final InternalTenantContext internalTenantContext = (InternalTenantContext) Iterables.find(ImmutableList.copyOf(args), new Predicate<Object>() {
                @Override
                public boolean apply(final Object input) {
                    return input instanceof InternalTenantContext;
                }
            }, null);
            final CacheLoaderArgument cacheLoaderArgument = new CacheLoaderArgument(objectType, args, internalTenantContext, handle);
            return cache.get(cacheKey, cacheLoaderArgument);
        } else {
            return invokeRaw(method, args);
        }
    }

    /**
     * Extract object from sqlDaoClass by looking at first parameter type (EntityModelDao) and
     * constructing an empty object so we can call the getObjectType method on it.
     *
     * @return the objectType associated to that handler
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private ObjectType getObjectType() throws InstantiationException, IllegalAccessException, ClassNotFoundException {

        int foundIndexForEntitySqlDao = -1;
        // If the sqlDaoClass implements multiple interfaces, first figure out which one is the EntitySqlDao
        for (int i = 0; i < sqlDaoClass.getGenericInterfaces().length; i++) {
            final Type type = sqlDaoClass.getGenericInterfaces()[0];
            if (!(type instanceof java.lang.reflect.ParameterizedType)) {
                // AuditSqlDao for example won't extend EntitySqlDao
                return null;
            }

            if (EntitySqlDao.class.getName().equals(((Class) ((java.lang.reflect.ParameterizedType) type).getRawType()).getName())) {
                foundIndexForEntitySqlDao = i;
                break;
            }
        }
        // Find out from the parameters of the EntitySqlDao which one is the EntityModelDao, and extract his (sub)type to finally return the ObjectType
        if (foundIndexForEntitySqlDao >= 0) {
            final Type[] types = ((java.lang.reflect.ParameterizedType) sqlDaoClass.getGenericInterfaces()[foundIndexForEntitySqlDao]).getActualTypeArguments();
            int foundIndexForEntityModelDao = -1;
            for (int i = 0; i < types.length; i++) {
                final Class clz = ((Class) types[i]);
                final Type[] genericInterfaces = clz.getGenericInterfaces();
                for (final Type genericInterface : genericInterfaces) {
                    if (genericInterface instanceof ParameterizedType) {
                        if (EntityModelDao.class.getName().equals(((Class) ((ParameterizedType) genericInterface).getRawType()).getName())) {
                            foundIndexForEntityModelDao = i;
                            break;
                        }
                    }
                }
            }

            if (foundIndexForEntityModelDao >= 0) {
                final String modelClassName = ((Class) types[foundIndexForEntityModelDao]).getName();

                final Class<? extends EntityModelDao<?>> clz = (Class<? extends EntityModelDao<?>>) Class.forName(modelClassName);

                final EntityModelDao<?> modelDao = (EntityModelDao<?>) clz.newInstance();
                return modelDao.getTableName().getObjectType();
            }
        }
        return null;
    }

    private Object invokeWithAuditAndHistory(final Audited auditedAnnotation, final Method method, final Object[] args) throws Throwable {
        final InternalCallContext context = retrieveContextFromArguments(args);
        final List<String> entityIds = retrieveEntityIdsFromArguments(method, args);

        final ChangeType changeType = auditedAnnotation.value();

        // Get the current state before deletion for the history tables
        final Map<String, M> deletedEntities = new HashMap<String, M>();
        // Unfortunately, we cannot just look at DELETE as "markAsInactive" operations are often treated as UPDATE
        if (changeType == ChangeType.UPDATE || changeType == ChangeType.DELETE) {
            for (final String entityId : entityIds) {
                deletedEntities.put(entityId, sqlDao.getById(entityId, context));
                printSQLWarnings();
            }
        }

        // Real jdbc call
        final Object obj = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, getProfilingId("raw", method), new WithProfilingCallback<Object, Throwable>() {
            @Override
            public Object execute() throws Throwable {
                return executeJDBCCall(method, args);
            }
        });

        M m = null;
        for (final String entityId : entityIds) {
            m = updateHistoryAndAudit(entityId, deletedEntities.get(entityId), changeType, context);
        }

        // PERF: override the return value with the reHydrated entity to avoid an extra 'get' in the transaction,
        // (see EntityDaoBase#createAndRefresh for an example, but it works for updates as well).
        if (entityIds.size() == 1) {
            return m;
        } else {
            // jDBI will return the number of rows modified otherwise
            return obj;
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

    private M updateHistoryAndAudit(final String entityId, @Nullable final M deletedEntity, final ChangeType changeType, final InternalCallContext context) throws Throwable {
        final Object reHydratedEntity = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, getProfilingId("history/audit", null), new WithProfilingCallback<Object, Throwable>() {
            @Override
            public M execute() throws Throwable {
                final M reHydratedEntity;
                if (changeType == ChangeType.DELETE) {
                    reHydratedEntity = deletedEntity;
                } else {
                    // See note above regarding "markAsInactive" operations
                    reHydratedEntity = MoreObjects.firstNonNull(sqlDao.getById(entityId, context), deletedEntity);
                    printSQLWarnings();
                }
                Preconditions.checkNotNull(reHydratedEntity, "reHydratedEntity cannot be null");
                final Long entityRecordId = reHydratedEntity.getRecordId();
                final TableName tableName = reHydratedEntity.getTableName();

                // Note: audit entries point to the history record id
                final Long historyRecordId;
                if (tableName.getHistoryTableName() != null) {
                    historyRecordId = insertHistory(entityRecordId, reHydratedEntity, changeType, context);
                } else {
                    historyRecordId = entityRecordId;
                }

                // Make sure to re-hydrate the object (especially needed for create calls)
                insertAudits(tableName, reHydratedEntity, entityRecordId, historyRecordId, changeType, context);
                return reHydratedEntity;
            }
        });
        //noinspection unchecked
        return (M) reHydratedEntity;
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
        return null;
    }

    private Long insertHistory(final Long entityRecordId, final M entityModelDao, final ChangeType changeType, final InternalCallContext context) {
        final EntityHistoryModelDao<M, E> history = new EntityHistoryModelDao<M, E>(entityModelDao, entityRecordId, changeType, null, context.getCreatedDate());
        final Long recordId = sqlDao.addHistoryFromTransaction(history, context);
        printSQLWarnings();
        return recordId;
    }

    private void insertAudits(final TableName tableName, final M entityModelDao, final Long entityRecordId, final Long historyRecordId, final ChangeType changeType, final InternalCallContext contextMaybeWithoutAccountRecordId) {
        final TableName destinationTableName = MoreObjects.firstNonNull(tableName.getHistoryTableName(), tableName);
        final EntityAudit audit = new EntityAudit(destinationTableName, historyRecordId, changeType, contextMaybeWithoutAccountRecordId.getCreatedDate());

        final InternalCallContext context;
        // Populate the account record id when creating the account record
        if (TableName.ACCOUNT.equals(tableName) && ChangeType.INSERT.equals(changeType)) {
            // AccountModelDao in practice
            final TimeZoneAwareEntity accountModelDao = (TimeZoneAwareEntity) entityModelDao;
            context = internalCallContextFactory.createInternalCallContext(accountModelDao, entityRecordId, contextMaybeWithoutAccountRecordId);
        } else {
            context = contextMaybeWithoutAccountRecordId;
        }
        sqlDao.insertAuditFromTransaction(audit, context);
        printSQLWarnings();

        // We need to invalidate the caches. There is a small window of doom here where caches will be stale.
        // TODO Knowledge on how the key is constructed is also in AuditSqlDao
        if (tableName.getHistoryTableName() != null) {
            final CacheController<String, List> cacheController = cacheControllerDispatcher.getCacheController(CacheType.AUDIT_LOG_VIA_HISTORY);
            if (cacheController != null) {
                final String key = buildCacheKey(ImmutableMap.<Integer, Object>of(0, tableName.getHistoryTableName(), 1, tableName.getHistoryTableName(), 2, entityRecordId));
                cacheController.remove(key);
            }
        } else {
            final CacheController<String, List> cacheController = cacheControllerDispatcher.getCacheController(CacheType.AUDIT_LOG);
            if (cacheController != null) {
                final String key = buildCacheKey(ImmutableMap.<Integer, Object>of(0, tableName, 1, entityRecordId));
                cacheController.remove(key);
            }
        }
    }

    private String buildCacheKey(final Map<Integer, Object> keyPieces) {
        final StringBuilder cacheKey = new StringBuilder();
        for (int i = 0; i < keyPieces.size(); i++) {
            // To normalize the arguments and avoid casing issues, we make all pieces of the key uppercase.
            // Since the database engine may be case insensitive and we use arguments of the SQL method call
            // to build the key, the key has to be case insensitive as well.
            final String str = String.valueOf(keyPieces.get(i)).toUpperCase();
            cacheKey.append(str);
            if (i < keyPieces.size() - 1) {
                cacheKey.append(CacheControllerDispatcher.CACHE_KEY_SEPARATOR);
            }
        }
        return cacheKey.toString();
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
