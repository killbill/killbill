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

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInvocation;

// Taken from Shiro - the original class is private :(
public class AopAllianceMethodInvocationAdapter implements org.apache.shiro.aop.MethodInvocation {

    private final MethodInvocation mi;

    public AopAllianceMethodInvocationAdapter(final MethodInvocation mi) {
        this.mi = mi;
    }

    public Method getMethod() {
        return mi.getMethod();
    }

    public Object[] getArguments() {
        return mi.getArguments();
    }

    public String toString() {
        return "Method invocation [" + mi.getMethod() + "]";
    }

    public Object proceed() throws Throwable {
        return mi.proceed();
    }

    public Object getThis() {
        return mi.getThis();
    }
}
