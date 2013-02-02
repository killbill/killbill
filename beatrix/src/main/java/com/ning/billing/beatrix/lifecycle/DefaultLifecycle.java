/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.lifecycle.LifecycleHandlerType;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel;
import com.ning.billing.lifecycle.LifecycleHandlerType.LifecycleLevel.Sequence;

import com.google.common.base.Supplier;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;
import com.google.inject.Injector;


public class DefaultLifecycle implements Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(DefaultLifecycle.class);
    private final SetMultimap<LifecycleLevel, LifecycleHandler<? extends KillbillService>> handlersByLevel;

    private final ServiceFinder serviceFinder;

    protected final Injector injector;

    @Inject
    public DefaultLifecycle(final Injector injector) {

        this.serviceFinder = new ServiceFinder(DefaultLifecycle.class.getClassLoader());
        this.handlersByLevel = Multimaps.newSetMultimap(new ConcurrentHashMap<LifecycleLevel, Collection<LifecycleHandler<? extends KillbillService>>>(),

                                                        new Supplier<Set<LifecycleHandler<? extends KillbillService>>>() {
                                                            @Override
                                                            public Set<LifecycleHandler<? extends KillbillService>> get() {
                                                                return new CopyOnWriteArraySet<LifecycleHandler<? extends KillbillService>>();
                                                            }
                                                        });
        this.injector = injector;

        init();
    }


    @Override
    public void fireStartupSequencePriorEventRegistration() {
        fireSequence(Sequence.STARTUP_PRE_EVENT_REGISTRATION);
    }

    @Override
    public void fireStartupSequencePostEventRegistration() {
        fireSequence(Sequence.STARTUP_POST_EVENT_REGISTRATION);
    }

    @Override
    public void fireShutdownSequencePriorEventUnRegistration() {
        fireSequence(Sequence.SHUTDOWN_PRE_EVENT_UNREGISTRATION);
    }

    @Override
    public void fireShutdownSequencePostEventUnRegistration() {
        fireSequence(Sequence.SHUTDOWN_POST_EVENT_UNREGISTRATION);
    }

    protected Set<? extends KillbillService> findServices() {

        final Set<KillbillService> result = new HashSet<KillbillService>();
        final Set<Class<? extends KillbillService>> services = serviceFinder.getServices();
        for (final Class<? extends KillbillService> cur : services) {
            log.debug("Found service {}", cur.getName());
            try {
                final KillbillService instance = injector.getInstance(cur);
                log.debug("got instance {}", instance.getName());
                result.add(instance);
            } catch (final Exception e) {
                logWarn("Failed to inject " + cur.getName(), e);
            }

        }
        return result;
    }

    private void init() {
        final Set<? extends KillbillService> services = findServices();
        final Iterator<? extends KillbillService> it = services.iterator();
        while (it.hasNext()) {
            handlersByLevel.putAll(findAllHandlers(it.next()));
        }
    }

    private void fireSequence(final Sequence seq) {
        final List<LifecycleLevel> levels = LifecycleLevel.getLevelsForSequence(seq);
        for (final LifecycleLevel cur : levels) {
            doFireStage(cur);
        }
    }

    private void doFireStage(final LifecycleLevel level) {
        log.info("Killbill lifecycle firing stage {}", level);
        final Set<LifecycleHandler<? extends KillbillService>> handlers = handlersByLevel.get(level);
        for (final LifecycleHandler<? extends KillbillService> cur : handlers) {

            try {
                final Method method = cur.getMethod();
                final KillbillService target = cur.getTarget();
                log.info("Killbill lifecycle calling handler {} for service {}", cur.getMethod().getName(), target.getName());
                method.invoke(target);
            } catch (final Exception e) {
                logWarn("Killbill lifecycle failed to invoke lifecycle handler", e);
            }
        }

    }


    // Used to disable valid injection failure from unit tests
    protected void logWarn(final String msg, final Exception e) {
        log.warn(msg);
    }

    private Multimap<LifecycleLevel, LifecycleHandler<? extends KillbillService>> findAllHandlers(final KillbillService service) {
        final Multimap<LifecycleLevel, LifecycleHandler<? extends KillbillService>> methodsInService = HashMultimap.create();
        final Class<? extends KillbillService> clazz = service.getClass();
        for (final Method method : clazz.getMethods()) {
            final LifecycleHandlerType annotation = method.getAnnotation(LifecycleHandlerType.class);
            if (annotation != null) {
                final LifecycleLevel level = annotation.value();
                final LifecycleHandler<? extends KillbillService> handler = new LifecycleHandler<KillbillService>(service, method);
                methodsInService.put(level, handler);
            }
        }
        return methodsInService;
    }

    private final class LifecycleHandler<T> {
        private final T target;
        private final Method method;

        public LifecycleHandler(final T target, final Method method) {
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
