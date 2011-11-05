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

package com.ning.billing.entitlement;

import java.util.List;

import com.ning.billing.entitlement.api.billing.IEntitlementBillingApi;
import com.ning.billing.entitlement.api.user.IApiListener;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.lifecycle.IService;

public interface IEntitlementService extends IService {

    IEntitlementUserApi getUserApi(List<IApiListener> listeners);

    IEntitlementBillingApi getBillingApi();

}
