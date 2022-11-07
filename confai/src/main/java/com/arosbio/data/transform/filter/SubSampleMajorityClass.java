/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.data.transform.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.GlobalConfig;
import com.arosbio.commons.MathUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;
import com.arosbio.commons.mixins.Aliased;
import com.arosbio.data.DataRecord;
import com.arosbio.data.DataUtils;
import com.arosbio.data.Dataset.RecordType;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.data.FeatureVector;
import com.google.common.collect.ImmutableList;

/**
 * Sub-sample the majority class (or classes) to the same frequency of the
 * minority class.
 */
public class SubSampleMajorityClass implements Filter, Aliased {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubSampleMajorityClass.class);

    public static final String DESCRIPTION = "Balance class occurrences by sub-sampling of the majority class. For multi-class data sets all classes will occur with the same frequency.";
    public static final String NAME = "SubSampleMajorityClass";
    public static final String ALIAS = "DownSampleMajorityClass";

    private transient TransformInfo info;
    private transient List<DataRecord> excludedRecs;

    private boolean inPlace = true;
    private boolean placeExcludedInModelExclusive = false;

    @Override
    public boolean isTransformInPlace() {
        return inPlace;
    }

    @Override
    public SubSampleMajorityClass transformInPlace(boolean inPlace) {
        this.inPlace = inPlace;
        return this;
    }

    public boolean isPlaceExcludedInModelData() {
        return placeExcludedInModelExclusive;
    }

    /**
     * This filter removes data exclusively from the
     * {@link com.arosbio.data.Dataset.RecordType#NORMAL normal} subset of
     * data, these can later be added to the
     * {@link com.arosbio.data.Dataset.RecordType#MODELING_EXCLUSIVE
     * model-exclusive} set of
     * records and used exclusively for learning the underlying model (i.e. not used
     * for calibration).
     * 
     * @param reuse {@code true} if excluded records should be added to the
     *              {@link com.arosbio.data.Dataset.RecordType#MODELING_EXCLUSIVE
     *              model-exclusive} data set
     * @return the calling instance (fluent API)
     */
    public SubSampleMajorityClass placeExcludedInModelData(boolean reuse) {
        this.placeExcludedInModelExclusive = reuse;
        return this;
    }

    @Override
    public void fit(Collection<DataRecord> data) throws TransformationException {
        // do nothing
    }

    @Override
    public boolean isFitted() {
        return true;
    }

    @Override
    public SubSet fitAndTransform(SubSet data) throws TransformationException {
        return transform(data);
    }

    @Override
    public SubSet transform(SubSet dataset) throws IllegalStateException {
        if (dataset.getDataType() == RecordType.NORMAL) {

            List<List<DataRecord>> stratas = DataUtils.stratify(dataset);
            Map<Integer, Integer> counts = new HashMap<>();
            for (int i = 0; i < stratas.size(); i++) {
                counts.put(i, stratas.get(i).size());
            }
            int numMinority = MathUtils.min(counts.values());
            LOGGER.debug(
                    "Found classes frequency according to (using indicies, not labels): {} with the min occurrence being: {}",
                    counts, numMinority);
            SubSet newSet = new SubSet();
            excludedRecs = new ArrayList<>();
            for (List<DataRecord> strata : stratas) {
                // If the minority class
                if (strata.size() == numMinority) {
                    newSet.addAll(strata);
                } else {
                    // Shuffle to sub-sample a random set
                    Collections.shuffle(strata, new Random(GlobalConfig.getInstance().getRNGSeed()));
                    newSet.addAll(strata.subList(0, numMinority));
                    excludedRecs.addAll(strata.subList(numMinority,strata.size()));
                }
            }
            // Shuffle again in order to not have all examples from the same class directly
            // after each other
            newSet.shuffle();
            info = new TransformInfo(excludedRecs.size(), 0);
            LOGGER.debug("Finished transforming subset with info: {}", info);

            if (inPlace) {
                dataset.setRecords(newSet);
                return dataset;
            } else {
                return newSet;
            }
        } else if (dataset.getDataType() == RecordType.MODELING_EXCLUSIVE 
                && placeExcludedInModelExclusive
                && excludedRecs != null
                && !excludedRecs.isEmpty()) {
            LOGGER.debug("Adding {} (previously excluded) records to the modeling-exclusive dataset", excludedRecs.size());
            if (inPlace){
                dataset.addAll(excludedRecs);
                dataset.shuffle();
                return dataset;
            } else {
                SubSet newSet = new SubSet(RecordType.MODELING_EXCLUSIVE);
                // Add all old records
                if (!dataset.isEmpty()){
                    newSet.addAll(dataset);
                }
                // Add the excluded records
                newSet.addAll(excludedRecs);
                newSet.shuffle();
                return newSet;
            }
        }
        
        return dataset;
    }

    @Override
    public FeatureVector transform(FeatureVector object) throws IllegalStateException, TransformationException {
        return object;
    }

    @Override
    public boolean appliesToNewObjects() {
        return false;
    }

    @Override
    public boolean applicableToClassificationData() {
        return true;
    }

    @Override
    public boolean applicableToRegressionData() {
        return false;
    }

    @Override
    public TransformInfo getLastInfo() {
        return info;
    }

    public List<DataRecord> getExcludedRecords() {
        return excludedRecs;
    }

    @Override
    public SubSampleMajorityClass clone() {
        SubSampleMajorityClass c = new SubSampleMajorityClass();
        c.inPlace = inPlace;

        return c;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String[] getAliases() {
        return new String[] { ALIAS };
    }

    @Override
    public String toString() {
        return NAME;
    }

    private static List<String> REUSE_CONF_NAMES = Arrays.asList("reuse","placeInModelExcl");
    @Override
    public List<ConfigParameter> getConfigParameters() {
        return ImmutableList.of(
            new BooleanConfig.Builder(REUSE_CONF_NAMES, false)
            .description("By setting this to true, the removed data can be placed in 'model exclusive' data - i.e. used to build underlying model(s) but never used for calibration or test-evaluation")
            .build()
        );
    }

    @Override
    public void setConfigParameters(Map<String, Object> params)
            throws IllegalStateException, IllegalArgumentException {
        if (params == null || params.isEmpty())
            return;
        
        for (Map.Entry<String,Object> p : params.entrySet()){
            try{
                if (CollectionUtils.containsIgnoreCase(REUSE_CONF_NAMES, p.getKey())) {
					placeExcludedInModelExclusive = TypeUtils.asBoolean(p.getValue());
				}
            } catch(Exception e){
                LOGGER.debug("Invalid config {} with value: {}", p.getKey(), p.getValue());
            }
        }
    }

}
