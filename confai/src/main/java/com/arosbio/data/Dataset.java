/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.mixins.HasProperties;
import com.arosbio.data.io.DataSerializationFormat;
import com.arosbio.data.io.LIBSVMFormat;
import com.arosbio.data.transform.Transformer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.encryption.utils.EncryptUtils;
import com.arosbio.encryption.utils.EncryptUtils.EncryptionStatus;
import com.arosbio.io.DataIOUtils;
import com.arosbio.io.DataSink;
import com.arosbio.io.DataSource;
import com.arosbio.io.Saveable;
import com.arosbio.io.StreamUtils;
import com.arosbio.ml.io.impl.PropertyNameSettings;
import com.google.common.collect.ImmutableList;


/**
 * The {@link Dataset} class represent a modeling dataset with records. Data is stored in (potentially)
 * three separate {@link com.arosbio.data.Dataset.SubSet SubSet}s:
 * <ul>
 * 		<li>dataset</li>
 * 		<li>modelingExclusive</li>
 * 		<li>calibrationExclusive</li>
 * </ul> 
 * The <i>dataset</i> dataset is used as the 'normal' dataset, were records are
 * used both in the <b>proper training set</b> and <b>calibration set</b> in the Conformal prediction
 * notation. The '*<i>Exclusive</i>' datasets are used for records that exclusively should be part of
 * either <b>proper training set</b> or <b>calibration set</b>. These will be added to the splits
 * that has been created using the <i>dataset</i> dataset. 
 * 
 * <p>
 * Data is stored as {@link com.arosbio.data.DataRecord DataRecord} objects, which contains a 
 * {@link FeatureVector} of features/attributes and the given label for that object.   
 * </p>
 * 
 * @author ola
 * @author staffan
 *
 */
public class Dataset implements Cloneable, HasProperties, Saveable {

	// ---------------------------------------------------------------------
	// ENUMS
	// ---------------------------------------------------------------------
	/**
	 * An enum type for marking a specific {@link SubSet SubSet} of observations for specific tasks in modeling, training underlying algorithm or calibration 
	 * of final Predictors.
	 */
	public enum RecordType { 
		/**
		 * This is the <b>standard</b> data type, used for test-train splits, splitting into calibration/proper-training sets etc 
		 */
		NORMAL, 
		/**
		 * Data part of <b>modeling-exclusive</b> is added when training underlying models, but never used as test-data nor calibration-data (for Split-predictor types)
		 */
		MODELING_EXCLUSIVE, 
		/**
		 * Data part of <b>calibration-exclusive</b> is added to the calibration-sets <b><i>only</i></b>. Never used for testing or added to proper-training sets.
		 */
		CALIBRATION_EXCLUSIVE 
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(Dataset.class);
	private final static String PROBLEM_DIRECTORY_NAME = "sparse_data";
	private final static String DATASET_FILE_NAME = "data.csr";
	private final static String CALIBRATION_EXCLUSIVE_FILE_NAME = "data.calib.exclusive.csr";
	private final static String MODELING_EXCLUSIVE_FILE_NAME = "data.model.exclusive.csr";
	private final static String TRANSFORMER_DIRECTORY_NAME = "transformations";
	private final static String TRANSFORMER_BASE_FILE_NAME = "data.transform.";

	private SubSet dataset = new SubSet();
	private SubSet modelingExclusive = new SubSet(RecordType.MODELING_EXCLUSIVE);
	private SubSet calibrationExclusive = new SubSet(RecordType.CALIBRATION_EXCLUSIVE);

	private List<Transformer> transformers = new ArrayList<>();

	/**
	 * A single set of {@link DataRecord DataRecords}. 
	 */
	public static class SubSet extends ArrayList<DataRecord> {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3844667771504296003L;
		private static final Logger LOGGER = LoggerFactory.getLogger(SubSet.class);
		private static final DataSerializationFormat DEFAULT_SERIALIZATION_FMT = new LIBSVMFormat();

		private RecordType dataType = RecordType.NORMAL;

		// ---------------------------------------------------------------------
		// CONSTRUCTORS
		// ---------------------------------------------------------------------

		public SubSet(){
			super();
		}

		public SubSet(RecordType type){
			super();
			if (type == null)
				throw new NullPointerException("RecordType cannot be null");
			this.dataType = type;
		}

		public SubSet(List<DataRecord> records) {
			super(records);
		}

		public SubSet(int initialCapacity) {
			super(initialCapacity);
		}

		// ---------------------------------------------------------------------
		// STATIC INIT
		// ---------------------------------------------------------------------

		/**
		 * Creates a {@link SubSet} by reading an {@link InputStream} in LibSVM format. The stream can be 
		 * gzipped as well.
		 * @param stream An {@link InputStream} in LibSVM format
		 * @return The data in a {@link SubSet} 
		 * @throws IOException An IO exception when reading from the stream
		 */
		public static SubSet fromLIBSVMFormat(InputStream stream) throws IOException {
			return fromInput(stream, new LIBSVMFormat());
		}

