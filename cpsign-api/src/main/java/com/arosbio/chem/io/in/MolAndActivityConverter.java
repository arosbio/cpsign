/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.in;


import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.chem.CPSignMolProperties;
import com.arosbio.chem.io.in.ChemFileIterator.EarlyLoadingStopException;
import com.arosbio.chem.io.in.FailedRecord.Cause;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.data.NamedLabels;

/**
 * An object of this class simply wraps an {@link java.util.Iterator} of <code>IAtomContainer</code> into an {@link java.util.Iterator} with a <code>Pair</code> of 
 * the molecule and the corresponding response value. 
 * 
 * <p>
 * <b>Classification</b><br>
 * If labels are given to the constructor, it's assumed that this is classification data
 * and that the property values should correspond to the given labels. Molecules missing the <code>property</code> will be ignored (number of
 * molecules missing the given property can be fetched through {@link #getMolsSkippedMissingActivity()}. Molecules with a property that
 * does not match the given labels are also failed and the number of those records can be fetched using {@link #getMolsSkippedInvalidActivity()}. 
 * 
 * <p>
 * <b>Regression</b><br>
 * If on the other hand no labels are given, we assume that it's regression data and that the property should be either a numeric value or a 
 * {@link java.lang.String} that can be parsed into a floating point value. 
 * Molecules that do not have the <code>property</code> will be ignored, number of
 * molecules missing the given <code>property</code> can be fetched through {@link #getMolsSkippedMissingActivity()}. 
 * Molecules that has the <code>property</code> but where the property cannot be parsed into a floating point value 
 * will be skipped and the number of those molecules can be retrieved by from {@link #getMolsSkippedInvalidActivity()}.
 * 
 * <p>
 * To adhere to normal Iterator interface, the {@link #initialize()} method should be called prior to calling {@link #hasNext()} for the first
 * time. Otherwise the {@link #hasNext()} method will throw an {@link IllegalArgumentException} in case no valid records can be generated
 * with the given parameters.
 * 
 * @author staffan
 *
 */
public class MolAndActivityConverter implements Iterator<Pair<IAtomContainer, Double>>, Closeable {

	private static final String ERR_EMPTY_ACTIVITY_PROPERTY = "Empty activity property";
	private static final Logger LOGGER = LoggerFactory.getLogger(MolAndActivityConverter.class);

	// Settings
	private final int terminateOnNumFails;
	private final boolean isClassification;
	private final NamedLabels classLabels;
	private final String propertyNameForActivity;
	private final Iterator<IAtomContainer> molIterator;

	/** Current "state" - if nextPair != null next() is OK, otherwise try to parse next from molIterator */ 
	private Pair<IAtomContainer, Double> nextPair;
	
	/** If this is != null, calling the next() method should instead throw this stoppingExcept! */
	private EarlyLoadingStopException stoppingExcept;

	// Stats about loading
	private int molsSkippedMissingActivity;
	private int molsSkippedInvalidActivity;
	private int numOKmols;
	private List<FailedRecord> failedRecords = new ArrayList<>();
	
	private MolAndActivityConverter(Iterator<IAtomContainer> molecules, String property, int terminateOnNumFails, NamedLabels nl) throws IllegalArgumentException {
		if (property== null || property.isBlank())
			throw new IllegalArgumentException("The property must be set");
		
		this.molIterator = molecules;
		this.propertyNameForActivity = property.trim();
		LOGGER.debug("init IteratingMolAndActivity using property={} in {} mode", property, nl!=null?"classification" : "regression");
		this.terminateOnNumFails = terminateOnNumFails;
		this.classLabels = nl;
		this.isClassification = nl != null;
	}

	public static class Builder {

		private String property;
		private Iterator<IAtomContainer> iterator;
		private NamedLabels classLabels;
		private int terminateOnNumFails = 10;

		private Builder(){}

		public static Builder regressionConverter(Iterator<IAtomContainer> molecules, String property){
			Builder b = new Builder();
			b.property = property;
			b.iterator = molecules;
			return b;
		}
		public static Builder classificationConverter(Iterator<IAtomContainer> molecules, String property, NamedLabels classLabels){
			Builder b = new Builder();
			b.classLabels = classLabels;
			b.property = property;
			b.iterator = molecules;
			return b;
		}

