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
package com.ning.billing.server.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import com.ning.billing.server.listeners.KillbillGuiceListener;

public class ServerUtil {


	// Look a lot like the ServiceFinder for Lifecycle in util; mught want to refactor that at some point.
	public static Set<Class<?>> getKillbillConfig(final String packageFilter, final String jarFilter, final String interfaceFilter) throws ClassNotFoundException {
		Object[] classPaths;

		final Set<Class<?>> result = new HashSet<Class<?>>();

		ClassLoader classLoader = KillbillGuiceListener.class.getClassLoader();
		try {
			classPaths = ((java.net.URLClassLoader) classLoader).getURLs();
		} catch(ClassCastException cce){
			classPaths = System.getProperty("java.class.path", "").split(File.pathSeparator);
		}
		Enumeration<?> files = null;
		for (int h = 0; h < classPaths.length; h++) {

			JarFile module = null;
			File classPath = new File( (URL.class).isInstance(classPaths[h]) ?
					((URL)classPaths[h]).getFile() : classPaths[h].toString());

			if (classPath.isDirectory()) {


				List<String> dirListing = new ArrayList<String>();
				recursivelyListDir(dirListing, classPath, new StringBuffer() );
				files = Collections.enumeration(dirListing);

			} else if (classPath.getName().endsWith(".jar")) {



				String [] jarParts = classPath.getName().split("/");
				String jarName = jarParts[jarParts.length - 1];

				System.out.println(jarName);
				if (jarFilter != null && jarName != null && ! jarName.startsWith(jarFilter)) {
					continue;
				}
				try {
					module = new JarFile(classPath);

					files = module.entries();

				} catch (MalformedURLException mue){
					throw new ClassNotFoundException("Bad classpath. Error: " + mue.getMessage());
				} catch (IOException io){
					throw new ClassNotFoundException("jar file '" + classPath.getName() +
							"' could not be instantiate from file path. Error: " + io.getMessage());
				}
			}

			while ( files != null && files.hasMoreElements() ){
				String fileName = files.nextElement().toString();

				if (fileName.endsWith(".class")){
					String className = fileName.replaceAll("/", ".").substring(0, fileName.length() - 6);
					if (packageFilter != null) {
						if (!className.startsWith(packageFilter)) {
							continue;
						}
					}
					Class<?> theClass = null;
					try {
						theClass = Class.forName(className, false, classLoader);
						Class<?> [] classInterfaces = getInterfaces(theClass);
						for (int i = 0; i < classInterfaces.length; i++) {
							String interfaceName = classInterfaces[i].getName();
							if (!interfaceFilter.equals(interfaceName) ) {
								continue;
							}
							result.add((Class<?>) theClass);
						}
					} catch (NoClassDefFoundError e) {
						continue;
					}
				}
			}
			if (module != null) {
				try {
					module.close();
				} catch (IOException e) {

				}
			} 
		}
		return result;
	}
	
	private static void recursivelyListDir(List<String> dirListing, File dir, StringBuffer relativePath){
		int prevLen;
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			for(int i = 0; i < files.length; i++){
				prevLen = relativePath.length();
				recursivelyListDir(dirListing, files[i],
						relativePath.append(prevLen == 0 ? "" : "/" ).append( files[i].getName()));
				relativePath.delete(prevLen, relativePath.length());
			}
		} else {
			dirListing.add(relativePath.toString());
		}
	}

	private static Class<?> [] getInterfaces(Class<?> theClass) {
		Class<?> [] classInterfaces = theClass.getInterfaces();
		return classInterfaces;
	}
}
