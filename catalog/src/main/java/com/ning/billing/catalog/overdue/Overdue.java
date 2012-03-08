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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.overdue.BillingStateBundle;
import com.ning.billing.catalog.api.overdue.OverdueState;

@XmlAccessorType(XmlAccessType.NONE)
public class Overdue {

	@XmlElement(required=false, name="bundleOverdueStages")
	private OverdueStage<BundleCondition>[] bundleOverdueStages;
	
	
	public List<OverdueState> calculateBundleOverdueState(BillingStateBundle[] states, DateTime now){
		List<OverdueState> result = new ArrayList<OverdueState>();
		for(BillingStateBundle state : states) {
			for(OverdueStage<BundleCondition> stage : bundleOverdueStages) {
				if(stage.getCondition().evaluate(state, now)) {	
					OverdueState ods = new OverdueState(state.getObjectId(),stage.getStageName(), 
							stage.getExternalMessage(), stage.isCancelled());
					result.add(ods);
					break;
				}
			}
		}
		return result;
	}

	
}
