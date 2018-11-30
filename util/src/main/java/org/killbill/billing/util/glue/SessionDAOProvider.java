/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.glue;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.CachingSessionDAO;
import org.apache.shiro.session.mgt.eis.SessionDAO;
import org.killbill.billing.util.config.definition.RbacConfig;
import org.killbill.billing.util.config.definition.RedisCacheConfig;
import org.killbill.billing.util.security.shiro.dao.JDBCSessionDao;
import org.killbill.billing.util.security.shiro.dao.RedisSessionDao;
import org.skife.jdbi.v2.IDBI;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class SessionDAOProvider implements Provider<SessionDAO> {

    private final SessionManager sessionManager;
    private final IDBI dbi;
    private final IDBI roDbi;
    private final RbacConfig rbacConfig;
    private final RedisCacheConfig redisCacheConfig;

    @Inject
    public SessionDAOProvider(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final SessionManager sessionManager, final RbacConfig rbacConfig, final RedisCacheConfig redisCacheConfig) {
        this.sessionManager = sessionManager;
        this.dbi = dbi;
        this.roDbi = roDbi;
        this.rbacConfig = rbacConfig;
        this.redisCacheConfig = redisCacheConfig;
    }

    @Override
    public SessionDAO get() {
        final CachingSessionDAO sessionDao;
        if (redisCacheConfig.isRedisCachingEnabled()) {
            sessionDao = new RedisSessionDao();
        } else {
            sessionDao = new JDBCSessionDao(dbi, roDbi);
        }

        if (sessionManager instanceof DefaultSessionManager) {
            final DefaultSessionManager defaultSessionManager = (DefaultSessionManager) sessionManager;
            defaultSessionManager.setSessionDAO(sessionDao);
            defaultSessionManager.setGlobalSessionTimeout(rbacConfig.getGlobalSessionTimeout().getMillis());
        }

        return sessionDao;
    }
}
