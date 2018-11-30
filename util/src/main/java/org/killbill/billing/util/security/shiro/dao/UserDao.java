/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import org.killbill.billing.security.SecurityApiException;

public interface UserDao {

    public void insertUser(String username, String password, List<String> roles, String createdBy) throws SecurityApiException;

    public List<UserRolesModelDao> getUserRoles(String username) throws SecurityApiException;

    public void addRoleDefinition(String role, List<String> permissions, String createdBy) throws SecurityApiException;

    public void updateRoleDefinition(String role, List<String> permissions, String createdBy) throws SecurityApiException;

    public List<RolesPermissionsModelDao> getRoleDefinition(String role);

    public void updateUserPassword(String username, String password, String createdBy) throws SecurityApiException;

    public void updateUserRoles(String username, List<String> roles, String createdBy) throws SecurityApiException;

    public void invalidateUser(String username, String createdBy) throws SecurityApiException;
}
