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

    @Override
    public String[] getAliases() {
        return new String[]{ALIAS};
    }

    public HACFilter withMinHAC(Integer min){
        this.minHAC = min;
        return this;
    }

    public HACFilter withMaxHAC(Integer max){
        this.maxHAC = max;
        return this;
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
    
}