		/**
		 * Creates a {@link SubSet} by reading an encrypted {@link InputStream} in LibSVM format.
		 * @param stream An encrypted {@link InputStream}
		 * @param spec An {@link EncryptionSpecification} having the specification for decrypting the stream
		 * @return The data in a {@link SubSet}
		 * @throws IOException An IO exception when reading from the stream
		 * @throws InvalidKeyException If <code>spec</code> is <code>null</code> or not matches the encryption
		 */
		public static SubSet fromLIBSVMFormat(InputStream stream, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException {
			if (spec == null)
				throw new InvalidKeyException("Must send a EncryptionSpecification when calling this method");

			InputStream decryptedStream = spec.decryptStream(stream);
			return fromLIBSVMFormat(decryptedStream);
		}

		public static SubSet fromInput(InputStream stream, DataSerializationFormat format) throws IOException {
			try( 
					InputStream unzippedStream = StreamUtils.unZIP(stream);
					) {
				return format.read(unzippedStream);

			} catch (Exception e){
				throw new IOException(e.getMessage());
			}
		}

		public static SubSet fromInput(InputStream stream, DataSerializationFormat format, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException {
			if (spec == null)
				throw new InvalidKeyException("Must send a EncryptionSpecification when calling this method");

			InputStream decryptedStream = spec.decryptStream(stream);
			return fromInput(decryptedStream, format); 
		}

		// ---------------------------------------------------------------------
		// GETTERS / SETTERS
		// ---------------------------------------------------------------------

		public RecordType getDataType(){
			return dataType;
		}

		/**
		 * Set the {@link RecordType} of the current {@link SubSet}
		 * @param type The {@link RecordType} of the current {@link SubSet}
		 * @return the same {@link SubSet}
		 * @throws NullPointerException In case {@code type} is {@code null}
		 */
		public SubSet withRecordType(RecordType type){
			if (type == null)
				throw new NullPointerException("Record type cannot be null");
			this.dataType = type;
			return this;
		}

		public Set<Double> getLabels(){
			return DataUtils.findLabels(this);
		}

		/**
		 * Clears the current records and copies the ones from the parameter {@code records}.
		 * Note that records are copied, so re-arrangement of the input list has no effect on the current
		 * SubSet. Making alterations to individual records are reflected as the records themselves are not cloned. 
		 * @param records the new records
		 */
		public void setRecords(List<DataRecord> records) {
			clear();
			addAll(records);
		}

		public int getNumRecords() {
			return size();
		}

		/**
		 * Get the number of features or attributes
		 * @return the number of features or attributes
		 */
		public int getNumAttributes(){
			int max = -1,min=Integer.MAX_VALUE;
			for (DataRecord record : this){
				if (record != null){
					max = Math.max(max, record.getMaxFeatureIndex());
					min = Math.min(min, record.getMinFeatureIndex());
				}
					
			}
			return Math.max(max + 1 - min, 0); // + 1 for using feature index 0 as well! cap at 0 so we cannot have negative number of features
		}
		/**
		 * Alias for {@link #getNumAttributes()}
		 * @return the number of features or attributes
		 */
		public int getNumFeatures(){
			return getNumAttributes();
		}

		public boolean containsMissingFeatures() {
			for (DataRecord r : this) {
				if (r.getFeatures().containsMissingFeatures())
					return true;
			}
			return false;
		}

		/**
		 * Get the frequency of all labels. <b>only applicable to classification data</b>
		 * @return Map "numeric value" -&gt; "num occurrences"
		 */
		public Map<Double, Integer> getLabelFrequencies(){
			return DataUtils.countLabels(this);
		}

		// ============================================================
		//   SAVE / LOAD DATA
		// ============================================================

		/**
		 * Read data using the default {@link DataSerializationFormat}, which is {@link LIBSVMFormat}
		 * @param stream to read from
		 * @throws IOException Issues reading data
		 */
		public void readRecords(InputStream stream) 
				throws IOException {
			readRecords(stream, DEFAULT_SERIALIZATION_FMT);
		}

		/**
		 * Read data using an explicit {@link DataSerializationFormat}
		 * @param stream to read from 
		 * @param format the format
		 * @throws IOException Issues reading data
		 */
		public void readRecords(InputStream stream, DataSerializationFormat format) 
				throws IOException {
			SubSet ds = fromInput(stream, format);
			this.addAll(ds);
		}

		public void readRecords(InputStream stream, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException {
			readRecords(stream, DEFAULT_SERIALIZATION_FMT, spec);
		}

		/**
		 * Adds data from sparse file to the current{@link Dataset}. If you wish to remove old data
		 * that might be in the {@link Dataset} since before, please call the {@link #clear() clear} method before.
		 * @param stream An {@link InputStream} with encrypted data
		 * @param format An object that can read the data in the format it is saved in
		 * @param spec An {@link EncryptionSpecification} having the specification for decrypting the stream
		 * @throws IOException An IO exception when reading from the stream
		 * @throws InvalidKeyException In case the stream could not be decrypted (faulty encryption-key)
		 */
		public void readRecords(InputStream stream, DataSerializationFormat format, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException {

			EncryptionStatus status =null;
			try (BufferedInputStream buffStream = new BufferedInputStream(stream);){

				status = EncryptUtils.getStatus(buffStream, spec);
				LOGGER.debug("EncryptionStatus for SubSet was={}",status);
				switch (status) {
				case ENCRYPTED_CORRECT_SPEC:
					LOGGER.debug("Trying to read encrypted records");
					readRecords(spec.decryptStream(buffStream), format);
					break;
				case ENCRYPTED_WRONG_SPEC:
					LOGGER.debug("Records are encrypted with a different key than the given one");
					throw new InvalidKeyException("Records are encrypted with a different key than the given one");
				case UNKNOWN:
					LOGGER.debug("Trying to read plain-text or compressed records");
					readRecords(buffStream, format);
					break;
				default:
					LOGGER.debug("EncryptionStatus returned was: {} - not handled by the code", status);
					throw new IOException("Could not read records due to coding error, please send the log-file to Aros Bio AB");
				}
			} catch (InvalidKeyException | IOException e){
				LOGGER.debug("Failed reading dataset from format: {}", format.getName(), e);
				if (status == EncryptionStatus.UNKNOWN){
					throw new IOException(e.getMessage() + ", could the data be encrypted?");
				}
				throw e;
			}
		}


		/**
		 * Writes the {@link SubSet} to an OutputStream, using the default serialization format ({@link LIBSVMFormat}).
		 * @param ostream The {@link OutputStream} to write to
		 * @param compress If the stream should be compressed using gzip
		 * @throws IOException An IO exception when writing to the stream
		 */
		public void writeRecords(OutputStream ostream, boolean compress) throws IOException {
			writeRecords(ostream, compress, DEFAULT_SERIALIZATION_FMT);
		}

		public void writeRecords(OutputStream ostream, boolean compress, DataSerializationFormat format) throws IOException {
			if (compress)
				ostream = new GZIPOutputStream(ostream);

			format.write(ostream, this);
		}

		/**
		 * Write the {@link Dataset} to encrypted file
		 * @param ostream The {@link OutputStream} to write to
		 * @param spec The {@link EncryptionSpecification} that should be used
		 * @throws IOException An IO exception when writing to the stream
		 * @throws InvalidKeyException No encryption spec given
		 */
		public void writeRecords(OutputStream ostream, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException {
			if (spec == null)
				throw new InvalidKeyException("Cannot send null as Encryption Specification");
			OutputStream encryptedStream = spec.encryptStream(ostream);
			writeRecords(encryptedStream, false, DEFAULT_SERIALIZATION_FMT);
		}

		public void writeRecords(OutputStream ostream, EncryptionSpecification spec, DataSerializationFormat format) 
				throws IOException, InvalidKeyException {
			if (spec == null)
				throw new InvalidKeyException("Cannot send null as Encryption Specification");
			OutputStream encryptedStream = spec.encryptStream(ostream);
			writeRecords(encryptedStream, false, format);
		}

		public void saveToSink(DataSink sink, String location, EncryptionSpecification spec) 
				throws IOException, InvalidKeyException, IllegalStateException {
			LOGGER.debug("Saving dataset to datasink, loc={}", location);
			try (OutputStream ostream = sink.getOutputStream(location)){
				if(spec != null)
					writeRecords(ostream, spec);
				else
					writeRecords(ostream, false);
			}
		}
		public void loadFromSource(DataSource source, String location, EncryptionSpecification spec) throws IOException, InvalidKeyException {
			LOGGER.debug("loading dataset from datasource, loc={}", location);
			try (InputStream istream = source.getInputStream(location)){
				readRecords(istream, spec);
			}
		}


		/**
		 * Prints the number of records and features
		 * @return A {@link String} with representing the data stored in this SubSet
		 */
		@Override
		public String toString() {
			return String.format("Dataset with %s records and %s features",
					this.size(),getNumFeatures());
		}

		/**
		 * Strictly for debugging and tests
		 * @return The dataset in LIBSVM format
		 */
		public String toLibSVMFormat() {
			StringBuffer sb = new StringBuffer(size()*20);
			String nl = System.lineSeparator();
			for (DataRecord rec: this){
				sb.append(LIBSVMFormat.serialize(rec)).append(nl);
			}

			if (sb.length()>0)
				sb.deleteCharAt(sb.length()-nl.length()); // remove the last newline

			return sb.toString();
		}

		/**
		 * Shuffle the SubSet using the current RNG seed as random seed
		 * @return The reference to the current SubSet 
		 */
		public SubSet shuffle(){
			shuffle(GlobalConfig.getInstance().getRNGSeed());
			return this;
		}

		/**
		 * Randomly shuffles the {@link SubSet}, using an explicit random seed. 
		 * @param randomSeed the random seed to use for the RNG
		 * @return The reference to the current SubSet
		 */
		public SubSet shuffle(long randomSeed) {
			Collections.shuffle(this, new Random(randomSeed));
			return this;
		}

		/**
		 * Checks if two {@link SubSet} are identical, down to the order of records
		 * @return <code>true</code> if the {@link Object}s are identical, otherwise <code>false</code>
		 */
		@Override
		public boolean equals(Object o){
			if (this== o)
				return true;

			if (! (o instanceof SubSet)){
				LOGGER.debug("object not a SubSet");
				return false;
			}

			SubSet other = (SubSet) o;

			if (size() != other.size()){
				LOGGER.debug("SubSet-size not equal: {} vs. {}", size(), other.size());
				return false;
			}

			for (int i=0; i<size(); i++){
				if(! get(i).equals(other.get(i))){
					LOGGER.debug("DataRecords not the same at index {}, records:\n{}\n{}", i, get(i), other.get(i));
					return false;
				}
			}
			return true;
		}

		/**
		 * Makes a deep copy of the {@link SubSet}
		 * @return a deep copy of the current {@link SubSet}
		 */
		@Override
		public SubSet clone() {
			SubSet clone = new SubSet(size());
			clone.dataType = this.dataType;
			for (DataRecord rec : this){
				clone.add(rec.clone());
			}
			return clone;
		}

		/**
		 * Randomly split the {@link SubSet} into two disjoint datasets. Ex calling with <code>fraction</code>=0.3 will
		 * have 30% of the records in the first {@link SubSet} and the remaining 70% in the second. Uses {@link GlobalConfig#getRNGSeed()} as 
		 * seed for the random generator
		 * @param fraction splitting fraction, should be in range <code>(0.0..1.0)</code>
		 * @return Two {@link SubSet}s, one with original size*<code>fraction</code> and the other with original size*(1-<code>fraction</code>)
		 * @throws IllegalArgumentException If <code>fraction</code> is outside range <code>(0.0..1.0)</code>
		 */
		public SubSet[] splitRandom(double fraction) throws IllegalArgumentException{
			return splitRandom(GlobalConfig.getInstance().getRNGSeed(), fraction);
		}

		/**
		 * Randomly split the {@link SubSet} into two disjoint dataset. Ex calling with <code>fraction</code>=0.3 will
		 * have 30% of the records in the first {@link SubSet} and the remaining 70% in the second.
		 * @param seed the seed used for the random generator
		 * @param fraction splitting fraction, should be in the range <code>(0.0..1.0)</code>
		 * @return Two {@link SubSet}s, one with original size*<code>fraction</code> and the other with original size*(1-<code>fraction</code>)
		 * @throws IllegalArgumentException If <code>fraction</code> is outside range <code>(0.0..1.0)</code>
		 */
		public SubSet[] splitRandom(long seed, double fraction) throws IllegalArgumentException{
			if (fraction <= 0.0 || fraction>= 1.0)
				throw new IllegalArgumentException("Splitting fraction must be within range (0.0..1.0)");

			int splitIndex = (int) Math.ceil(size()*fraction);

			List<DataRecord> dsList = this.clone();
			Collections.shuffle(dsList, new Random(seed));

			return new SubSet[] {
					new SubSet(dsList.subList(0, splitIndex)),
					new SubSet(dsList.subList(splitIndex, dsList.size()))
			};
		}
		
		public SubSet[] splitStratified(double fraction) throws IllegalArgumentException, IllegalAccessException{
			return splitStratified(GlobalConfig.getInstance().getRNGSeed(), fraction);
		}

		/**
		 * Split classification data stratified. Fails for regression 
		 * @param seed random number generator seed for shuffling
		 * @param fraction splitting fraction, should be in the range (0..1)
		 * @return two SubSets 
		 * @throws IllegalArgumentException If <code>fraction</code> is outside (0..1)
		 * @throws IllegalAccessException If calling this method with regression data (i.e. more than 10 labels)
		 */
		public SubSet[] splitStratified(long seed, double fraction) throws IllegalArgumentException, IllegalAccessException {
			if (fraction <= 0.0 || fraction >= 1.0)
				throw new IllegalArgumentException("Splitting fraction must be within range (0.0..1.0)");
			if (isEmpty()) // give back two empty subsets
				return new SubSet[]{new SubSet(), new SubSet()};

			// Split up records into separate lists
			List<List<DataRecord>> stratas = null;
			try{
				stratas = DataUtils.stratify(this);
			} catch (IllegalArgumentException e) {
				LOGGER.debug("Failed generating stratas for dataset",e);
				throw new IllegalAccessException(e.getMessage());
			}
			
			SubSet s1 = new SubSet();
			SubSet s2 = new SubSet();

			for (List<DataRecord> strata : stratas) {
				int splitInd = (int) Math.ceil(strata.size()*fraction);
				s1.addAll(strata.subList(0, splitInd));
				s2.addAll(strata.subList(splitInd, strata.size()));
			}

			s1.shuffle(seed);
			s2.shuffle(seed);

			return new SubSet[] {s1, s2};
		}

		/**
		 * Does a static split (i.e. no randomization), will return two {@link SubSet} with the first having the first
		 * fraction and the remaining data in the second one. Ex calling with <code>fraction</code>=0.3 will
		 * have 30% of the records in the first {@link SubSet} and the remaining 70% in the second.
		 * @param fraction splitting fraction, should be between <code>0.0</code> and <code>1.0</code>
		 * @return Two <code>SubSet</code>, one with original size*fraction and the other with original size*(1-fraction)
		 * @throws IllegalArgumentException If <code>fraction</code> is outside (0..1)
		 */
		public SubSet[] splitStatic(double fraction) throws IllegalArgumentException{
			if(fraction <= 0.0 || fraction>= 1.0)
				throw new IllegalArgumentException("Splitting fraction must be within range (0..1)");

			return splitStatic((int)(Math.ceil(fraction*size())));
		}

		/**
		 * Does a static split (i.e. no randomization), will return two {@link SubSet} with the first having
		 * all records from index 0 to (excluding) <code>indexToSplitAt</code> and the second with all records
		 * from (inclusive) <code>indexToSplitAt</code> to the end. Will preserve order of the original {@link SubSet}
		 * @param indexToSplitAt The index to split at
		 * @return Two {@link SubSet}, one with all records up to (exclusive) <code>indexToSplitAt</code> and the other with the remaining data
		 * @throws IllegalArgumentException If <code>indexToSplitAt</code> is less than 0 or larger than original {@link SubSet}s size
		 */
		public SubSet[] splitStatic(int indexToSplitAt) throws IllegalArgumentException{
			if (indexToSplitAt < 0)
				throw new IllegalArgumentException("Cannot split at a negative index");
			if (indexToSplitAt >= size())
				throw new IllegalArgumentException("Cannot split at index " + indexToSplitAt + ", dataset is only " + size() + " records big");

			SubSet clone = clone();

			return new SubSet[]{
					new SubSet(clone.subList(0,indexToSplitAt)), 
					new SubSet(clone.subList(indexToSplitAt, size()))
			};
		}

		/**
		 * Adds the records of the <code>dataset</code> into this object. Makes a deep copy
		 * of the underlying data so no changes are seen in the other instance
		 * @param dataset Another {@link SubSet} to join into the current one
		 * @throws IllegalArgumentException If the <code>dataset</code> is the same {@link SubSet} as the current one or if the <code>dataset</code> is empty or indices are faulty
		 * @return The reference to the current SubSet
		 */
		public SubSet join(SubSet dataset) throws IllegalArgumentException{
			if (this == dataset)
				throw new IllegalArgumentException("Cannot join the dataset with itself");
			if (dataset == null)
				throw new IllegalArgumentException("Cannot join the dataset with a null reference");

			for (DataRecord rec : dataset){
				add(rec.clone());
			}
			return this;
		}

		/**
		 * Performs a shallow join of the records from the <code>dataset</code>. Changing things in one of the
		 * SubSets will alter the other one as well. 
		 * @param dataset Another {@link SubSet} to join into the current one
		 * @throws IllegalArgumentException If any of the two {@link SubSet}s are ill-formatted.
		 * @return The reference to the current SubSet 
		 */
		public SubSet joinShallow(SubSet dataset) throws IllegalArgumentException {
			if (dataset == this)
				throw new IllegalArgumentException("Cannot join the dataset with itself");
			if (dataset == null)
				throw new IllegalArgumentException("Cannot join the dataset with a null reference");

			if (isEmpty()) {
				LOGGER.debug("Current SubSet is empty, just point at the other dataset");
				addAll(dataset); 
			} else {
				LOGGER.debug("Appending data from the other dataset");
				addAll(dataset);
			}
			return this;
		}


		public List<Double> extractColumn(int column){
			List<Double> columnValues = new ArrayList<>(size());
			for (DataRecord r: this) {
				columnValues.add(r.getFeatures().getFeature(column));
			}
			return columnValues;
		}

	}


	/**
	 * Constructor that creates a empty {@link Dataset}
	 */
	public Dataset() {
		super();
	}



	/**
	 * Creates a {@link Dataset} by reading an {@link InputStream} in LibSVM format. The stream can be 
	 * gzipped as well. The data will be put in the default {@link SubSet}
	 * @param stream An {@link InputStream} in LibSVM format
	 * @return The data in a {@link Dataset} 
	 * @throws IOException If an IOException occurred reading from the stream
	 */
	public static Dataset fromLIBSVMFormat(InputStream stream) throws IOException {		

		Dataset prob = new Dataset();
		prob.dataset = SubSet.fromLIBSVMFormat(stream);

		return prob;

	}

	/**
	 * Creates a {@link Dataset} by reading an encrypted {@link InputStream} in {@link LIBSVMFormat} format.
	 * The records will be put in the default dataset
	 * @param stream An encrypted {@link InputStream}
	 * @param spec An {@link EncryptionSpecification} having the specification for decrypting the stream
	 * @return The data in a {@link Dataset}
	 * @throws IOException If an IOException occurred reading from the stream
	 * @throws InvalidKeyException If <code>spec</code> is <code>null</code> or not matches the encryption
	 */
	public static Dataset fromLIBSVMFormat(InputStream stream, EncryptionSpecification spec) 
			throws IOException, InvalidKeyException {
		if (spec == null)
			throw new InvalidKeyException("Must send a EncryptionSpecification when calling this method");

		InputStream decryptedStream = spec.decryptStream(stream);
		return fromLIBSVMFormat(decryptedStream);
	}

	public SubSet getDataset(RecordType type) {
		switch (type) {
		case NORMAL:
			return dataset;
		case CALIBRATION_EXCLUSIVE:
			return calibrationExclusive;
		case MODELING_EXCLUSIVE:
			return modelingExclusive;
		default:
			// Should never happen
			throw new IllegalArgumentException("RecordType: " + type + " not supported");
		}
	}

	/**
	 * Getter for the normal dataset
	 * @return the normal {@link SubSet}
	 */
	public SubSet getDataset() {
		return dataset;
	}

	/**
	 * Setter for the normal dataset
	 * @param dataset set the normal {@link SubSet}
	 */
	public void setDataset(SubSet dataset) {
		if (dataset == null)
			this.dataset = new SubSet();
		else {
			this.dataset = dataset;
			this.dataset.withRecordType(RecordType.NORMAL);
		}
			
	}

	public SubSet getModelingExclusiveDataset() {
		return modelingExclusive;
	}

	public void setModelingExclusiveDataset(SubSet modelingExclusive) {
		if (modelingExclusive == null)
			this.modelingExclusive = new SubSet(RecordType.MODELING_EXCLUSIVE);
		else {
			this.modelingExclusive = modelingExclusive;
			this.modelingExclusive.withRecordType(RecordType.MODELING_EXCLUSIVE);
		}
			
	}

	public SubSet getCalibrationExclusiveDataset() {
		return calibrationExclusive;
	}

	public void setCalibrationExclusiveDataset(SubSet calibrationExclusive) {
		if (calibrationExclusive == null)
			this.calibrationExclusive = new SubSet(RecordType.CALIBRATION_EXCLUSIVE);
		else {
			this.calibrationExclusive = calibrationExclusive;
			this.calibrationExclusive.withRecordType(RecordType.CALIBRATION_EXCLUSIVE);
		}
			
	}

	/**
	 * Getter for the number of records in the current problem (sum of all datasets number of records)
	 * @return number of records
	 */
	public int getNumRecords(){
		return dataset.size()+
				modelingExclusive.size()+
				calibrationExclusive.size();
	}

	/**
	 * Getter for the number of attributes
	 * @return number of attributes
	 */
	public int getNumAttributes(){
		int max1 = dataset.getNumFeatures();
		int max2 = calibrationExclusive.getNumFeatures();
		int max3 = modelingExclusive.getNumFeatures();

		return Math.max(max1, Math.max(max2, max3));
	}

	/**
	 * Alias for {@link #getNumAttributes()}
	 * @return number of features
	 */
	public int getNumFeatures(){
		return getNumAttributes();
	}

	/**
	 * Does a brute-force look through all the datasets to find all labels
	 * @return All labels found in this {@link Dataset} 
	 */
	public Set<Double> getLabels(){
		Set<Double> labels = new HashSet<>();
		if (dataset!=null)
			labels.addAll(dataset.getLabels());
		if (modelingExclusive!=null)
			labels.addAll(modelingExclusive.getLabels());
		if (calibrationExclusive!=null)
			labels.addAll(calibrationExclusive.getLabels());
		return labels;
	}

	public String toString() {
		if (dataset.isEmpty() && modelingExclusive.isEmpty() && calibrationExclusive.isEmpty())
			return "Empty dataset";
		StringBuilder sb = new StringBuilder();
		sb.append("Dataset with;");
		final String nl = System.lineSeparator();
		if (!dataset.isEmpty()) {
			sb.append(nl);
			sb.append(dataset.toString());
		}
		if (!modelingExclusive.isEmpty()){
			sb.append(nl);
			sb.append("Model exclusive-").append(modelingExclusive.toString());
		}
		if (! calibrationExclusive.isEmpty()){
			sb.append(nl);
			sb.append("Calibration exclusive-").append(calibrationExclusive.toString());
		}
		return sb.toString();
	}

	/**
	 * Set transformers that has been applied from outside of the Dataset class, but should
	 * be saved for future predictions. If the transformations should be applied - use the 
	 * {@link #apply(List)} or {@link #apply(Transformer)} methods instead
	 * @param transformers A list of {@link Transformer}s that should already have been applied to this Dataset.
	 */
	public void setTransformers(List<Transformer> transformers) {
		this.transformers = new ArrayList<>(transformers);
	}

	private List<DataRecord> getAllRecords(){
		List<DataRecord> allRecs = new ArrayList<>();
		if (!dataset.isEmpty())
			allRecs.addAll(dataset);
		if (!modelingExclusive.isEmpty())
			allRecs.addAll(modelingExclusive);
		if (!calibrationExclusive.isEmpty())
			allRecs.addAll(calibrationExclusive);

		return allRecs;
	}

	/**
	 * First fits the Transformer using all available data - then transform
	 * each of the {@link SubSet SubSets}
	 * @param transformer A single {@link Transformer} to apply 
	 */
	public void apply(Transformer transformer) {
		// Use all records to fit the transformer
		transformer.fit(getAllRecords());

		// Apply on each dataset in term
		dataset = transformer.transform(dataset);
		modelingExclusive = transformer.transform(modelingExclusive);
		modelingExclusive.dataType = RecordType.MODELING_EXCLUSIVE;
		calibrationExclusive = transformer.transform(calibrationExclusive);
		calibrationExclusive.dataType = RecordType.CALIBRATION_EXCLUSIVE;

		this.transformers.add(transformer);

	}

	public void apply(Transformer transformer, RecordType fitUsing) {
		transformer.fit(getDataset(fitUsing));

		// Apply on each dataset in term
		dataset = transformer.transform(dataset);
		modelingExclusive = transformer.transform(modelingExclusive);
		modelingExclusive.dataType = RecordType.MODELING_EXCLUSIVE;
		calibrationExclusive = transformer.transform(calibrationExclusive);
		calibrationExclusive.dataType = RecordType.CALIBRATION_EXCLUSIVE;

		this.transformers.add(transformer);

	}

	public void apply(Transformer... transformers){
		for (Transformer t : transformers) {
			apply(t);
		}
	}

	public void apply(RecordType fitUsing, Transformer... transformers){
		for (Transformer t : transformers) {
			apply(t,fitUsing);
		}
	}

	public void apply(List<Transformer> transformers) {
		for (Transformer t : transformers) {
			apply(t);
		}
	}

	public void apply(List<Transformer> transformers, RecordType fitUsing) {
		for (Transformer t : transformers) {
			apply(t,fitUsing);
		}
	}

	/**
	 * Applies the transformers to a new object, if any transformers are set, otherwise
	 * the object will be returned unchanged
	 * @param object a new test object
	 * @return the transformed (if any transformers should be applied, else the original) FeatureVector 
	 */
	public FeatureVector transform(FeatureVector object) {
		for (Transformer t : transformers) {
			if (t.appliesToNewObjects())
				object = t.transform(object);
		}
		return object;
	}

	public List<Transformer> getTransformers(){
		return ImmutableList.copyOf(transformers);
	}



	// ============================================================
	//   WRITE DATA
	// ============================================================


	private void doSaveTransformers(final DataSink sink, final String directoryBase) throws IOException {

		if (!transformers.isEmpty()) {
			int index = 0;
			for (Transformer t : transformers) {
				if (! t.appliesToNewObjects()) {
					LOGGER.debug("Skipping to save transformer {} as it does not apply to new objects",t);
					continue;
				} else {
					String tPath = directoryBase+TRANSFORMER_BASE_FILE_NAME+index;
					try (OutputStream os = sink.getOutputStream(tPath);
							ObjectOutputStream oos = new ObjectOutputStream(os);){
						oos.writeObject(t);
						oos.writeUTF("\n");
						LOGGER.debug("Saved transformer {} to path: {}",t, tPath);
					}
					index++;
				}
			}

			if (index <= 0) {
				LOGGER.debug("No transformers saved - none required to be saved");
			} else {
				LOGGER.debug("Saved {} transformers",(index+1));
			}
		}
	}

	public void saveTransformersToSink(DataSink sink, String basePath) 
			throws IOException {
		if (!transformers.isEmpty()) {
			String transformerDir = DataIOUtils.createBaseDirectory(sink, basePath, TRANSFORMER_DIRECTORY_NAME);
			doSaveTransformers(sink, transformerDir);
		}
	}

	/**
	 * Saves both records any applied transformations that is needed for future test-records
	 */
	@Override
	public void saveToDataSink(DataSink sink, String path, EncryptionSpecification encryptSpec)
			throws IOException, InvalidKeyException, IllegalStateException {

		if (dataset.isEmpty()&&modelingExclusive.isEmpty()&&calibrationExclusive.isEmpty())
			throw new IllegalStateException("Dataset is empty - cannot be saved");

		String problemDir = DataIOUtils.appendTrailingDash(
				DataIOUtils.createBaseDirectory(sink, path, PROBLEM_DIRECTORY_NAME));
		LOGGER.debug("Saving Dataset to location={}", problemDir);

		// Save the respective datasets
		if(!dataset.isEmpty()){
			dataset.saveToSink(sink, problemDir+DATASET_FILE_NAME, encryptSpec);
			LOGGER.debug("Saved normal dataset");
		}
		if(!modelingExclusive.isEmpty()){
			modelingExclusive.saveToSink(sink, problemDir+MODELING_EXCLUSIVE_FILE_NAME, encryptSpec);
			LOGGER.debug("Saved modeling exclusive dataset");
		}
		if(!calibrationExclusive.isEmpty()){
			calibrationExclusive.saveToSink(sink, problemDir+CALIBRATION_EXCLUSIVE_FILE_NAME, encryptSpec);
			LOGGER.debug("Saved calibration exclusive dataset");
		}

		// Save Transformers list
		if (!transformers.isEmpty()) {
			doSaveTransformers(sink, problemDir);
		}

		LOGGER.debug("Saved Dataset to DataSink");
	}

	// ============================================================
	//   READ DATA
	// ============================================================

	public void loadTransformersFromSource(DataSource src) throws IOException {
		loadTransformersFromSource(src, null);
	}

	public void loadTransformersFromSource(DataSource src, String basePath) throws IOException {
		try {
			String base = DataIOUtils.locateBasePath(src, basePath, TRANSFORMER_BASE_FILE_NAME);
			doLoadTransformers(src, base);
		} catch (IOException e) {
			// No transformers saved
			LOGGER.debug("No transformers saved");
		}
	}

	private void doLoadTransformers(final DataSource src, final String baseName) 
			throws IOException {

		if (src.hasEntry(baseName + 0)) {  
			transformers = new ArrayList<>();
			// At least one transformer has been saved!
			int index = 0;
			while (true) {
				String tPath = baseName + index; 
				// Check if there is more transformers saved - break if not
				if (!src.hasEntry(tPath))
					break;

				try (ObjectInputStream ois = new ObjectInputStream(src.getInputStream(tPath));){
					Transformer t = (Transformer)ois.readObject();
					transformers.add(t);
					LOGGER.debug("Successfully loaded Transformer {}", t);
				} catch (Exception e) {
					LOGGER.debug("Failed loading transformer at path: {}", tPath, e);
					throw new IOException("Failed loading Transformer from model");
				}
				index++;
			}
			LOGGER.debug("Loaded {} transformers from model",transformers.size());
		}

	}

	@Override
	public void loadFromDataSource(DataSource src, String path, EncryptionSpecification encryptSpec)
			throws IOException, IllegalArgumentException, InvalidKeyException {
		LOGGER.debug("Trying to load Dataset from path={}",path);
		String dataBaseDir = DataIOUtils.appendTrailingDash(DataIOUtils.locateBasePath(src, path, PROBLEM_DIRECTORY_NAME));
		LOGGER.debug("Dataset dir={}",dataBaseDir);

		boolean hasLoadedData=false;
		if (src.hasEntry(dataBaseDir+DATASET_FILE_NAME)){
			dataset.loadFromSource(src, dataBaseDir+DATASET_FILE_NAME, encryptSpec);
			hasLoadedData=true;
			LOGGER.debug("Loaded dataset from source");
		} if (src.hasEntry(dataBaseDir+CALIBRATION_EXCLUSIVE_FILE_NAME)){
			calibrationExclusive.loadFromSource(src, dataBaseDir+CALIBRATION_EXCLUSIVE_FILE_NAME, encryptSpec);
			hasLoadedData=true;
			LOGGER.debug("Loaded calibration exclusive dataset from source");
		} if(src.hasEntry(dataBaseDir+MODELING_EXCLUSIVE_FILE_NAME)){
			modelingExclusive.loadFromSource(src, dataBaseDir+MODELING_EXCLUSIVE_FILE_NAME, encryptSpec);
			hasLoadedData=true;
			LOGGER.debug("Loaded modeling exclusive dataset from source");
		}

		if (!hasLoadedData)
			throw new IllegalArgumentException("Could not locate any datasets in the DataSource");

		loadTransformersFromSource(src, dataBaseDir);

	}

	/**
	 * Loads a {@link Dataset}
	 * @param src A {@link DataSource} to load the problem from
	 * @param encryptSpec An {@link EncryptionSpecification} needed to decrypt the src, or <code>null</code> if not encrypted
	 * @throws IOException Any exception occurring when reading from IO
	 * @throws IllegalArgumentException If no data could be loaded (i.e. miss-matching of CPSign-version or non-cpsign model)
	 * @throws InvalidKeyException If data is encrypted with a different encryption key
	 */
	public void loadFromDataSource(DataSource src, EncryptionSpecification encryptSpec)
			throws IOException, IllegalArgumentException, InvalidKeyException {
		loadFromDataSource(src, null, encryptSpec);
	}


	// ============================================================
	//   OTHER METHODS
	// ============================================================

	/**
	 * Clear the current {@link Dataset} form all data and release memory
	 */
	public void clear(){
		dataset.clear();
		calibrationExclusive.clear();
		modelingExclusive.clear();
	}

	/**
	 * Checks if the complete dataset is empty, including all of the <code>SubSets</code>.
	 * @return <code>true</code> if all of the datasets are empty, <code>false</code> otherwise
	 */
	public boolean isEmpty(){
		return dataset.isEmpty() && modelingExclusive.isEmpty() && calibrationExclusive.isEmpty();
	}


	/**
	 * Use System.currentTimeMillis as random seed,
	 * shuffles all datasets (all done individually)
	 */
	public void shuffle(){
		shuffle(GlobalConfig.getInstance().getRNGSeed());
	}


	/**
	 * Use an explicit random seed for shuffling. Shuffle all datasets
	 * @param randomSeed the RNG seed to use
	 */
	public void shuffle(long randomSeed) {
		dataset.shuffle(randomSeed);
		modelingExclusive.shuffle(randomSeed);
		calibrationExclusive.shuffle(randomSeed);
	}

	/**
	 * Checks if two {@link Dataset} are identical, into the order of records
	 * @return <code>true</code> if the {@link Object}s are identical, otherwise <code>false</code>
	 */
	@Override
	public boolean equals(Object o){
		if (this== o)
			return true;

		if (! (o instanceof Dataset)){
			LOGGER.debug("object not a Dataset");
			return false;
		}

		Dataset other = (Dataset) o;

		if (! dataset.equals(other.dataset)){
			LOGGER.debug("SubSet 'dataset' does not equal in the two Datasets");
			return false;
		}

		if (! modelingExclusive.equals(other.modelingExclusive)){
			LOGGER.debug("SubSet 'modelExclusive' does not equal in the two Datasets");
			return false;
		}

		if (! calibrationExclusive.equals(other.calibrationExclusive)){
			LOGGER.debug("SubSet 'calibrationExclusive' does not equal in the two Datasets");
			return false;
		}
		return true;
	}

	/**
	 * Makes a deep copy of the {@link Dataset}
	 * @return a deep copy of the current {@link Dataset}
	 */
	@Override
	public Dataset clone() {

		Dataset clone = new Dataset();
		clone.dataset = dataset.clone();
		clone.calibrationExclusive = calibrationExclusive.clone();
		clone.modelingExclusive = modelingExclusive.clone();
		if (!transformers.isEmpty()) {
			// Copy the transformations as well
			for (Transformer t : transformers)
				clone.transformers.add(t.clone());
		}

		return clone;
	}

	public Dataset cloneDataOnly(){
		Dataset clone = new Dataset();
		clone.dataset = dataset.clone();
		clone.calibrationExclusive = calibrationExclusive.clone();
		clone.modelingExclusive = modelingExclusive.clone();
		return clone;
	}

	/**
	 * Adds the records of the <code>other</code> into this object. Makes a deep copy
	 * of the underlying data so the <code>other</code> will not be changed
	 * @param other Another {@link Dataset} to join into the current one
	 * @throws IllegalArgumentException If the <code>other</code> is the same {@link Dataset} as the current one or if the <code>other</code> is empty or indices are faulty
	 */
	public void join(Dataset other) throws IllegalArgumentException{
		if (this == other)
			throw new IllegalArgumentException("Cannot join the dataset with itself");
		if (other == null)
			throw new IllegalArgumentException("Cannot join the dataset with a null reference");

		dataset.join(other.dataset);
		modelingExclusive.join(other.modelingExclusive);
		calibrationExclusive.join(other.calibrationExclusive);

	}

	/**
	 * Performs a shallow join of the records from the <code>other</code>. Changing things in one of the
	 * data sets will alter the other one as well. 
	 * @param other Another {@link Dataset} to join into the current one
	 * @throws IllegalArgumentException If any of the two {@link Dataset}s are ill-formatted. 
	 */
	public void joinShallow(Dataset other) throws IllegalArgumentException {
		if (other == this)
			throw new IllegalArgumentException("Cannot join the problem with itself");
		if (other == null)
			throw new IllegalArgumentException("Cannot join the problem with a null reference");

		dataset.joinShallow(other.dataset);
		modelingExclusive.joinShallow(other.modelingExclusive);
		calibrationExclusive.joinShallow(other.calibrationExclusive);

	}

	@Override
	public Map<String, Object> getProperties() {
		Map<String,Object> props = new HashMap<>();
		props.put(PropertyNameSettings.NUM_FEATURES_KEY, getNumAttributes());
		props.put(PropertyNameSettings.NUM_OBSERVATIONS_KEY, getNumRecords());
		return props;
	}


}
