package com.ning.billing.util.entity;

import org.skife.jdbi.v2.sqlobject.BindBean;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface UpdatableEntityDao<T extends UpdatableEntity> extends EntityDao<T> {
    @SqlUpdate
    public void update(@BindBean final T entity) throws EntityPersistenceException;
}
