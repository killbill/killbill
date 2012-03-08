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

package com.ning.billing.catalog.overdue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

@XmlAccessorType(XmlAccessType.NONE)
public class OverdueStage<T extends Condition> {
	@XmlElement(required=false, name="condition")
	private T condition;

	@XmlAttribute(required=false, name="stageName")
	private String stageName;

	@XmlElement(required=false, name="externalMessage")
	private String externalMessage;

	@XmlElement(required=false, name="cancel")
	private boolean cancel;

	public String getStageName() {
		return stageName;
	}

	public String getExternalMessage() {
		return externalMessage;
	}
	public boolean isCancelled() {
		return cancel;
	}

	public T getCondition() {
		return condition;
	}

	public OverdueStage<T> setStageName(String stageName) {
		this.stageName = stageName;
		return this;
	}

	public OverdueStage<T> setExternalMessage(String externalMessage) {
		this.externalMessage = externalMessage;
		return this;
	}

	public OverdueStage<T> setCancel(boolean cancel) {
		this.cancel = cancel;
		return this;
	}

	public OverdueStage<T> setCondition(T condition) {
		this.condition = condition;
		return this;
	}

}
