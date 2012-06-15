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

package com.ning.billing.mock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrainDeadProxyFactory {
    private static final Logger log = LoggerFactory.getLogger(BrainDeadProxyFactory.class);

    public static final Object ZOMBIE_VOID = new Object();

    public static interface ZombieControl {

        public ZombieControl addResult(String method, Object result);

        public ZombieControl clearResults();

    }

    @SuppressWarnings("unchecked")
    public static <T> T createBrainDeadProxyFor(final Class<T> clazz, final Class<?>... others) {
        final Class<?>[] clazzes = Arrays.copyOf(others, others.length + 2);
        clazzes[others.length] = ZombieControl.class;
        clazzes[others.length + 1] = clazz;
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(),
                                          clazzes,
                                          new InvocationHandler() {
                                              private final Map<String, Object> results = new HashMap<String, Object>();

                                              @Override
                                              public Object invoke(final Object proxy, final Method method, final Object[] args)
                                                      throws Throwable {

                                                  if (method.getDeclaringClass().equals(ZombieControl.class)) {
                                                      if (method.getName().equals("addResult")) {
                                                          results.put((String) args[0], args[1]);
                                                          return proxy;
                                                      } else if (method.getName().equals("clearResults")) {
                                                          results.clear();
                                                          return proxy;
                                                      }

                                                  } else {

                                                      final Object result = results.get(method.getName());
                                                      if (result == ZOMBIE_VOID) {
                                                          return (Void) null;
                                                      } else if (result != null) {
                                                          if (result instanceof Throwable) {
                                                              throw ((Throwable) result);
                                                          }
                                                          return result;
                                                      } else if (method.getName().equals("equals")) {
                                                          return proxy == args[0];
                                                      } else {
                                                          log.error(String.format("No result for Method: '%s' on Class '%s'", method.getName(), method.getDeclaringClass().getName()));
                                                          throw new UnsupportedOperationException();
                                                      }
                                                  }
                                                  return null;
                                              }
                                          });
    }
}
