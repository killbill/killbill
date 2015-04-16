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

import org.joda.time.DateTime;

public class RolesPermissionsModelDao {

    private Long recordId;
    private String roleName;
    private String permission;
    private Boolean isActive;
    private DateTime createdDate;
    private String createdBy;
    private DateTime updatedDate;
    private String updatedBy;

    public RolesPermissionsModelDao() {
    }

    public RolesPermissionsModelDao(final Long recordId, final String roleName, final String permission, final Boolean is_active, final DateTime createdDate, final String createdBy, final DateTime updatedDate, final String updatedBy) {
        this.recordId = recordId;
        this.roleName = roleName;
        this.permission = permission;
        this.isActive = is_active;
        this.createdDate = createdDate;
        this.createdBy = createdBy;
        this.updatedDate = updatedDate;
        this.updatedBy = updatedBy;
    }

    public RolesPermissionsModelDao(final String roleName, final String permission, final DateTime createdDate, final String createdBy) {
        this(-1L, roleName, permission, Boolean.TRUE, createdDate, createdBy, createdDate, createdBy);
    }

        public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(final Long recordId) {
        this.recordId = recordId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(final String roleName) {
        this.roleName = roleName;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(final String permission) {
        this.permission = permission;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(final DateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
