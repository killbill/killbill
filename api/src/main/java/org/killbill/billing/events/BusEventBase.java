/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.events;

import java.util.UUID;

import org.killbill.bus.api.BusEvent;

public class BusEventBase implements BusEvent {

    private final Long searchKey1;
    private final Long searchKey2;
    private final UUID userToken;

    public BusEventBase(final Long searchKey1,
                        final Long searchKey2,
                        final UUID userToken) {
        this.searchKey1 = searchKey1;
        this.searchKey2 = searchKey2;
        this.userToken = userToken;
    }

    @Override
    public Long getSearchKey1() {
        return searchKey1;
    }

    @Override
    public Long getSearchKey2() {
        return searchKey2;
    }

    @Override
    public UUID getUserToken() {
        return userToken;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BusEventBase)) {
            return false;
        }

        final BusEventBase that = (BusEventBase) o;

        if (searchKey1 != null ? !searchKey1.equals(that.searchKey1) : that.searchKey1 != null) {
            return false;
        }
        if (searchKey2 != null ? !searchKey2.equals(that.searchKey2) : that.searchKey2 != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = searchKey1 != null ? searchKey1.hashCode() : 0;
        result = 31 * result + (searchKey2 != null ? searchKey2.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        return result;
    }
}
