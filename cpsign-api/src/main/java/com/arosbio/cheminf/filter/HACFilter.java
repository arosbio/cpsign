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

import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.mixins.Aliased;

public class HACFilter extends BaseFilter<HACFilter> implements Aliased {

    public final static String NAME = "HAC";
    private final static String ALIAS = "HeavyAtomCount";
    private final static String DESCRIPTION = "Filter molecules based on their Heavy Atom Count (HAC).";
    private final static int DEFAULT_MIN_HAC = 5;

    private Integer minHAC = DEFAULT_MIN_HAC, maxHAC = null;

    HACFilter getThis(){
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
        return "HAC filter: ["+minHAC+".."+maxHAC+']';
    }

    @Override
    public String[] getAliases() {
        return new String[]{ALIAS};
    }

    public HACFilter withMinHAC(Integer min){
        this.minHAC = min;
        return this;
    }
    public int getMinHAC(){
        return minHAC != null? minHAC : 0;
    }

    public HACFilter withMaxHAC(Integer max){
        this.maxHAC = max;
        return this;
    }

    public int getMaxHAC(){
        return maxHAC != null? maxHAC : Integer.MAX_VALUE;
    }

    @Override
    public List<ConfigParameter> getConfigParameters() {
        List<ConfigParameter> params = new ArrayList<>();
		params.add(new IntegerConfig.Builder("min", DEFAULT_MIN_HAC).description("Min HAC value").build());
		params.add(new IntegerConfig.Builder("max", null).description("Max HAC value").build());
        params.addAll(super.getConfigParameters());
		return params;
    }

    @Override
    public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
        Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (p.getKey().equalsIgnoreCase("min")) {
                if (p.getValue() == null){
                    minHAC = null;
                } else {
                    minHAC = TypeUtils.asInt(p.getValue());
                }
			} else if (p.getKey().equalsIgnoreCase("max")) {
                if (p.getValue() == null){
                    maxHAC = null;
                } else {
                    maxHAC = TypeUtils.asInt(p.getValue());
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
        int hac = molecule.getAtomCount();

        if (minHAC != null && hac < minHAC){
            return false;
        }
        if (maxHAC != null && hac > maxHAC){
            return false;
        }
        return true;
    }

    @Override
    public String getDiscardReason(IAtomContainer molecule)
            throws IllegalArgumentException, CDKException {
        int hac = molecule.getAtomCount();

        if (minHAC != null && hac < minHAC){
            return String.format(Locale.ENGLISH, "Molecule had too low HAC (%d) vs required minimum (%d)", hac, minHAC);
        }
        if (maxHAC != null && hac > maxHAC){
            return String.format(Locale.ENGLISH, "Molecule had too high HAC (%d) vs required maximum (%d)", hac, maxHAC);
        }
        
        throw new IllegalArgumentException("Molecule should not be discarded");
    }

    @Override
    public HACFilter clone(){
        return new HACFilter()
            .withApplyToPredictions(applyToPredictions())
            .withMaxHAC(maxHAC)
            .withMinHAC(minHAC);
    }
    
}
