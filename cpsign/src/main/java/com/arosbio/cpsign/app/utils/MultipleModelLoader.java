/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cpsign.app.utils;

import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.ZipException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.io.ModelSerializer;
import com.arosbio.encryption.EncryptionSpecification;
import com.arosbio.io.UriUtils;
import com.arosbio.ml.io.ModelIO.ModelType;

public class  MultipleModelLoader implements Iterator<Object>{

	private static final Logger LOGGER = LoggerFactory.getLogger(MultipleModelLoader.class);

	final private List<URI> resources;
	final private int numResources;
	private EncryptionSpecification spec;
	private int numSuccessfullyLoaded = 0;
	private int numFailed = 0;
	private URI lastLoadedURI;

	public MultipleModelLoader(List<String> resources, EncryptionSpecification spec) throws IOException {
		this.resources = UriUtils.getResources(resources);
		numResources = this.resources.size();
		this.spec = spec;
	}

	@Override
	public boolean hasNext() {
		return ! resources.isEmpty();
	}

	@Override
	public Object next() throws NoSuchElementException, FailedLoadingModelException {
		if (!hasNext())
			throw new NoSuchElementException("No more models to load");
		lastLoadedURI = resources.remove(0);
		LOGGER.debug("trying to load model from uri={}",lastLoadedURI);

		ModelType t = null; 
		Object loaded = null;
		try {
			t = ModelSerializer.getType(lastLoadedURI);
			LOGGER.debug("attempting to load new model of type {}", t);
			
			if (t == ModelType.CHEM_PREDICTOR){
				loaded = ModelSerializer.loadChemPredictor(lastLoadedURI,spec);
			} else if (t == ModelType.PLAIN_PREDICTOR){
				loaded = ModelSerializer.loadPredictor(lastLoadedURI, spec);
			} else if (t == ModelType.PRECOMPUTED_DATA){
				loaded = ModelSerializer.loadDataset(lastLoadedURI, spec);
			} 
		
			numSuccessfullyLoaded++;
		} catch (InvalidKeyException e) {
			numFailed++;
			LOGGER.debug("Failed loading model due to encryption",e);
			throw new FailedLoadingModelException(e.getMessage());
		} catch (IllegalArgumentException  e) {
			numFailed++;
			LOGGER.debug("Failed loading model due to IllegalArgumentException",e);
			throw new FailedLoadingModelException(e.getMessage());
		} catch (ZipException e) {
			numFailed++;
			LOGGER.debug("Failed loading model due to ZipException",e);
			throw new FailedLoadingModelException("Could not parse model from location: " + lastLoadedURI + " as a valid cpsign model");
		} catch (IOException e) {
			numFailed++;
			LOGGER.debug("Failed loading model due to IOException",e);
			throw new FailedLoadingModelException("Could not load model from location: " + lastLoadedURI + ", due to reason: "+e.getMessage());
		}

		if (loaded != null)
			return loaded;
		
		LOGGER.debug("Failed loading model of type {}", t);
		throw new FailedLoadingModelException("Could not load model from location: " + lastLoadedURI + ", due to reason: Model of unrecognized type");
	}

	public int getTotalNum() {
		return numResources;
	}

	public int getNumSuccessful() {
		return numSuccessfullyLoaded;
	}

	public int getNumFailed() {
		return numFailed;
	}
	
	public URI getLastLoadedURI() {
		return lastLoadedURI;
	}

	private static class FailedLoadingModelException extends RuntimeException {
		private static final long serialVersionUID = 6453000459700492139L;

		public FailedLoadingModelException(String msg) {
			super(msg);
		}
	}

}
