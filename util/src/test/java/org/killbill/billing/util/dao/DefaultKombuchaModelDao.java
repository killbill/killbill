/*
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

package org.killbill.billing.util.dao;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;

public class DefaultKombuchaModelDao extends EntityModelDaoBase implements KombuchaModelDao {

    private String tea;
    private String mushroom;
    private String sugar;

    public DefaultKombuchaModelDao() { }

    public DefaultKombuchaModelDao(final String tea,
                                   final String mushroom,
                                   final String sugar,
                                   final InternalTenantContext internalTenantContext) {
        this.tea = tea;
        this.mushroom = mushroom;
        this.sugar = sugar;
        setAccountRecordId(internalTenantContext.getAccountRecordId());
        setTenantRecordId(internalTenantContext.getTenantRecordId());
    }

    public String getTea() {
        return tea;
    }

    public void setTea(final String tea) {
        this.tea = tea;
    }

    public String getMushroom() {
        return mushroom;
    }

    public void setMushroom(final String mushroom) {
        this.mushroom = mushroom;
    }

    public String getSugar() {
        return sugar;
    }

    public void setSugar(final String sugar) {
        this.sugar = sugar;
    }

    @Override
    public TableName getTableName() {
        return null;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
