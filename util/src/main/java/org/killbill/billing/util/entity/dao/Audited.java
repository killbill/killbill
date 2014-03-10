/*
 * Copyright 2010-2012 Ning, Inc.
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

package org.killbill.billing.util.entity.dao;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.killbill.billing.util.audit.ChangeType;

/**
 * The <code>Audited</code> annotation wraps a Sql dao method and
 * create Audit and History entries as needed. Every r/w
 * database operation on any Entity should have this annotation.
 * <p/>
 * To create a audit entries automatically for some method <code>updateChargedThroughDate</code>:
 * <pre>
 *         @Audited(type = ChangeType.UPDATE)
 *         @SqlUpdate public void updateChargedThroughDate(@Bind("id") String id,
 *                                                         @Bind("chargedThroughDate") Date chargedThroughDate,
 *                                                         @InternalTenantContextBinder final InternalCallContext callcontext);
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Audited {

    /**
     * @return the type of operation
     */
    ChangeType value();
}
