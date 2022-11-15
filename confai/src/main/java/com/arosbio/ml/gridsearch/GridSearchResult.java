/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch;

import java.util.List;
import java.util.Objects;

import com.arosbio.ml.gridsearch.GridSearch.EvalStatus;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.metrics.SingleValuedMetric;

/**
 * {@code GridSearchResult} holds the result of a Grid Search in the parameter space of hyperparameters (e.g. Cost and Gamma values). 
 * 
 * If there was a warning during parameter search (in case an optimal value was found on an edge of the parameter grid)
 * that can be checked using the {@link #hasWarning()} method and the message retrieved using {@link #getWarning()}
 * 
 * @author Aros Bio AB
 *
 */
public class GridSearchResult {
	
	/**
	 * Should never be {@code null} or empty
	 */
	private final List<GSResult> bestParameters;
	/**
	 * Should never be {@code null}
	 */
	private final SingleValuedMetric optimizationMetric; 
	/**
	 * May be {@code null} when no error message was given
	 */
	private final String optionalWarning;
	
	
	private GridSearchResult(Builder b){
		this.bestParameters = b.bestParameters;
		this.optimizationMetric=b.optimizationMetric;
		this.optionalWarning = b.optionalWarning;
	}

	public static class Builder {
		private List<GSResult> bestParameters;
		private SingleValuedMetric optimizationMetric; 
		private String optionalWarning;

		public Builder(List<GSResult> results, SingleValuedMetric optMetric){
			this.bestParameters = Objects.requireNonNull(results);
			if (results.isEmpty())
				throw new IllegalArgumentException("Grid search results cannot be null");
			this.optimizationMetric = Objects.requireNonNull(optMetric);
		}
		public Builder warning(String msg){
			this.optionalWarning = msg;
			return this;
		}
		public GridSearchResult build(){
			return new GridSearchResult(this);
		}
	}
	
	
	public SingleValuedMetric getOptimizationType() {
		return optimizationMetric;
	}
	
	public int getNumGSResults() {
		return bestParameters.size();
	}
	
	public List<GSResult> getBestParameters() {
		return bestParameters;
	}
	
	public boolean getHasValidParamCombo() {
		if (bestParameters == null || bestParameters.isEmpty())
			return false;
		return bestParameters.get(0).getStatus() == EvalStatus.VALID;
	}

	/**
	 * Returns the warning if any, {@code null} otherwise
	 * @return a warning or {@code null}
	 */
	public String getWarning() {
		return optionalWarning;
	}

	/**
	 * Check if there was any warning issued during grid search
	 * @return {@code true} if there was any warning, {@code false} otherwise
	 */
	public boolean hasWarning() {
		return optionalWarning != null && !optionalWarning.isEmpty();
	}

	public String toString(){
		StringBuilder sb = new StringBuilder("GridSearchResult for ");
		sb.append(optimizationMetric.getName());
		sb.append(" {best value: "); sb.append(bestParameters.get(0).getResult());
		sb.append(", optimalParameters: "); 
		sb.append(bestParameters.get(0).getParams());
		
		if (optionalWarning != null) {
			sb.append(", warning: "); sb.append(optionalWarning);
		}
		sb.append('}');
		return sb.toString();
	}
	
	
}
