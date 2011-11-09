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

package com.ning.billing.beatrix.lifecycle;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.lifecycle.IService;
import com.ning.billing.lifecycle.LyfecycleHandlerType;
import com.ning.billing.lifecycle.LyfecycleHandlerType.LyfecycleLevel;


public class Lifecycle {

    private final static Logger log = LoggerFactory.getLogger(Lifecycle.class);
    private final SetMultimap<LyfecycleLevel, LifecycleHandler> handlersByLevel;

    private final ServiceFinder serviceFinder;

    private final Injector injector;

    @Inject
    public Lifecycle(Injector injector) {

        this.serviceFinder = new ServiceFinder(Lifecycle.class.getClassLoader());
        this.handlersByLevel = Multimaps.newSetMultimap(new ConcurrentHashMap<LyfecycleLevel, Collection<LifecycleHandler>>(),

                new Supplier<Set<LifecycleHandler>>() {
                  @Override
                  public Set<LifecycleHandler> get() {
                    return new CopyOnWriteArraySet<LifecycleHandler>();
                  }
                });
        this.injector = injector;
    }

    public void init() {
        Set<? extends IService> services = findServices();
        Iterator<? extends IService> it = services.iterator();
        while (it.hasNext()) {
            handlersByLevel.putAll(findAllHandlers(it.next()));
        }
    }

    public void fireStages() {
        for (LyfecycleLevel level : LyfecycleLevel.values()) {
            log.info("Firing stage {}", level);
            Set<LifecycleHandler> handlers = handlersByLevel.get(level);
            for (LifecycleHandler cur : handlers) {
                log.debug("Calling handler {}", cur.getMethod().getName());
                try {
                    Method method = cur.getMethod();
                    Object target = cur.getTarget();
                    method.invoke(target);
                } catch (Exception e) {
                    log.warn("Failed to invoke lifecycle handler", e);
                }
            }
        }
    }

    private Set<? extends IService> findServices() {

        Set<IService> result = new TreeSet<IService>();
        Set<Class<? extends IService>> services =  serviceFinder.getServices();
        for (Class<? extends IService> cur : services) {
            log.debug("Found service {}", cur);
            try {
                IService service = injector.getInstance(cur);
                result.add(injector.getInstance(cur));
            } catch (Exception e) {
                log.warn("Failed to inject {}", cur.getName());
            }

        }
        return result;
    }


    public Multimap<LyfecycleLevel, LifecycleHandler> findAllHandlers(IService service) {
        Multimap<LyfecycleLevel, LifecycleHandler> methodsInService =
            HashMultimap.create();
        Class<? extends IService> clazz = service.getClass();
        for (Method method : clazz.getMethods()) {
            LyfecycleHandlerType annotation = method.getAnnotation(LyfecycleHandlerType.class);
            if (annotation != null) {
                LyfecycleLevel level = annotation.value();
                LifecycleHandler handler = new LifecycleHandler(service, method);
                methodsInService.put(level, handler);
            }
        }
        return methodsInService;
    }


    private final class LifecycleHandler {
        private final Object target;
        private final Method method;

        public LifecycleHandler(Object target, Method method) {
            this.target = target;
            this.method = method;
        }

        public Object getTarget() {
            return target;
        }

        public Method getMethod() {
            return method;
        }
    }
}
