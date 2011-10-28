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

package com.ning.billing.util.notification;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

public class TestNotificationSystem {
	
	private class TestNotify implements INotification {}
	private class TestDontNotify implements INotification {}
	private boolean notified;
	
	
	@Test(enabled=true)
	public void test(){
		NotificationSystem ns = new NotificationSystem();
		
		ns.register(new INotificationHandler<TestNotify>() {

			@Override
			public void handle(TestNotify notification) {
				notified = true;
				
			}
		}, TestNotify.class);
		
		ns.register(new INotificationHandler<TestDontNotify>() {

			@Override
			public void handle(TestDontNotify notification) {
				fail("This notification should not be called");
				
			}
		}, TestDontNotify.class);
		
		ns.publish(new TestNotify());
		
		assertTrue(notified);
	}
}