		/**
		 * Sets the maximum number of inconsistent records at the 'property level', i.e. 
		 * invalid records could occur at an earlier level - i.e. invalid chemical structures etc. 
		 * Set this to a negative number in order to deactivate early stopping.
		 * @param max the maximum number, or a negative number to deactivate early stopping
		 * @return the builder instance
		 */
		public Builder maxAllowedInvalidRecords(int max){
			this.terminateOnNumFails = max;
			return this;
		}

		public MolAndActivityConverter build() throws IllegalArgumentException {
			return new MolAndActivityConverter(iterator, property, terminateOnNumFails, classLabels);
		}
	}

	public String getPropertyNameForActivity() {
		return propertyNameForActivity;
	}

	public boolean isClassification() {
		return isClassification;
	}

	public NamedLabels getClassLabels() {
		return classLabels!=null ? classLabels.clone() : null;
	}

	public int getMolsSkippedMissingActivity(){
		return molsSkippedMissingActivity;
	}

	public int getMolsSkippedInvalidActivity(){
		return molsSkippedInvalidActivity;
	}

	public int getNumOKMols(){
		return numOKmols;
	}

	public int getNumFailedMols() {
		return getFailedRecords().size();
	}

	/**
	 * Getter method for the setting of maximum number of allowed inconsistent records,
	 * which is set using {@link Builder#maxAllowedInvalidRecords(int)}. If the value is negative,
	 * there is no early termination - all records will be iterated through.
	 * @return maximum number of inconsistent records before failure
	 */
	public int getMaxNumInconsistentRecords(){
		return terminateOnNumFails;
	}

	public List<FailedRecord> getFailedRecords(){
		// Copy of the list, the records themselves are immutable so no need to clone those
		List<FailedRecord> result = new ArrayList<>(failedRecords);
		if (molIterator instanceof ChemFileIterator) {
			result.addAll(((ChemFileIterator) molIterator).getFailedRecords());
			result = CollectionUtils.getUniqueAndSorted(result);
		}
		return result;
	}

	/**
	 * Get the underlying iterator of molecules 
	 * @return The underlying iterator of molecules
	 */
	public Iterator<IAtomContainer> getIterator(){
		return molIterator;
	}
	
	/**
	 * Initializes the iterator by pre-fetching the first (valid) record,
	 * or fails with an {@link IllegalArgumentException} in case the arguments
	 * did not match the data - in which case no valid data could be generated 
	 * @throws IllegalArgumentException If no valid records could be generated 
	 */
	public void initialize() throws IllegalArgumentException {
		try {
			if (isClassification)
				loadNextClassification();
			else
				loadNextRegression();
		} catch (Exception e) {
			LOGGER.debug("Failed initializing MolAndActivityConverter", e);
			throw new IllegalArgumentException("Could not generate any valid records from input data");
		}
	}

	@Override
	public boolean hasNext() {
		// If called previously
		if (nextPair != null || stoppingExcept != null) 
			return true;

		// Otherwise try to load the next one
		if (isClassification)
			loadNextClassification();
		else
			loadNextRegression();

		if (nextPair == null && stoppingExcept == null) {
			LOGGER.debug("Finished iterating, numOKmols: {}, molsSkippedMissingActivity: {}, molsSkippedInvalidActivity: {}",
					numOKmols,molsSkippedMissingActivity,molsSkippedInvalidActivity);
			try {
				close();
			} catch(Exception e) {}
		}

		return nextPair != null || stoppingExcept != null;
	}

	@Override
	public Pair<IAtomContainer, Double> next() throws EarlyLoadingStopException {
		if (nextPair != null){
			return returnNext();
		} else if (stoppingExcept != null) {
			throw stoppingExcept;
		} else {
			// Try to load next pair
			if (hasNext()){
				return returnNext();
			} else {
				throw new NoSuchElementException();
			}
		}
	}

