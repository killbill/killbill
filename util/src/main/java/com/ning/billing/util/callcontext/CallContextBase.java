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

package com.ning.billing.util.callcontext;

import java.util.UUID;

public abstract class CallContextBase implements CallContext {
	
	private final UUID userToken;
    private final String userName;
    private final CallOrigin callOrigin;
    private final UserType userType;


    public CallContextBase(String userName, CallOrigin callOrigin, UserType userType) {
    	this(userName, callOrigin, userType, null);
    }

    public CallContextBase(String userName, CallOrigin callOrigin, UserType userType, UUID userToken) {
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
        this.userToken = userToken;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    @Override
    public UserType getUserType() {
        return userType;
    }
    
    @Override
    public UUID getUserToken() {
    	return userToken;
    }
}
