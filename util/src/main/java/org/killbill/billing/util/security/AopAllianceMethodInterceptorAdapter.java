/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.util.security;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

// Taken from Shiro - the original class is private :(
public class AopAllianceMethodInterceptorAdapter implements MethodInterceptor {

    org.apache.shiro.aop.MethodInterceptor shiroInterceptor;

    public AopAllianceMethodInterceptorAdapter(final org.apache.shiro.aop.MethodInterceptor shiroInterceptor) {
        this.shiroInterceptor = shiroInterceptor;
    }

    public Object invoke(final MethodInvocation invocation) throws Throwable {
        return shiroInterceptor.invoke(new AopAllianceMethodInvocationAdapter(invocation));
    }

    @Override
    public String toString() {
        return "AopAlliance Adapter for " + shiroInterceptor.toString();
    }
}
