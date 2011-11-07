package com.ning.billing.beatrix.lifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LyfecycleHandlerType {


    public enum LyfecycleLevel {
        LOAD_CATALOG,
        INIT_BUS,
        REGISTER_EVENTS,
        START_SERVICE,
        STOP_SERVICE,
        UNREGISTER_EVENTS,
        SHUTDOWN
    }

    public LyfecycleLevel value();
}
