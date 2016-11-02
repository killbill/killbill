/*
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

package org.killbill.billing.util.security.shiro.dao;

import java.util.List;

import javax.inject.Inject;

import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.security.SecurityApiException;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.security.shiro.KillbillCredentialsMatcher;
import org.killbill.clock.Clock;
import org.killbill.commons.jdbi.mapper.LowerToCamelBeanMapperFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class DefaultUserDao implements UserDao {

    private static final RandomNumberGenerator rng = new SecureRandomNumberGenerator();

    private final IDBI dbi;
    private final Clock clock;
    private final SecurityConfig securityConfig;

    @Inject
    public DefaultUserDao(final IDBI dbi, final Clock clock, final SecurityConfig securityConfig) {
        this.dbi = dbi;
        this.clock = clock;
        this.securityConfig = securityConfig;
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(UserModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(UserRolesModelDao.class));
        ((DBI) dbi).registerMapper(new LowerToCamelBeanMapperFactory(RolesPermissionsModelDao.class));
    }

    @Override
    public void insertUser(final String username, final String password, final List<String> roles, final String createdBy) throws SecurityApiException {
        final ByteSource salt = rng.nextBytes();
        final String hashedPasswordBase64 = new SimpleHash(KillbillCredentialsMatcher.HASH_ALGORITHM_NAME,
                                                           password, salt.toBase64(), securityConfig.getShiroNbHashIterations()).toBase64();

        final DateTime createdDate = clock.getUTCNow();
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final UserRolesSqlDao userRolesSqlDao = handle.attach(UserRolesSqlDao.class);
                for (final String role : roles) {
                    userRolesSqlDao.create(new UserRolesModelDao(username, role, createdDate, createdBy));
                }

                final UsersSqlDao usersSqlDao = handle.attach(UsersSqlDao.class);
                final UserModelDao userModelDao = usersSqlDao.getByUsername(username);
                if (userModelDao != null) {
                    throw new SecurityApiException(ErrorCode.SECURITY_USER_ALREADY_EXISTS, username);
                }
                usersSqlDao.create(new UserModelDao(username, hashedPasswordBase64, salt.toBase64(), createdDate, createdBy));
                return null;
            }
        });
    }

    public List<UserRolesModelDao> getUserRoles(final String username) {
        return dbi.inTransaction(new TransactionCallback<List<UserRolesModelDao>>() {
            @Override
            public List<UserRolesModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final UserRolesSqlDao userRolesSqlDao = handle.attach(UserRolesSqlDao.class);
                return userRolesSqlDao.getByUsername(username);
            }
        });
    }

    @Override
    public void addRoleDefinition(final String role, final List<String> permissions, final String createdBy) {
        final DateTime createdDate = clock.getUTCNow();
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final RolesPermissionsSqlDao rolesPermissionsSqlDao = handle.attach(RolesPermissionsSqlDao.class);
                final List<RolesPermissionsModelDao> existingRole = rolesPermissionsSqlDao.getByRoleName(role);
                if (!existingRole.isEmpty()) {
                    throw new SecurityApiException(ErrorCode.SECURITY_ROLE_ALREADY_EXISTS, role);
                }
                for (final String permission : permissions) {
                    rolesPermissionsSqlDao.create(new RolesPermissionsModelDao(role, permission, createdDate, createdBy));
                }
                return null;
            }
        });

    }

    @Override
    public List<RolesPermissionsModelDao> getRoleDefinition(final String role) {
        return dbi.inTransaction(new TransactionCallback<List<RolesPermissionsModelDao>>() {
            @Override
            public List<RolesPermissionsModelDao> inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final RolesPermissionsSqlDao rolesPermissionsSqlDao = handle.attach(RolesPermissionsSqlDao.class);
                return rolesPermissionsSqlDao.getByRoleName(role);
            }
        });
    }

    @Override
    public void updateUserPassword(final String username, final String password, final String updatedBy) throws SecurityApiException {
        final ByteSource salt = rng.nextBytes();
        final String hashedPasswordBase64 = new SimpleHash(KillbillCredentialsMatcher.HASH_ALGORITHM_NAME,
                                                           password, salt.toBase64(), securityConfig.getShiroNbHashIterations()).toBase64();

        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {

                final DateTime updatedDate = clock.getUTCNow();
                final UsersSqlDao usersSqlDao = handle.attach(UsersSqlDao.class);
                final UserModelDao userModelDao = usersSqlDao.getByUsername(username);
                if (userModelDao == null) {
                    throw new SecurityApiException(ErrorCode.SECURITY_INVALID_USER, username);
                }
                usersSqlDao.updatePassword(username, hashedPasswordBase64, salt.toBase64(), updatedDate.toDate(), updatedBy);
                return null;
            }
        });
    }

    @Override
    public void updateUserRoles(final String username, final List<String> roles, final String updatedBy) throws SecurityApiException {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final DateTime updatedDate = clock.getUTCNow();
                final UsersSqlDao usersSqlDao = handle.attach(UsersSqlDao.class);
                final UserModelDao userModelDao = usersSqlDao.getByUsername(username);
                if (userModelDao == null) {
                    throw new SecurityApiException(ErrorCode.SECURITY_INVALID_USER, username);
                }

                // Remove stale entries
                final UserRolesSqlDao userRolesSqlDao = handle.attach(UserRolesSqlDao.class);
                final List<UserRolesModelDao> existingRoles = userRolesSqlDao.getByUsername(username);
                for (final UserRolesModelDao curRole : existingRoles) {
                    if (Iterables.tryFind(roles, new Predicate<String>() {
                        @Override
                        public boolean apply(final String input) {
                            return input.equals(curRole.getRoleName());
                        }
                    }).orNull() == null) {
                        userRolesSqlDao.invalidate(username, curRole.getRoleName(), updatedDate.toDate(), updatedBy);
                    }
                }

                // Add new entries
                for (final String curNewRole : roles) {
                    if (Iterables.tryFind(existingRoles, new Predicate<UserRolesModelDao>() {
                        @Override
                        public boolean apply(final UserRolesModelDao input) {
                            return input.getRoleName().equals(curNewRole);
                        }
                    }).orNull() == null) {
                        userRolesSqlDao.create(new UserRolesModelDao(username, curNewRole, updatedDate, updatedBy));
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void invalidateUser(final String username, final String updatedBy) throws SecurityApiException {
        dbi.inTransaction(new TransactionCallback<Void>() {
            @Override
            public Void inTransaction(final Handle handle, final TransactionStatus status) throws Exception {
                final DateTime updatedDate = clock.getUTCNow();
                final UsersSqlDao usersSqlDao = handle.attach(UsersSqlDao.class);
                final UserModelDao userModelDao = usersSqlDao.getByUsername(username);
                if (userModelDao == null) {
                    throw new SecurityApiException(ErrorCode.SECURITY_INVALID_USER, username);
                }
                usersSqlDao.invalidate(username, updatedDate.toDate(), updatedBy);
                return null;
            }
        });
    }
}
