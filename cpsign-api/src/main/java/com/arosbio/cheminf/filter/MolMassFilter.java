/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.cheminf.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.commons.mixins.Aliased;

public class MolMassFilter extends BaseFilter<MolMassFilter> implements Aliased {

    public final static String NAME = "MolMass";
    private final static String ALIAS = "MolWeight";
    private final static String DESCRIPTION = "Filter molecules based on their molecular mass. Without setting the min/max values all molecules will be kept by this filter.";

    private double minMass = 0d, maxMass = Double.MAX_VALUE;

    MolMassFilter getThis(){
        return this;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getName() {
        return NAME;
    }

     public String toString(){
        return "MolMass filter: ["+minMass+".."+maxMass+']';
    }

    @Override
    public String[] getAliases() {
        return new String[]{ALIAS};
    }

    public MolMassFilter withMinMass(Double min){
        this.minMass = min;
        return this;
    }

    public MolMassFilter withMaxMass(Double max){
        this.maxMass = max;
        return this;
    }

    @Override
    public List<ConfigParameter> getConfigParameters() {
        List<ConfigParameter> params = new ArrayList<>();
		params.add(new NumericConfig.Builder("min", 0d).description("The minimum allowed molecular mass").build());
		params.add(new NumericConfig.Builder("max", Double.MAX_VALUE).description("The maximum allowed molecular mass").build());
        params.addAll(super.getConfigParameters());
		return params;
    }

    @Override
    public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
        Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase("min")) {
                if (p.getValue() == null){
                    minMass = 0;
                } else {
                    minMass = TypeUtils.asDouble(p.getValue());
                }
			} else if (p.getKey().equalsIgnoreCase("max")) {
                if (p.getValue() == null){
                    maxMass = Double.MAX_VALUE;
                } else {
                    maxMass = TypeUtils.asDouble(p.getValue());
                }
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}
		if (!unUsedParams.isEmpty())
			super.setConfigParameters(unUsedParams);
    }

    @Override
    public boolean keep(IAtomContainer molecule) throws IllegalArgumentException, CDKException {

        double mass = AtomContainerManipulator.getMass(molecule, AtomContainerManipulator.MolWeightIgnoreSpecified);
        if (mass < minMass){
            return false;
        }
        if (mass > maxMass){
            return false;
        }
        return true;
    }

    @Override
    public String getDiscardReason(IAtomContainer molecule)
            throws IllegalArgumentException, CDKException {
        double mass = AtomContainerManipulator.getMass(molecule, AtomContainerManipulator.MolWeightIgnoreSpecified);
        if (mass < minMass){
            return String.format(Locale.ENGLISH, "Molecule had too low molecular mass (%.3f) vs required minimum (%.3f)", mass, minMass);
        }
        if (mass > maxMass){
            return String.format(Locale.ENGLISH, "Molecule had too high molecular mass (%.3f) vs required maximum (%.3f)", mass, maxMass);
        }
        
        throw new IllegalArgumentException("Molecule should not be discarded");
    }


    @Override
    public MolMassFilter clone(){
        return new MolMassFilter()
            .withApplyToPredictions(applyToPredictions())
            .withMinMass(minMass)
            .withMaxMass(maxMass);
    }
    
}
