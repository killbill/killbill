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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class NotificationSystem {
	Hashtable<Class<? extends INotification>, List<INotificationHandler<?>>> handlers = 
			new Hashtable<Class<? extends INotification>, List<INotificationHandler<?>>>();
	
	public <T extends INotification> void register(INotificationHandler<T> handler, Class<T> clazz) {
		List<INotificationHandler<?>> hList = handlers.get(clazz);
		if (hList == null) {
			hList = new ArrayList<INotificationHandler<?>>();
			handlers.put(clazz, hList);
		}
		hList.add(handler);
	}
	
	public <T extends INotification> void publish(T notification) {
		List<INotificationHandler<?>> hList = handlers.get(notification.getClass());
		if (hList != null) {
			for(INotificationHandler<?> handler : hList) {
				// Unfortunate cast but the hashtable contains many classes and generics are
				// not up to it without an explicit cast. Cast is safe by construction though.
				@SuppressWarnings("unchecked")
				INotificationHandler<T> handlerOfT = (INotificationHandler<T>) handler;
				handlerOfT.handle(notification);
			}
		}
	}
	
	
	
}
