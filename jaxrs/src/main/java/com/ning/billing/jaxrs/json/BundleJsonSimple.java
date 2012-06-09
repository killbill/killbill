/* 
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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
package com.ning.billing.jaxrs.json;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

public class BundleJsonSimple {

    protected final String bundleId;

    protected final String externalKey;

    @JsonCreator
    public BundleJsonSimple(@JsonProperty("bundleId") @Nullable final String bundleId,
                            @JsonProperty("externalKey") @Nullable final String externalKey) {
        this.bundleId = bundleId;
        this.externalKey = externalKey;
    }

    public BundleJsonSimple() {
        this(null, null);
    }

    @JsonProperty("bundleId")
    public String getBundleId() {
        return bundleId;
    }

    @JsonProperty("externalKey")
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BundleJsonSimple that = (BundleJsonSimple) o;

        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = bundleId != null ? bundleId.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        return result;
    }
}
