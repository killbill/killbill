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

package org.killbill.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlID;

import org.killbill.billing.catalog.api.Unit;
import org.killbill.billing.util.config.catalog.ValidatingConfig;
import org.killbill.billing.util.config.catalog.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultUnit extends ValidatingConfig<StandaloneCatalog> implements Unit {
    
    @XmlAttribute(required = true)
    @XmlID
    private String name;

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.Unit#getName()
     */
    @Override
    public String getName() {
        return name;
    }

    @Override
    public ValidationErrors validate(StandaloneCatalog root, ValidationErrors errors) {
        return errors;
    }

}
