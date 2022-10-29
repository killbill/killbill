 /*
  * Copyright 2020-2022 Equinix, Inc
  * Copyright 2014-2022 The Billing Project, LLC
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

 package org.killbill.billing.subscription.events.expired;

 import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.subscription.events.EventBase;

 public class ExpiredEventData extends EventBase implements ExpiredEvent {

     public ExpiredEventData(final ExpiredEventBuilder builder) {
         super(builder);
     }

     @Override
     public EventType getType() {
         return EventType.EXPIRED;
     }
     
     public static ExpiredEvent createExpiredEvent(final UUID subscriptionId, final DateTime effectiveDate) {
         return new ExpiredEventData(new ExpiredEventBuilder()
                                            .setSubscriptionId(subscriptionId)
                                            .setEffectiveDate(effectiveDate)
                                           );
     }     

 }
