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

package com.ning.billing.payment.api;

import java.util.UUID;

import com.google.common.base.Objects;

public class PaymentMethodInfo {

    private final String id;
    private final String accountId;
    private final Boolean defaultMethod;
    private final String type;

    public PaymentMethodInfo(String id,
                             String accountId,
                             Boolean defaultMethod,
                             String type) {
        this.id = id;
        this.accountId = accountId;
        this.defaultMethod = defaultMethod;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public Boolean getDefaultMethod() {
        return defaultMethod;
    }

    public String getType() {
        return type;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id,
                                accountId,
                                defaultMethod,
                                type);
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() == obj.getClass()) {
            PaymentMethodInfo other = (PaymentMethodInfo)obj;
            if (obj == other) {
                return true;
            }
            else {
                return Objects.equal(id, other.id) &&
                       Objects.equal(accountId, other.accountId) &&
                       Objects.equal(defaultMethod, other.defaultMethod) &&
                       Objects.equal(type, other.type);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "PaymentMethodInfo [id=" + id + ", accountId=" + accountId + ", defaultMethod=" + defaultMethod + ", type=" + type + "]";
    }

    protected abstract static class BuilderBase<T extends PaymentMethodInfo, V extends BuilderBase<T, V>> {
        protected final Class<V> builderClazz;
        protected String id;
        protected String accountId;
        protected Boolean defaultMethod;

        protected BuilderBase(Class<V> builderClazz) {
            this.builderClazz = builderClazz;
        }

        protected BuilderBase(Class<V> builderClazz, T src) {
            this(builderClazz);
            this.id = src.id;
            this.accountId = src.accountId;
            this.defaultMethod = src.defaultMethod;
        }

        public V setId(String id) {
            this.id = id;
            return builderClazz.cast(this);
        }

        public V setAccountId(String accountId) {
            this.accountId = accountId;
            return builderClazz.cast(this);
        }

        public V setDefaultMethod(Boolean defaultMethod) {
            this.defaultMethod = defaultMethod;
            return builderClazz.cast(this);
        }
    }

    public static class Builder extends BuilderBase<PaymentMethodInfo, Builder> {
        private String type;

        public Builder() {
            super(Builder.class);
        }

        public Builder(PaymentMethodInfo src) {
            super(Builder.class, src);
            this.type = src.type;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        public PaymentMethodInfo build() {
            return new PaymentMethodInfo(id, accountId, defaultMethod, type);
        }
    }
}
