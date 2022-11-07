/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.Configurable;
import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.arosbio.commons.config.Configurable.Sorter.Direction;
import com.arosbio.commons.config.Configurable.Sorter.Priority;
import com.arosbio.ml.gridsearch.GridSearch.EvalStatus;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;

public class GSResComparator implements Comparator<GSResult> {

	static final Logger LOGGER = LoggerFactory.getLogger(GSResComparator.class);

	// when initialized
	private final List<ConfigParameter> params;
	// computed when needed for the first time
	private boolean setupComplete = false;
	private List<Pair<Sorter, String>> sorting;

	public GSResComparator(Configurable obj) {
		params = obj.getConfigParameters();
	}

	public GSResComparator(List<ConfigParameter> confs){
		this.params = confs;
	}

	/**
	 * Internal testing only
	 * @return the found sorting or {@code null} if not forced to be calculated yet
	 */
	public List<Pair<Sorter, String>> getSortingList() {
		return sorting;
	}

	@Override
	public int compare(GSResult res1, GSResult res2) {

		// First check status
		if (res1.getStatus() != EvalStatus.VALID && res2.getStatus() != EvalStatus.VALID) {
			return backupRuntimeSort(res1, res2);
		} else if (res1.getStatus() != EvalStatus.VALID) {
			return 1;
		} else if (res2.getStatus() != EvalStatus.VALID) {
			return -1;
		}

		// Primarily compare based on the result!
		int cmp = Double.compare(res1.getResult(), res2.getResult());
		if (cmp != 0) {
			return (res1.getOptimizationMetric().goalIsMinimization() ? 1 : -1) * cmp;
		}
		// Go through the parameters in the setup 
		if (!setupComplete) {
			try {
				doSetup(res1.getParams());
			} catch (Exception e) {
				LOGGER.debug("Failed setting up a sorting of parameter values - will fallback to sorting by runtime");
			} finally {
				setupComplete = true; // Simply to not have to do it multiple times
			}
		}

		// Secondarily need to lookup if there are any sortings to adhere to
		LOGGER.debug("Comparing two grid-results based on parameter-sorting instead of results");

		// if none had any sorting:
		if (sorting == null || sorting.isEmpty()) {
			// Backup - pick the one with fastest runtime
			return backupRuntimeSort(res1, res2);
		}

		try {

			for (Pair<Sorter, String> p : sorting) {
				Object p1 = res1.getParams().get(p.getRight());
				Object p2 = res2.getParams().get(p.getRight());
				int cmp2 = 0;
				if (p.getLeft().hasComparator()) {
					// Compare using custom comparator when available
					cmp2 = p.getLeft().getComparator().compare(p1, p2);
				} else if (TypeUtils.isInt(p1) && TypeUtils.isInt(p2)) {
					cmp2 = Integer.compare(TypeUtils.asInt(p1), TypeUtils.asInt(p2));
				} else if (TypeUtils.isDouble(p1) || TypeUtils.isDouble(p2)) {
					cmp2 = Double.compare(TypeUtils.asDouble(p1), TypeUtils.asDouble(p2));
				} 
				// else if (p1 instanceof Enum && p2 instanceof Enum && p1.getClass() == p2.getClass()) {
				// 	// If enums of the same type
				// 	cmp2 = ((Enum) p1).compareTo((Enum) p2);
				// }

				if (cmp2 != 0) {
					// reverse if should prefer lower
					return p.getLeft().getDirection() == Direction.PREFER_LOWER ? cmp2 : -cmp2;
				}
				LOGGER.debug("Failed comparing parameter {} with priority {} - continuing to next", p.getRight(),
						p.getLeft().getPrio());
			}

		} catch (Exception e) {
			LOGGER.error("Failed sorting grid results due to exception, falling back to runtime-sorting", e);
		}
		return backupRuntimeSort(res1, res2);

	}

	private static int backupRuntimeSort(GSResult r1, GSResult r2) {
		return Long.compare(r1.getRuntime(), r2.getRuntime());
	}

	private void doSetup(Map<String, Object> pMap) {
		LOGGER.debug("Peforming setup of parameter ranking");
		sorting = new ArrayList<>();

		// Go through all parameters to find the ones that have an explicit sorting
		for (ConfigParameter p : params) {
			// Skip if prio is None
			if (p.getSorting().getPrio() == Priority.NONE) {
				continue;
			}
			// Skip if direction is None
			if (p.getSorting().getDirection() == Direction.NONE) {
				continue;
			}

			for (String confName : p.getNames()) {
				for (String usedName : pMap.keySet()) {
					if (confName.equalsIgnoreCase(usedName)) {
						sorting.add(Pair.of(p.getSorting(), usedName));
						break;
					}
				}
			}

		}
		Collections.sort(sorting, new Comparator<Pair<Sorter, ?>>() {
			@Override
			public int compare(Pair<Sorter, ?> o1, Pair<Sorter, ?> o2) {
				// Sort in reverse of the prio - to but highest prio first
				return - o1.getLeft().getPrio().compareTo(o2.getLeft().getPrio());
			}
		});
		// Collections.reverse(sorting); // 

		LOGGER.debug("Finished createing a custom sorting");
	}

}