/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Range;

public class IntervalUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(IntervalUtils.class);


	public static Range<Double> getCappedInterval( 
			double y_hat, double distanceToMP, double minObs, double maxObs) {
		return capInterval(getInterval(y_hat, distanceToMP), minObs, maxObs);
	}

	public static Range<Double> capInterval(Range<Double> interval, 
			Range<Double> observedRange) {
		if (Double.isNaN(interval.lowerEndpoint())|| Double.isNaN(interval.upperEndpoint())) {
			LOGGER.debug("Lower and/or upper endpoints were NaN, capping to min and max-obs");
			return observedRange;
		}
		return interval.intersection(observedRange);
	}
	/**
	 * Calculate the interval for a given prediction, using the min and max ranges
	 * @param interval the non-capped interval
	 * @param minObs the minimum observed value
	 * @param maxObs the maximum observed value
	 * @return the capped Confidence Interval
	 */
	public static Range<Double> capInterval(Range<Double> interval, 
			double minObs, double maxObs) {

		if (Double.isNaN(interval.lowerEndpoint())|| Double.isNaN(interval.upperEndpoint())) {
			LOGGER.debug("Lower and/or upper endpoints were NaN, capping to min and max-obs");
			return Range.closed(minObs, maxObs);
		}

		// Truncate to be within trainingset upper and lower bound
		try { 
			return Range.closed(
					Math.max(interval.lowerEndpoint(), minObs), 
					Math.min(interval.upperEndpoint(), maxObs));
		} catch (IllegalArgumentException e) {
			LOGGER.debug("Could not generate standard capped interval: {}", e.getMessage());
		}

		if (interval.lowerEndpoint() >= maxObs) {
			return Range.closed(maxObs, maxObs);
		}
		if (interval.upperEndpoint() <= minObs) {
			return Range.closed(minObs, minObs);
		}
		LOGGER.debug("Could not generate capped interval with arguments; interval={}, minObs={}, maxObs={}",interval,minObs, maxObs);
		throw new IllegalArgumentException("Could not generate capped interval");
	}

	/**
	 * Generate the prediction interval given the midpoint ({@code y_hat}) and the intervals
	 * half width ({@code halfWidth})
	 * @param y_hat midpoint of the interval
	 * @param halfWidth half of the interval width
	 * @return the Range of the interval
	 */
	public static Range<Double> getInterval(double y_hat, double halfWidth) {
		return Range.closed(y_hat-halfWidth,y_hat+halfWidth);
	}


}
