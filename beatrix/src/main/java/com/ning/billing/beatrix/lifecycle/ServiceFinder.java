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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.lifecycle.KillbillService;

public class ServiceFinder {

    private static final Logger log = LoggerFactory.getLogger(ServiceFinder.class);

    private final ClassLoader loader;
    private final Set<Class<? extends KillbillService>> servicesTypes;

    public ServiceFinder(final ClassLoader loader) {
        this.loader = loader;
        this.servicesTypes = initialize();
        final Iterator<Class<? extends KillbillService>> it = servicesTypes.iterator();
        while (it.hasNext()) {
            final Class<? extends KillbillService> svc = it.next();
            log.debug("Found IService classes {}", svc.getName());
        }
    }

    public Set<Class<? extends KillbillService>> getServices() {
        return servicesTypes;
    }

    private Set<Class<? extends KillbillService>> initialize() {
        try {

            final Set<String> packageFilter = new HashSet<String>();
            packageFilter.add("com.ning.billing");
            final String jarFilter = "killbill";
            return findClasses(loader, KillbillService.class.getName(), jarFilter, packageFilter);
        } catch (ClassNotFoundException nfe) {
            throw new RuntimeException("Failed to initialize ClassFinder", nfe);
        }
    }

    /*
     *  Code originally from Kris Dover <krisdover@hotmail.com> and adapted for my purpose.
     *
     */
    private static Set<Class<? extends KillbillService>> findClasses(final ClassLoader classLoader,
                                                                     final String interfaceFilter,
                                                                     final String jarFilter,
                                                                     final Set<String> packageFilter)
            throws ClassNotFoundException {

        final Set<Class<? extends KillbillService>> result = new HashSet<Class<? extends KillbillService>>();

        Object[] classPaths;
        try {
            classPaths = ((java.net.URLClassLoader) classLoader).getURLs();
        } catch (ClassCastException cce) {
            classPaths = System.getProperty("java.class.path", "").split(File.pathSeparator);
        }

        for (int h = 0; h < classPaths.length; h++) {
            Enumeration<?> files = null;
            JarFile module = null;

            final String protocol;
            final File classPath;
            if ((URL.class).isInstance(classPaths[h])) {
                final URL urlClassPath = (URL) classPaths[h];
                classPath = new File(urlClassPath.getFile());
                protocol = urlClassPath.getProtocol();
            } else {
                classPath = new File(classPaths[h].toString());
                protocol = "file";
            }

            // Check if the protocol is "file". For example, if classPath is http://felix.extensions:9/,
            // the file will be "/" and we don't want to scan the full filesystem
            if ("file".equals(protocol) && classPath.isDirectory()) {
                log.debug("DIR : " + classPath);

                final List<String> dirListing = new ArrayList<String>();
                recursivelyListDir(dirListing, classPath, new StringBuffer());
                files = Collections.enumeration(dirListing);
            } else if (classPath.getName().endsWith(".jar")) {

                log.debug("JAR : " + classPath);

                final String[] jarParts = classPath.getName().split("/");
                final String jarName = jarParts[jarParts.length - 1];
                if (jarFilter != null && jarName != null && !jarName.startsWith(jarFilter)) {
                    continue;
                }
                boolean failed = true;
                try {
                    module = new JarFile(classPath);
                    failed = false;
                } catch (MalformedURLException mue) {
                    throw new ClassNotFoundException("Bad classpath. Error: " + mue.getMessage());
                } catch (IOException io) {
                    throw new ClassNotFoundException("jar file '" + classPath.getName() +
                                                             "' could not be instantiate from file path. Error: " + io.getMessage());
                }
                if (!failed) {
                    files = module.entries();
                }
            }

            while (files != null && files.hasMoreElements()) {
                final String fileName = files.nextElement().toString();

                if (fileName.endsWith(".class")) {
                    final String className = fileName.replaceAll("/", ".").substring(0, fileName.length() - 6);
                    if (packageFilter != null) {
                        boolean skip = true;
                        final Iterator<String> it = packageFilter.iterator();
                        while (it.hasNext()) {
                            final String filter = it.next() + ".";
                            if (className.startsWith(filter)) {
                                skip = false;
                                break;
                            }
                        }
                        if (skip) {
                            continue;
                        }
                    }
                    Class<?> theClass = null;
                    try {
                        theClass = Class.forName(className, false, classLoader);
                    } catch (NoClassDefFoundError e) {
                        continue;
                    }
                    if (!theClass.isInterface()) {
                        continue;
                    }
                    final Class<?>[] classInterfaces = getAllInterfaces(theClass);
                    String interfaceName = null;
                    for (int i = 0; i < classInterfaces.length; i++) {

                        interfaceName = classInterfaces[i].getName();
                        if (!interfaceFilter.equals(interfaceName)) {
                            continue;
                        }
                        result.add((Class<? extends KillbillService>) theClass);
                        break;
                    }

                }
            }
            if (module != null) {
                try {
                    module.close();
                } catch (IOException ioe) {
                    throw new ClassNotFoundException("The module jar file '" + classPath.getName() +
                                                             "' could not be closed. Error: " + ioe.getMessage());
                }
            }
        }
        return result;
    }

    private static Class<?>[] getAllInterfaces(final Class<?> theClass) {
        final Set<Class<?>> superInterfaces = new HashSet<Class<?>>();
        final Class<?>[] classInterfaces = theClass.getInterfaces();
        for (final Class<?> cur : classInterfaces) {
            getSuperInterfaces(superInterfaces, cur);
        }
        return superInterfaces.toArray(new Class<?>[superInterfaces.size()]);
    }

    private static void getSuperInterfaces(final Set<Class<?>> superInterfaces, final Class<?> theInterface) {

        superInterfaces.add(theInterface);
        final Class<?>[] classInterfaces = theInterface.getInterfaces();
        for (final Class<?> cur : classInterfaces) {
            getSuperInterfaces(superInterfaces, cur);
        }
    }

    private static void recursivelyListDir(final List<String> dirListing, final File dir, final StringBuffer relativePath) {
        int prevLen;
        if (dir.isDirectory()) {
            final File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (final File file : files) {
                prevLen = relativePath.length();
                recursivelyListDir(dirListing, file,
                                   relativePath.append(prevLen == 0 ? "" : "/").append(file.getName()));
                relativePath.delete(prevLen, relativePath.length());
            }
        } else {
            dirListing.add(relativePath.toString());
        }
    }
}
