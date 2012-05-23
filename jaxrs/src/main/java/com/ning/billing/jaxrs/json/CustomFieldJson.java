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

import com.fasterxml.jackson.annotation.JsonCreator;

import com.ning.billing.util.customfield.CustomField;

public class CustomFieldJson {

    private final String name;
    private final String value;
    
    public CustomFieldJson() {
        this.name = null;
        this.value = null;
    }
    
    @JsonCreator
    public CustomFieldJson(String name, String value) {
        super();
        this.name = name;
        this.value = value;
    }
    
    public CustomFieldJson(CustomField input) {
        this.name = input.getName();
        this.value = input.getValue();
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
