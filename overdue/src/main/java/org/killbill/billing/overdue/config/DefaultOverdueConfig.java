/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.overdue.config;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlRootElement(name = "overdueConfig")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultOverdueConfig extends ValidatingConfig<DefaultOverdueConfig> implements OverdueConfig, Externalizable {

    private static final long serialVersionUID = 1282636602472877120L;

    @XmlElement(required = true, name = "accountOverdueStates")
    private DefaultOverdueStatesAccount accountOverdueStates = new DefaultOverdueStatesAccount();

    public DefaultOverdueStatesAccount getOverdueStatesAccount() {
        return accountOverdueStates;
    }

    @Override
    public ValidationErrors validate(final DefaultOverdueConfig root,
                                     final ValidationErrors errors) {
        return accountOverdueStates.validate(root, errors);
    }

    // For deserialization
    public DefaultOverdueConfig() {}

    public DefaultOverdueConfig setOverdueStates(final DefaultOverdueStatesAccount accountOverdueStates) {
        this.accountOverdueStates = accountOverdueStates;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultOverdueConfig that = (DefaultOverdueConfig) o;

        return accountOverdueStates != null ? accountOverdueStates.equals(that.accountOverdueStates) : that.accountOverdueStates == null;
    }

    @Override
    public int hashCode() {
        return accountOverdueStates != null ? accountOverdueStates.hashCode() : 0;
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.accountOverdueStates = (DefaultOverdueStatesAccount) in.readObject();
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        oo.writeObject(accountOverdueStates);
    }
}