	private Pair<IAtomContainer, Double> returnNext(){
		if (nextPair != null){
			Pair<IAtomContainer, Double> next = nextPair;
			nextPair = null;
			numOKmols++;
			return next;
		} else if (stoppingExcept != null){
			LOGGER.debug("Failing parsing of molecules from MolAndActivityConverter");
			throw stoppingExcept;
		} else {
			LOGGER.debug("in an inconsistent state - terminating");
			throw new IllegalStateException("Invalid state when loading and converting molecules");
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	/**
	 * This method calls <code>close()</code> in case that call on the underlying {@link java.util.Iterator}
	 * if it inherits either {@link java.io.Closeable} and/or {@link java.lang.AutoCloseable}.
	 * This class itself has no resources, but does this as a convenience for the user. Note: this method
	 * will not throw any exceptions even if the calls might cause issues downstream in the wrapped Iterator 
	 * (e.g. by multiple <code>close()</code> calls).
	 */
	public void close() {
		if (molIterator != null) {
			LOGGER.debug("Calling close on MolAndActivityConverter");

			try {
				if (molIterator instanceof Closeable) {
					((Closeable) molIterator).close();
				} else if (molIterator instanceof AutoCloseable) {
					((AutoCloseable) molIterator).close();
				}
			} catch (Exception e) {
				LOGGER.debug("Attempted to close underlying molecule iterator but got an exception",e);
			}

		}
	}

	private void failMol(IAtomContainer mol, FailedRecord.Cause cause, String reason) {
		Object ind = CPSignMolProperties.getRecordIndex(mol);
		int index = com.arosbio.commons.TypeUtils.isInt(ind) ? com.arosbio.commons.TypeUtils.asInt(ind) : -1;

		failedRecords.add(new FailedRecord.Builder(index, cause)
			.withID(CPSignMolProperties.getMolTitle(mol))
			.withReason(reason)
			.build());
	}

	/**
	 * Checks in case we've had enough errors in order to stop processing the input, and
	 * log details relevant for debugging the root cause and potential remedies. Also populates the {@link #stoppingExcept}
	 * so it can be thrown at the next call to {@link #next()}
	 * @return {@code true} if we should continue to process records, {@code false} if we should fail
	 */
	private boolean checkShouldContinue(){
		if (terminateOnNumFails>= 0 && failedRecords.size() > terminateOnNumFails) {
			// we've encountered enough failed records in order to exit

			if (numOKmols == 0) {
				// Here (almost definitely) have issues with the parameters!
				LOGGER.debug("No valid records found for the given settings in the first {} records, number of records with missing property={} (for property={}), number with invalid property value={}",
					failedRecords.size(), molsSkippedMissingActivity, propertyNameForActivity, molsSkippedInvalidActivity);
			} 
			LOGGER.debug("Number of allowed invalid records passed - will return an exception in case next() has been called or on the next call to that method");  

			stoppingExcept = new EarlyLoadingStopException("Encountered more invalid records than the allowed (" + terminateOnNumFails+')',failedRecords);

			return false;
		}
		return true;
	}

	private void loadNextClassification(){

		while (nextPair == null && molIterator.hasNext()){
			// Fail "fast"
			if (!checkShouldContinue()){
				return;
			}

			// load the next mol and look for the activity value
			IAtomContainer mol = molIterator.next();
			Object property = mol.getProperty(propertyNameForActivity);
			// skip if property does not exist
			if (property == null){
				molsSkippedMissingActivity++;
				failMol(mol, Cause.MISSING_PROPERTY, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}
			// convert to string
			String activityString = property.toString().trim();
			if (activityString.isEmpty()) {
				molsSkippedMissingActivity++;
				failMol(mol, Cause.MISSING_PROPERTY, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			} else if (!classLabels.contain(activityString)){
				molsSkippedInvalidActivity++;
				failMol(mol, Cause.INVALID_PROPERTY, "Activity \""+activityString +"\" not one of the given labels");
				continue;
			}

			// compute the activity 
			nextPair = ImmutablePair.of(mol, (double) classLabels.getValue(activityString));
		}

	}


	private void loadNextRegression(){
		
		while (nextPair == null && molIterator.hasNext()){

			// fail early
			if (!checkShouldContinue()){
				return;
			}

			// load the next mol and look for the activity value
			IAtomContainer mol = molIterator.next();
			Object property = mol.getProperty(propertyNameForActivity);
			
			// skip if property does not exist
			if (property == null){
				molsSkippedMissingActivity++;
				failMol(mol, Cause.MISSING_PROPERTY, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}
			// convert to string
			String activity = property.toString().trim();

			// If no activity found
			if (activity.isEmpty()){
				molsSkippedMissingActivity++;
				failMol(mol, Cause.MISSING_PROPERTY, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}

			// Try to parse activity as double
			double activityValue;
			try {
				activityValue = Double.valueOf(activity);
			} catch (NumberFormatException e){
				//			molsSkippedMissingActivity++;
				molsSkippedInvalidActivity++;
				failMol(mol, Cause.INVALID_PROPERTY, "Invalid activity \"" + activity + "\"");
				continue;
			}

			// Everything went OK, update the nextPair
			nextPair = ImmutablePair.of(mol, activityValue);
			
		}
	}

}
