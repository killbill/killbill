/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.entity.dao;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.dao.NonEntitySqlDao;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.tag.dao.UUIDCollectionBinder;
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
import org.skife.jdbi.v2.sqlobject.SqlObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
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

    private final Class<S> sqlDaoClass;
    private final S sqlDao;
    private final Handle handle;

    private final CacheControllerDispatcher cacheControllerDispatcher;
    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Profiling prof;

    public EntitySqlDaoWrapperInvocationHandler(final Class<S> sqlDaoClass,
                                                final S sqlDao,
                                                final Handle handle,
                                                final Clock clock,
                                                // Special DAO that don't require caching can invoke EntitySqlDaoWrapperInvocationHandler with no caching (e.g NoCachingTenantDao)
                                                @Nullable final CacheControllerDispatcher cacheControllerDispatcher,
                                                @Nullable final NonEntityDao nonEntityDao,
                                                final InternalCallContextFactory internalCallContextFactory) {
        this.sqlDaoClass = sqlDaoClass;
        this.sqlDao = sqlDao;
        this.handle = handle;
        this.clock = clock;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
        this.nonEntityDao = nonEntityDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.prof = new Profiling<Object, Throwable>();
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
            return prof.executeWithProfiling(ProfilingFeatureType.DAO, sqlDaoClass.getSimpleName() + ":" + method.getName(), new WithProfilingCallback() {
                @Override
                public Object execute() throws Throwable {
                    return invokeSafely(proxy, method, args);
                }
            });
        } catch (Throwable t) {
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
        return prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, sqlDaoClass.getSimpleName() + " (raw):" + method.getName(), new WithProfilingCallback() {
            @Override
            public Object execute() throws Throwable {
                Object result = method.invoke(sqlDao, args);
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
        Object result = null;
        if (cache != null) {
            // Find all arguments marked with @CachableKey
            final Map<Integer, Object> keyPieces = new LinkedHashMap<Integer, Object>();
            final Annotation[][] annotations = method.getParameterAnnotations();
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
        }
        if (result == null) {
            result = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, sqlDaoClass.getSimpleName() + "(raw) :" + method.getName(), new WithProfilingCallback() {
                @Override
                public Object execute() throws Throwable {
                    return method.invoke(sqlDao, args);
                }
            });
        }
        return result;
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
                if (EntityModelDao.class.getName().equals(((Class) ((java.lang.reflect.ParameterizedType) clz.getGenericInterfaces()[0]).getRawType()).getName())) {
                    foundIndexForEntityModelDao = i;
                    break;
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
        InternalCallContext context = null;
        List<String> entityIds = null;
        final Map<String, M> entities = new HashMap<String, M>();
        final Map<String, Long> entityRecordIds = new HashMap<String, Long>();
        if (auditedAnnotation != null) {
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
        final Object obj = prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, sqlDaoClass.getSimpleName() + " (raw) :", new WithProfilingCallback() {
            @Override
            public Object execute() throws Throwable {
                return method.invoke(sqlDao, args);
            }
        });

        final ChangeType changeType = auditedAnnotation.value();

        for (final String entityId : entityIds) {
            updateHistoryAndAudit(entityId, entities, entityRecordIds, changeType, context);
        }
        return obj;
    }

    private void populateCacheOnGetByIdInvocation(M model) {

        final CacheController<Object, Object> cacheRecordId = cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID);
        cacheRecordId.add(getKey(model.getId().toString(), CacheType.RECORD_ID, model.getTableName()), model.getRecordId());

        final CacheController<Object, Object> cacheObjectId = cacheControllerDispatcher.getCacheController(CacheType.OBJECT_ID);
        cacheObjectId.add(getKey(model.getRecordId().toString(), CacheType.OBJECT_ID, model.getTableName()), model.getId());

        if (model.getTenantRecordId() != null) {
            final CacheController<Object, Object> cacheTenantRecordId = cacheControllerDispatcher.getCacheController(CacheType.TENANT_RECORD_ID);
            cacheTenantRecordId.add(getKey(model.getId().toString(), CacheType.TENANT_RECORD_ID, model.getTableName()), model.getTenantRecordId());
        }

        if (model.getAccountRecordId() != null) {
            final CacheController<Object, Object> cacheAccountRecordId = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID);
            cacheAccountRecordId.add(getKey(model.getId().toString(), CacheType.ACCOUNT_RECORD_ID, model.getTableName()), model.getAccountRecordId());
        }
    }

    private String getKey(final String rawKey, final CacheType cacheType, final TableName tableName) {
        return cacheType.isKeyPrefixedWithTableName() ?
               tableName + CacheControllerDispatcher.CACHE_KEY_SEPARATOR + rawKey :
               rawKey;
    }

    private void updateHistoryAndAudit(final String entityId, final Map<String, M> entities, final Map<String, Long> entityRecordIds,
                                       final ChangeType changeType, final InternalCallContext context) throws Throwable {

        prof.executeWithProfiling(ProfilingFeatureType.DAO_DETAILS, sqlDaoClass.getSimpleName() + " (history/audit) :", new WithProfilingCallback() {
            @Override
            public Object execute() {
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

                // Make sure to re-hydrate the object (especially needed for create calls)
                insertAudits(tableName, entityRecordId, historyRecordId, changeType, context);
                return null;
            }
        });
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

            for (final Annotation annotation : parameterAnnotations[i]) {
                if (arg instanceof String && Bind.class.equals(annotation.annotationType()) && ("id").equals(((Bind) annotation).value())) {
                    return ImmutableList.<String>of((String) arg);
                } else if (arg instanceof Collection && UUIDCollectionBinder.class.equals(annotation.annotationType())) {
                    return ImmutableList.<String>copyOf((Collection) arg);
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
        final EntityHistoryModelDao<M, E> history = new EntityHistoryModelDao<M, E>(entityModelDao, entityRecordId, changeType, clock.getUTCNow());

        sqlDao.addHistoryFromTransaction(history, context);

        final NonEntitySqlDao transactional = SqlObjectBuilder.attach(handle, NonEntitySqlDao.class);

        /* return transactional.getLastHistoryRecordId(entityRecordId, entityModelDao.getHistoryTableName().getTableName()); */
        return nonEntityDao.retrieveLastHistoryRecordIdFromTransaction(entityRecordId, entityModelDao.getHistoryTableName(), transactional);
    }

    private void insertAudits(final TableName tableName, final Long entityRecordId, final Long historyRecordId, final ChangeType changeType, final InternalCallContext contextMaybeWithoutAccountRecordId) {
        final TableName destinationTableName = Objects.firstNonNull(tableName.getHistoryTableName(), tableName);
        final EntityAudit audit = new EntityAudit(destinationTableName, historyRecordId, changeType, clock.getUTCNow());

        final InternalCallContext context;
        // Populate the account record id when creating the account record
        if (TableName.ACCOUNT.equals(tableName) && ChangeType.INSERT.equals(changeType)) {
            context = internalCallContextFactory.createInternalCallContext(entityRecordId, contextMaybeWithoutAccountRecordId);
        } else {
            context = contextMaybeWithoutAccountRecordId;
        }
        sqlDao.insertAuditFromTransaction(audit, context);

        // We need to invalidate the caches. There is a small window of doom here where caches will be stale.
        // TODO Knowledge on how the key is constructed is also in AuditSqlDao
        if (tableName.getHistoryTableName() != null) {
            final CacheController<Object, Object> cacheController = cacheControllerDispatcher.getCacheController(CacheType.AUDIT_LOG_VIA_HISTORY);
            if (cacheController != null) {
                final String key = buildCacheKey(ImmutableMap.<Integer, Object>of(0, tableName.getHistoryTableName(), 1, tableName.getHistoryTableName(), 2, entityRecordId));
                cacheController.remove(key);
            }
        } else {
            final CacheController<Object, Object> cacheController = cacheControllerDispatcher.getCacheController(CacheType.AUDIT_LOG);
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
}
