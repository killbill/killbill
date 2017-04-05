/*
 * Copyright 2017 Groupon, Inc
 * Copyright 2017 The Billing Project, LLC
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

import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SessionsSecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.DelegatingSubject;
import org.apache.shiro.util.CollectionUtils;
import org.killbill.billing.util.security.shiro.dao.JDBCSessionDao;

public class KillBillSubjectDAO extends DefaultSubjectDAO {

    @Override
    protected void saveToSession(final Subject subject) {
        boolean updatesDisabled = false;

        Session session = subject.getSession(false);
        if (session == null && !CollectionUtils.isEmpty(subject.getPrincipals())) {
            // Force the creation of the session here to get the id
            session = subject.getSession();
            // Optimize the session creation path: the default saveToSession implementation
            // will call setAttribute() several times in a row, causing unnecessary DAO UPDATE queries
            updatesDisabled = disableUpdatesForSession(subject, session);
        }

        super.saveToSession(subject);

        if (updatesDisabled) {
            enableUpdatesForSession(subject, session);
        }
    }

    private boolean disableUpdatesForSession(final Subject subject, final Session session) {
        final JDBCSessionDao sessionDAO = getJDBCSessionDao(subject);
        if (sessionDAO != null) {
            sessionDAO.disableUpdatesForSession(session);
            return true;
        }
        return false;
    }

    private void enableUpdatesForSession(final Subject subject, final Session session) {
        final JDBCSessionDao sessionDAO = getJDBCSessionDao(subject);
        if (sessionDAO != null) {
            sessionDAO.enableUpdatesForSession(session);
        }
    }

    private JDBCSessionDao getJDBCSessionDao(final Subject subject) {
        if (subject instanceof DelegatingSubject) {
            final DelegatingSubject delegatingSubject = (DelegatingSubject) subject;
            if (delegatingSubject.getSecurityManager() instanceof SessionsSecurityManager) {
                final SessionsSecurityManager securityManager = (SessionsSecurityManager) delegatingSubject.getSecurityManager();
                if (securityManager.getSessionManager() instanceof DefaultSessionManager) {
                    final DefaultSessionManager sessionManager = (DefaultSessionManager) securityManager.getSessionManager();
                    if (sessionManager.getSessionDAO() instanceof JDBCSessionDao) {
                        return (JDBCSessionDao) sessionManager.getSessionDAO();
                    }
                }
            }
        }

        return null;
    }
}
