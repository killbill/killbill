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
import com.ning.billing.beatrix.lifecycle.LyfecycleHandlerType.LyfecycleLevel;


public class Lifecycle {

    private final static Logger log = LoggerFactory.getLogger(Lifecycle.class);


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

    private final SetMultimap<LyfecycleLevel, LifecycleHandler> handlersByLevel;

    private final Injector injector;

    @Inject
    public Lifecycle(Injector injector) {
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
        List<? extends LifecycleService> services = findServices();
        Iterator<? extends LifecycleService> it = services.iterator();
        while (it.hasNext()) {
        //for (<? extends LifecycleService> cur : services) {
            handlersByLevel.putAll(findAllHandlers(it.next()));
        }
    }



    public void fireStages() {
        for (LyfecycleLevel level : LyfecycleLevel.values()) {
            log.info("Firing stage {}", level);
            Set<LifecycleHandler> handlers = handlersByLevel.get(level);
            for (LifecycleHandler cur : handlers) {
                log.info("Calling handler {}", cur.getMethod().getName());
                try {
                    Method method = cur.getMethod();
                    Object target = cur.getTarget();
                    method.invoke(target);
                } catch (Exception e) {
                    log.warn("Faikled to invoke lifecycle handler", e);
                }

            }
        }
    }

    private List<? extends LifecycleService> findServices() {

        List<LifecycleService> result = new LinkedList<LifecycleService>();

        ClassFinder classFinder = new ClassFinder(Lifecycle.class.getClassLoader());
        List<Class<? extends LifecycleService>> services =  classFinder.getServices();
        for (Class<? extends LifecycleService> cur : services) {
            log.info("Found service {}", cur);
            result.add(injector.getInstance(cur));
        }
        return result;
    }


    public Multimap<LyfecycleLevel, LifecycleHandler> findAllHandlers(Object service) {
        Multimap<LyfecycleLevel, LifecycleHandler> methodsInService =
            HashMultimap.create();
        Class clazz = service.getClass();
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


}
