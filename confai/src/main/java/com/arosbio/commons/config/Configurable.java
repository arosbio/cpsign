/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.commons.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public interface Configurable {

	/**
	 * Sorting preference for a {@link ConfigParameter} for deciding 
	 * the prefered parameter in e.g. a grid-search for hyper-parameters.
	 * This can be done in order to e.g. pick values that are less prone to 
	 * overfitting.
	 */
	public static class Sorter {
		/**
		 * The priority of the given Sorting. Use {@link #NONE} in case 
		 * no sorting should be done, e.g. for non-hyperparameters of algorithms.
		 */
		public static enum Priority {
			NONE, LOW, MEDIUM, HIGH;
		}
		public static enum Direction {
			PREFER_LOWER, NONE, PREFER_HIGHER;
		}

		private final Priority prio;
		private final Direction direction;
		private final Comparator<Object> explicitSorter;

		public Sorter(Priority prio, Direction dir){
			this(prio, dir, null);
		}
		/**
		 * A sorter that allows for a custom implementation for how the {@link ConfigParameter} should be sorted. E.g. how Enums should be 
		 * sorted or anything else. 
		 * @param prio The priority for this {@link ConfigParameter} 
		 * @param dir The direction for the parameter
		 * @param comp A custom {@link java.util.Comparator} for how objects of this {@link ConfigParameter} should be sorted, not needed for e.g. Integer or Double values
		 */
		public Sorter(Priority prio, Direction dir, Comparator<Object> comp){
			if (prio==Priority.NONE && dir != Direction.NONE){
				throw new IllegalArgumentException("If priority is NONE, there cannot be a direction of the sorting");
			}
			this.prio = prio;
			this.direction = dir;
			this.explicitSorter = comp;
		}
		/**
		 * The default {@link Sorter} for all ConfigParameter implementatioons, which imposes no sorting 
		 * on the ConfigParameter.
		 * @return A {@link Sorter} with no sorting
		 */
		public static Sorter none(){
			return new Sorter(Priority.LOW, Direction.NONE);
		}

		public Priority getPrio(){
			return prio;
		}

		public Direction getDirection(){
			return direction;
		}

		public boolean hasComparator(){
			return this.explicitSorter != null;
		}

		public Comparator<Object> getComparator(){
			return explicitSorter;
		}

		public String toString(){
			if (prio == Priority.NONE){
				return "Sorting: None";
			}
			return String.format("Sorting, prio=%s direction=%s",prio,direction);
		}
	}
	
	/**
	 * ConfigParameter should be immutable with exclusively getter methods. 
	 * For convenience there's also the {@link #withNames(List)} method that should
	 * create a new instance with all the same settings but with the new names. 
	 */
	public interface ConfigParameter {
		/**
		 * Get the possible names for this parameter. Note that the names should be 
		 * case-insensitive so the underlying implementations must comply to that.
		 * @return A list of accepted names
		 */
		public List<String> getNames();
		/**
		 * Get a textual representation of the type, e.g. "integer" "number" etc. 
		 * Intended for the CLI users to see how parameters should be formatted
		 * @return textual representation of the type
		 */
		public String getType();
		/**
		 * In case there is any prefered sorting based on this config. e.g. smaller/larger
		 * values could reduce chance of overfit and thus should be prefered 
		 * @return {@link Sorter} for how to pick best value
		 */
		public Sorter getSorting();
		/**
		 * Get the default value, or potentially {@code null} 
		 * @return default value or {@code null} 
		 */
		public Object getDefault();
		/**
		 * In case this {@link ConfigParameter} is used in grid-search, optionally supply a
		 * list of values that should be included in the search space.
		 * @return A list of values or empty list
		 */
		public List<Object> getDefaultGrid();

		/**
		 * Create a new instance, having all the same settings as the current {@link ConfigParameter}
		 * but with different names
		 * @param names A list of new names
		 * @return A new {@link ConfigParameter} with the new names
		 */		
		public ConfigParameter withNames(List<String> names);
	}

	public List<ConfigParameter> getConfigParameters();
	
	/**
	 * 
	 * @param params Parameters to set
	 * @throws IllegalStateException In case the Configurable object is in a state that does not allow changes
	 * @throws IllegalArgumentException If the argument are of incorrect type or parameter names are not allowed
	 */
	public void setConfigParameters(Map<String,Object> params) 
			throws IllegalStateException, IllegalArgumentException;
	
	
	public static IllegalArgumentException getInvalidArgsExcept(String param, Object value) {
		return new IllegalArgumentException(String.format("Invalid argument for parameter %s: %s", param,value));
	}
}
