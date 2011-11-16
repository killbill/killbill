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
import java.util.List;
import java.util.Set;
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
import com.ning.billing.lifecycle.LyfecycleHandlerType.LyfecycleLevel.Sequence;


public class Lifecycle {

    private final static Logger log = LoggerFactory.getLogger(Lifecycle.class);
    private final SetMultimap<LyfecycleLevel, LifecycleHandler<? extends IService>> handlersByLevel;

    private final ServiceFinder serviceFinder;

    private final Injector injector;

    @Inject
    public Lifecycle(Injector injector) {

        this.serviceFinder = new ServiceFinder(Lifecycle.class.getClassLoader());
        this.handlersByLevel = Multimaps.newSetMultimap(new ConcurrentHashMap<LyfecycleLevel, Collection<LifecycleHandler<? extends IService>>>(),

                new Supplier<Set<LifecycleHandler<? extends IService>>>() {
            @Override
            public Set<LifecycleHandler<? extends IService>> get() {
                return new CopyOnWriteArraySet<LifecycleHandler<? extends IService>>();
            }
        });
        this.injector = injector;

        init();
    }

    public void init() {
        Set<? extends IService> services = findServices();
        Iterator<? extends IService> it = services.iterator();
        while (it.hasNext()) {
            handlersByLevel.putAll(findAllHandlers(it.next()));
        }
    }


    public void fireStartupSequencePriorEventRegistration() {
        fireSequence(Sequence.STARTUP_PRE_EVENT_REGISTRATION);
    }

    public void fireStartupSequencePostEventRegistration() {
        fireSequence(Sequence.STARTUP_POST_EVENT_REGISTRATION);
    }

    public void fireShutdownSequencePriorEventUnRegistration() {
        fireSequence(Sequence.SHUTOWN_PRE_EVENT_UNREGISTRATION);
    }

    public void fireShutdownSequencePostEventUnRegistration() {
        fireSequence(Sequence.SHUTOWN_POST_EVENT_UNREGISTRATION);
    }

    private void fireSequence(Sequence seq) {
        List<LyfecycleLevel> levels = LyfecycleLevel.getLevelsForSequence(seq);
        for (LyfecycleLevel cur : levels) {
            doFireStage(cur);
        }
    }

    private void doFireStage(LyfecycleLevel level) {
        log.info("Killbill lifecycle firing stage {}", level);
        Set<LifecycleHandler<? extends IService>> handlers = handlersByLevel.get(level);
        for (LifecycleHandler<? extends IService> cur : handlers) {

            try {
                Method method = cur.getMethod();
                IService target = cur.getTarget();
                log.info("Killbill lifecycle calling handler {} for service {}", cur.getMethod().getName(), target.getName());
                method.invoke(target);
            } catch (Exception e) {
                logWarn("Killbill lifecycle failed to invoke lifecycle handler", e);
            }
        }

    }


    private Set<? extends IService> findServices() {

        Set<IService> result = new HashSet<IService>();
        Set<Class<? extends IService>> services =  serviceFinder.getServices();
        for (Class<? extends IService> cur : services) {
            log.debug("Found service {}", cur.getName());
            try {
                IService instance = injector.getInstance(cur);
                log.debug("got instance {}", instance.getName());
                result.add(instance);
            } catch (Exception e) {
                logWarn("Failed to inject " + cur.getName(), e);
            }

        }
        return result;
    }


    // Used to disable valid injection failure from unit tests
    protected void logWarn(String msg, Exception e) {
        log.warn(msg, e);
    }

    public Multimap<LyfecycleLevel, LifecycleHandler<? extends IService>> findAllHandlers(IService service) {
        Multimap<LyfecycleLevel, LifecycleHandler<? extends IService>> methodsInService = HashMultimap.create();
        Class<? extends IService> clazz = service.getClass();
        for (Method method : clazz.getMethods()) {
            LyfecycleHandlerType annotation = method.getAnnotation(LyfecycleHandlerType.class);
            if (annotation != null) {
                LyfecycleLevel level = annotation.value();
                LifecycleHandler<? extends IService> handler = new  LifecycleHandler<IService>(service, method);
                methodsInService.put(level, handler);
            }
        }
        return methodsInService;
    }


    private final class LifecycleHandler<T> {
        private final T target;
        private final Method method;

        public LifecycleHandler(T target, Method method) {
            this.target = target;
            this.method = method;
        }

        public T getTarget() {
            return target;
        }

        public Method getMethod() {
            return method;
        }
    }
}
