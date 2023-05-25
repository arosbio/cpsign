/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import com.arosbio.commons.mixins.HasID;
import com.arosbio.commons.mixins.Named;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuzzyServiceLoader<S> implements Iterable<S>{


	private static final Logger LOGGER = LoggerFactory.getLogger(FuzzyServiceLoader.class);

	private ServiceLoader<S> loader;
	final private Class<S> serviceCls;

	public FuzzyServiceLoader(Class<S> service) {
		loader = ServiceLoader.load(service);
		serviceCls = service;
	}

	@Override
	public Iterator<S> iterator() {
		return loader.iterator();
	}

	public S load(String name) {
		try {
			int id = TypeUtils.asInt(name);

			// Has an ID
			if (TypeUtils.isOfType(serviceCls, HasID.class)) {
				S obj = loadUsingID(loader.iterator(), id);
				loader.reload();
				return obj;
			}

		} catch (Exception e) {
			LOGGER.debug("Could not load service using ID");
		}

		// Has names
		if (TypeUtils.isOfType(serviceCls, Named.class)) {
			S obj = loadFuzzy(loader.iterator(), name);
			loader.reload();
			return obj;
		} 

		throw new IllegalArgumentException("No " + serviceCls.getSimpleName() + " could be loaded using id: " + name);
	}


	public static <S> Iterator<S> iterator(final Class<S> service)
			throws IllegalArgumentException {
		return new FuzzyServiceLoader<S>(service).iterator(); 
	}

	public static <S> S load(final Class<S> service, String name) 
			throws IllegalArgumentException {
		return new FuzzyServiceLoader<S>(service).load(name);
	}

	public static <S> S load(final Class<S> service, int id) 
			throws IllegalArgumentException {
		ServiceLoader<S> loader = ServiceLoader.load(service);

		// If no ID
		if (! TypeUtils.isOfType(service, HasID.class)) {
			LOGGER.debug("Service does not implement the HasID type, cannot load using ID");
			throw new IllegalArgumentException(service.getSimpleName() + " doesn't implement the HasID interface, cannot load it using ID");
		} 

		// It has an ID, loop and find the correct one
		return loadUsingID(loader.iterator(), id);

	}


	/**
	 * Requires class/interface T to inherit the {@link HasID} 
	 * @param iter
	 * @param id
	 * @return
	 */
	private static <S> S loadUsingID(Iterator<S> iter, int id) {

		while (iter.hasNext()) {
			S next = iter.next();
			if (next instanceof HasID) {
				if (((HasID) next).getID() == id) {
					return next;
				}
			}
		}

		throw new IllegalArgumentException("No service found for ID: " + id); 

	}

	/**
	 * Requires the class/interface T to inherit the {@link Named} interface
	 * @param loader
	 * @param name
	 * @return
	 */
	private static <S> S loadFuzzy(Iterator<S> iter, String name) 
			throws IllegalArgumentException {

		List<Pair<List<String>,S>> available = new ArrayList<>();
		while (iter.hasNext()) {
			S next = iter.next();

			available.add(ImmutablePair.of(TypeUtils.getNames(next), next));
		}

		try {
			S service = new FuzzyMatcher().matchPairs(available, name);
			LOGGER.debug("Loaded implementation of type: {}", ((Named)service).getName());
			return service; 
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Service not found: " + e.getMessage());
		}
	}




}
