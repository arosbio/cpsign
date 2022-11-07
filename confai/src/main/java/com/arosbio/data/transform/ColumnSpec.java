/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.google.common.collect.BoundType;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

public class ColumnSpec implements Serializable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ColumnSpec.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = -3393641608500286911L;
	
	private List<Integer> columns;
	private Range<Integer> columnsRange;

	private ColumnSpec() {}

	public ColumnSpec(int... cols) {
		this(CollectionUtils.arrToList(cols),false);
	}
	
	public ColumnSpec(List<Integer> columns) {
		this(columns,false);
	}

	public ColumnSpec(Collection<Integer> columns) {
		this(new ArrayList<>(columns),false);
	}

	public ColumnSpec(Range<Integer> columnsRange) {
		this.columnsRange = columnsRange;
	}
	
	private ColumnSpec(List<Integer> columns, boolean sorted) {
		if (! sorted) {
			Collections.sort(columns);
		}
		if (!columns.isEmpty()) {
			if (columns.get(0) < 0) {
				throw new IndexOutOfBoundsException("Column indices must not be smaller than 0");
			}
		}
		this.columns = columns;
	}
	
	public static ColumnSpec allColumns() {
		return new ColumnSpec();
	}

	public boolean useAll() {
		if (columns==null && columnsRange==null) {
			return true;
		} else if (columnsRange != null) {
			// If we don't have either a lower or upper bound
			if (!columnsRange.hasLowerBound() && ! columnsRange.hasUpperBound()) {
				return true;
			} 
			// If no upper bound, but the lower bound is lower than or equal to 1
			else if (! columnsRange.hasUpperBound() && (columnsRange.hasLowerBound() && columnsRange.lowerEndpoint() <=0)) {
				return true;
			}
		}
		return false;
	}

	public String toString() {
		if (useAll())
			return "all features";
		if (columns != null)
			return ""+columns.size() + " specified features";
		return "feature indices " + columnsRange;
	}
	
	public int getNumColumns(int maxCol) {
		if (useAll()) {
			return maxCol+1;
		} else if (columns != null) {
			return columns.size();
		} else if (columnsRange != null) {
			
			Range<Integer> r = columnsRange;
			
			int start = 0, stop = maxCol;
			if (columnsRange.hasLowerBound()) {
				start = r.lowerEndpoint() + (r.lowerBoundType() == BoundType.CLOSED ? 0 : 1);
			}
			if (columnsRange.hasUpperBound()) {
				int tmpStop = r.upperEndpoint() + (r.upperBoundType() == BoundType.CLOSED ? 0 : -1);
				stop = Math.min(tmpStop, stop);
			}
			
			return stop - start + 1; 
		} else {
			throw new IllegalArgumentException("Column spec is invalid");
		}
	}

	/**
	 * Get the full list of indices, verifies indices using the <code>maxCol</code> argument.
	 * @param maxCol The maximum feature index, used for capping SparseVectors
	 * @return A {@link Set} of indices that this ColumnSpec corresponds to
	 * @throws IndexOutOfBoundsException In case the ColumnSpec is invalid, e.g. includes negative indices or indices larger than <code>maxCol</code>
	 */
	public List<Integer> getColumns(int maxCol) throws IndexOutOfBoundsException {
		List<Integer> setToVerify = null;
		
		if (useAll()) {
			// Return all possible columns
			return StreamSupport.stream(
	                Spliterators.spliteratorUnknownSize(
	                		ContiguousSet.create(Range.closed(0, maxCol), DiscreteDomain.integers()).iterator(),
	                		Spliterator.ORDERED), false)
					.collect(Collectors.toList());
		} else if (columns != null) {
			setToVerify = new ArrayList<>(columns);
		} else if (columnsRange != null) {
			
			Range<Integer> r = columnsRange; 
			
			// Handle lower endpoint
			if (!r.hasLowerBound()) {
				r = r.intersection(Range.atLeast(0));
			} else if ( r.lowerEndpoint() < 0 && r.lowerBoundType() == BoundType.CLOSED) {
				throw new IndexOutOfBoundsException("Range "+ columnsRange + " not a valid range of features, must be [0.."+maxCol+ "] at most");
			} else if (r.lowerEndpoint() < -1)
				throw new IndexOutOfBoundsException("Range "+ columnsRange + " not a valid range of features, must be [0.."+maxCol+ "] at most");
			
			// Handle upper endpoint
			if (!r.hasUpperBound()) {
				r = r.intersection(Range.atMost(maxCol));
			} else if (r.upperEndpoint()> maxCol) {
				r = r.intersection(Range.atMost(maxCol));
			}
			
			// Debug the range that will be enumerated - in case some memory issues arise 
			LOGGER.debug("Enumerating full range of columns based on range {}",r);
			
			setToVerify = StreamSupport.stream(
	                Spliterators.spliteratorUnknownSize(
	                		ContiguousSet.create(r, DiscreteDomain.integers()).iterator(),
	                		Spliterator.ORDERED), false)
					.collect(Collectors.toList());
			
			// No need to check this case - we've set the limits correctly
			return setToVerify;
			
		} else {
			throw new IllegalArgumentException("Invalid column-specification chosen for Transformer");
		}
		
		// Verify the sets generated above
		SummaryStatistics stats = CollectionUtils.getStatistics(setToVerify);
		if (stats.getMin() < 0)
			throw new IndexOutOfBoundsException("Minimum index must be >=0");
		if (stats.getMax() > maxCol)
			throw new IndexOutOfBoundsException("Maximum index larger than maximum in the dataset: " + stats.getMax());
		
		return setToVerify;
		
	}
	
	public boolean isRangeBased() {
		return columnsRange != null;
	}
	
	public Range<Integer> getRange(){
		return columnsRange;
	}
	
	public boolean isListBased() {
		return columns != null;
	}
	
	public List<Integer> getColumnsList(){
		return columns;
	}

	public ColumnSpec clone() {
		ColumnSpec clone = new ColumnSpec();
		if (columns != null)
			clone.columns = new ArrayList<>(columns);
		if (columnsRange != null)
			clone.columnsRange = CollectionUtils.clone(columnsRange);
		return clone;
	}
	
	public boolean equals(Object o) {
		if (! (o instanceof ColumnSpec)) return false;
		
		ColumnSpec other = (ColumnSpec) o;
		
		if (columns != null) {
			if (other.columns == null)
				return false;
			return columns.equals(other.columns);
		}
		if (columnsRange != null) {
			if (other.columnsRange == null)
				return false;
			return columnsRange.equals(other.columnsRange);
		}
		
		return other.columns == null && other.columnsRange == null;
		
	}

}
