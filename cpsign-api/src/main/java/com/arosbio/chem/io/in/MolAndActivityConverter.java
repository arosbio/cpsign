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
import java.util.Collections;
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
	private int terminateOnNumFails = 10;
	private boolean isClassification;
	private NamedLabels classLabels;
	private String propertyNameForActivity;

	/** Current "state" - if nextPair != null next() is OK, otherwise try to parse next from molIterator */ 
	private Pair<IAtomContainer, Double> nextPair;
	private Iterator<IAtomContainer> molIterator;
	/** If this is != null, calling the next() method should instead throw this stoppingExcept! */
	private EarlyLoadingStopException stoppingExcept;

	// Stats about loading
	private int molsSkippedMissingActivity;
	private int molsSkippedInvalidActivity;
	private int numOKmols;
	private List<FailedRecord> failedRecords = new ArrayList<>();
	
	private MolAndActivityConverter(Iterator<IAtomContainer> molecules, String property){
		if (property== null || property.isEmpty())
			throw new IllegalArgumentException("The property must be set");
		LOGGER.debug("IteratingMolAndActivity using property={}", property);

		this.propertyNameForActivity = property.trim();
		this.molIterator = molecules;
	}
	
	/**
	 * For regression data
	 * @param molecules An {@link Iterator} with molecules
	 * @param property the activity property
	 * @return the calling instance reference, for fluid API type calling
	 * @throws IllegalArgumentException Invalid arguments
	 */
	public static MolAndActivityConverter regressionConverter(Iterator<IAtomContainer> molecules, String property) 
			throws IllegalArgumentException {
		LOGGER.debug("Creating MolAndActivityConverter for regression data using property={}", property);
		return new MolAndActivityConverter(molecules, property);
	}

	/**
	 * For classification
	 * @param molecules An {@link Iterator} with molecules
	 * @param property the activity property
	 * @param classLabels The mapping of textual labels to numeric labels
	 * @return the calling instance reference, for fluid API type calling
	 * @throws IllegalArgumentException Invalid arguments
	 */
	public static MolAndActivityConverter classificationConverter(Iterator<IAtomContainer> molecules, String property, NamedLabels classLabels) 
			throws IllegalArgumentException {
				LOGGER.debug("Creating MolAndActivityConverter for classification data using property={} and labels={}", property,classLabels);
		if (classLabels==null || classLabels.getNumLabels()==0){
			throw new IllegalArgumentException("No class labels given");
		}
		// We have a classification problem
		if (classLabels.getNumLabels() < 2)
			throw new IllegalArgumentException("Classification labels must be at least 2");
		MolAndActivityConverter iter = new MolAndActivityConverter(molecules, property);

		iter.isClassification = true;
		iter.classLabels = classLabels.clone();
		return iter;
	}

	public String getPropertyNameForActivity() {
		return propertyNameForActivity;
	}

	public void setPropertyNameForActivity(String property) {
		this.propertyNameForActivity = property;
	}

	public boolean isClassification() {
		return isClassification;
	}

	public NamedLabels getClassLabels() {
		return classLabels;
	}

	public void setClassLabels(NamedLabels labels) {
		this.classLabels = labels;
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

	public List<FailedRecord> getFailedRecords(){
		if (molIterator instanceof ChemFileIterator) {
			List<FailedRecord> recs = new ArrayList<>(failedRecords);
			recs.addAll(((ChemFileIterator) molIterator).getFailedRecords());
			Collections.sort(recs);
			return recs;
		}
		return failedRecords;
	}

	/**
	 * Terminates parsing after <code>failAfterNum</code> number of failures. I.e. <code>failAfterNum</code>=10 will
	 * stop once the 11th record fails. if <code>failAfterNum &lt; 0</code> there's no stopping at all.
	 * @param failAfterNum max number of tries
	 */
	public void setStopAfterNumFails(int failAfterNum) {
		this.terminateOnNumFails = failAfterNum;
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
			LOGGER.debug("Failed initializing {}", MolAndActivityConverter.class.getCanonicalName(), e);
			throw new IllegalArgumentException("Could not generate any valid records from input data");
		}
	}

	@Override
	public boolean hasNext() {
		if (nextPair != null) // If called previously
			return true;

		if (isClassification)
			loadNextClassification();
		else
			loadNextRegression();

		if (nextPair == null) {
			LOGGER.debug("Finished iterating, numOKmols: {}, molsSkippedMissingActivity: {}, molsSkippedInvalidActivity: {}",
					numOKmols,molsSkippedMissingActivity,molsSkippedInvalidActivity);
			try {
				close();
			} catch(Exception e) {}
		}

		return nextPair != null;
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
			} else if (stoppingExcept != null) {
				// The hasNext() method calls the loading-methods and this exception could now be populated
				throw stoppingExcept;
			} else {
				throw new NoSuchElementException();
			}
		}
	}

	private Pair<IAtomContainer, Double> returnNext(){
		Pair<IAtomContainer, Double> next = nextPair;
		nextPair = null;
		numOKmols++;
		return next;
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
			LOGGER.debug("Calling close on {}", this.getClass().getCanonicalName());

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

	private void failMol(IAtomContainer mol, String reason) {
		Object ind = CPSignMolProperties.getRecordIndex(mol);
		if (com.arosbio.commons.TypeUtils.isInt(ind))
			failedRecords.add(new FailedRecord(com.arosbio.commons.TypeUtils.asInt(ind), CPSignMolProperties.getMolTitle(mol)).setReason(reason));
		else
			failedRecords.add(new FailedRecord(-1, CPSignMolProperties.getMolTitle(mol)).setReason(reason));
	}

	private void loadNextClassification(){

		while (nextPair == null && molIterator.hasNext()){
			// Fail "fast"
			if (terminateOnNumFails>= 0 && failedRecords.size() > terminateOnNumFails) {
				if (numOKmols == 0) {
					// Here we might have issues with the parameters!
					throw new IllegalArgumentException(String.format("MolAndActivityConverter was called with class-labels: {} but cannot find a match in labels in first {} records",
							classLabels, terminateOnNumFails));
				} else {
					LOGGER.debug("Number of allowed failures passed - will return an exception in case next() has been called or on the next call to that method");  
					stoppingExcept = new EarlyLoadingStopException(failedRecords);
					return;
				}
			}

			// load the next mol and look for the activity value
			IAtomContainer mol = molIterator.next();
			Object property = mol.getProperty(propertyNameForActivity);
			// skip if property does not exist
			if (property == null){
				molsSkippedMissingActivity++;
				failMol(mol, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}
			// convert to string
			String activityString = property.toString().trim();
			if (activityString.isEmpty()) {
				molsSkippedMissingActivity++;
				failMol(mol, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			} else if (!classLabels.contain(activityString)){
				molsSkippedInvalidActivity++;
				failMol(mol, "Activity \""+activityString +"\" not one of the given labels");
				continue;
			}

			// compute the activity 
			nextPair = ImmutablePair.of(mol, (double) classLabels.getValue(activityString));
		}

	}


	private void loadNextRegression(){
		
		while (nextPair == null && molIterator.hasNext()){

			// fail early
			if (terminateOnNumFails>= 0 && failedRecords.size() > terminateOnNumFails) {
				if (numOKmols == 0) {
					// Here we might have issues with the parameters!
					throw new IllegalArgumentException("MolAndActivityConverter was called in Regression-mode, but no molecules were parsed correctly - if this is a classification problem you must give the class labels, or is the property incorrect?");
				} else {
					LOGGER.debug("Number of allowed failures passed - will return an exception in case next() has been called or on the next call to that method");  
					stoppingExcept = new EarlyLoadingStopException(failedRecords);
					return;
				}
			}


			// load the next mol and look for the activity value
			IAtomContainer mol = molIterator.next();
			Object property = mol.getProperty(propertyNameForActivity);
			
			// skip if property does not exist
			if (property == null){
				molsSkippedMissingActivity++;
				failMol(mol, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}
			// convert to string
			String activity = property.toString().trim();

			// If no activity found
			if (activity.isEmpty()){
				molsSkippedMissingActivity++;
				failMol(mol, ERR_EMPTY_ACTIVITY_PROPERTY);
				continue;
			}

			// Try to parse activity as double
			double activityValue;
			try {
				activityValue = Double.valueOf(activity);
			} catch (NumberFormatException e){
				//			molsSkippedMissingActivity++;
				molsSkippedInvalidActivity++;
				failMol(mol, "Invalid activity \"" + activity + "\"");
				continue;
			}

			// Everything went OK, update the nextPair
			nextPair = ImmutablePair.of(mol, activityValue);
			
		}
	}

}
