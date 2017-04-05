/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.payment.caching;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.killbill.automaton.LinkStateMachine;
import org.killbill.automaton.MissingEntryException;
import org.killbill.automaton.StateMachine;
import org.killbill.automaton.StateMachineConfig;
import org.killbill.billing.util.cache.ExternalizableInput;
import org.killbill.billing.util.cache.ExternalizableOutput;
import org.killbill.billing.util.cache.MapperHolder;

public class SerializableStateMachineConfig implements StateMachineConfig, Externalizable {

    private static final long serialVersionUID = 945320595649172168L;

    private StateMachineConfig stateMachineConfig;

    // For deserialization
    public SerializableStateMachineConfig() {}

    public SerializableStateMachineConfig(final StateMachineConfig stateMachineConfig) {
        this.stateMachineConfig = stateMachineConfig;
    }

    @Override
    public StateMachine[] getStateMachines() {
        return stateMachineConfig.getStateMachines();
    }

    @Override
    public LinkStateMachine[] getLinkStateMachines() {
        return stateMachineConfig.getLinkStateMachines();
    }

    @Override
    public StateMachine getStateMachineForState(final String stateName) throws MissingEntryException {
        return stateMachineConfig.getStateMachineForState(stateName);
    }

    @Override
    public StateMachine getStateMachine(final String stateMachineName) throws MissingEntryException {
        return stateMachineConfig.getStateMachine(stateMachineName);
    }

    @Override
    public LinkStateMachine getLinkStateMachine(final String linkStateMachineName) throws MissingEntryException {
        return stateMachineConfig.getLinkStateMachine(linkStateMachineName);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        MapperHolder.mapper().readerForUpdating(this).readValue(new ExternalizableInput(in));
    }

    @Override
    public void writeExternal(final ObjectOutput oo) throws IOException {
        MapperHolder.mapper().writeValue(new ExternalizableOutput(oo), this);
    }
}
