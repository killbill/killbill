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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

public class AccountJsonSimple {

    @JsonView(BundleTimelineViews.Base.class)
    protected final String acountId;
    
    @JsonView(BundleTimelineViews.Base.class)
    protected final String externalKey;
    
    public AccountJsonSimple() {
        this.acountId = null;
        this.externalKey = null;
    }

    @JsonCreator
    public AccountJsonSimple(@JsonProperty("account_id") String acountId,
            @JsonProperty("external_key") String externalKey) {
        this.acountId = acountId;
        this.externalKey = externalKey;
    }

    public String getAcountId() {
        return acountId;
    }

    public String getExternalKey() {
        return externalKey;
    }
}
