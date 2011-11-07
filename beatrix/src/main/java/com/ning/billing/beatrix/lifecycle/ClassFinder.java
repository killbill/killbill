package com.ning.billing.beatrix.lifecycle;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassFinder extends ClassList {

    private final Logger log = LoggerFactory.getLogger(ClassFinder.class);

    /*
    private static final Set<String> PACKAGE_FILTER = new ImmutableSet.Builder<String>()
																.add("ning.viking.core")
																.add("ning.viking.clients")
																.build();

	private static final Class<?> SUBSYSTEMS = Subsystem.class;
    private static final Class<?> PLATFROM_ACCESSORS = PlatformAccessor.class;
    private static final Class<?> DATA_FEED = DataFeed.class;
    private static final Class<?> DATA_ACCESS = DataAccess.class;
*/

	private final ClassLoader loader;
	private final Map<String, Set<Class<?>>> map;

	/*
	private static final Class<?> [] INTERFACES_WE_CARE = {
		SUBSYSTEMS,
		PLATFROM_ACCESSORS,
		DATA_FEED,
		DATA_ACCESS
	};
	*/

	public ClassFinder(ClassLoader loader) {
		this.loader = loader;
		this.map = initialize();
		Iterator<String> it = map.keySet().iterator();
		while (it.hasNext()) {
			String key = it.next();
			log.info("Found classes - " + key + " : " + map.get(key));
		}
	}


	@SuppressWarnings("unchecked")
	public List<Class<? extends LifecycleService>> getServices() {
		List<Class<? extends LifecycleService>> res = new ArrayList<Class<? extends LifecycleService>>();
		for (Class clz : map.get(LifecycleService.class.getName())) {
			res.add(clz);
		}
		return res;
	}


	private Map<String, Set<Class<?>>> initialize() {
		try {

		    Set<String> packageFilter = new TreeSet<String>();
		    packageFilter.add("com.ning.billing.beatrix.lifecycle");

			Set<String> interfaceFilter = new TreeSet<String>();
			interfaceFilter.add(LifecycleService.class.getName());

			return findClasses(loader, interfaceFilter, packageFilter, null, true);
		} catch (ClassNotFoundException nfe) {
			throw new RuntimeException("Failed to initialize ClassFinder", nfe);
		}
	}


}
