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
package com.ning.billing.account.api.user;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.ChangedField;
import com.ning.billing.account.api.DefaultChangedField;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent.DefaultAccountData;

public class TestEventJson {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups= {"fast"})
    public void testDefaultAccountChangeEvent() throws Exception {

        List<ChangedField> changes = new ArrayList<ChangedField>();
        changes.add(new DefaultChangedField("fieldXX", "valueX", "valueXXX"));
        changes.add(new DefaultChangedField("fieldYY", "valueY", "valueYYY"));        
        AccountChangeEvent e = new DefaultAccountChangeEvent(UUID.randomUUID(), changes, UUID.randomUUID());
        
        String json = mapper.writeValueAsString(e);
        
        Class<?> claz = Class.forName("com.ning.billing.account.api.user.DefaultAccountChangeEvent");
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }
    
    @Test(groups= {"fast"})
    public void testAccountCreationEvent() throws Exception {
        
        DefaultAccountData data = new DefaultAccountData("dsfdsf", "bobo", 3, "bobo@yahoo.com", 12, "USD", UUID.randomUUID(), 
                "UTC", "US", "21 avenue", "", "Gling", "San Franciso", "CA", "94110", "USA", "4126789887", false, false);
        DefaultAccountCreationEvent e = new DefaultAccountCreationEvent(data, UUID.randomUUID(), UUID.randomUUID());
        
        String json = mapper.writeValueAsString(e);
        Class<?> claz = Class.forName(DefaultAccountCreationEvent.class.getName());
        Object obj =  mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
        
    }
}
