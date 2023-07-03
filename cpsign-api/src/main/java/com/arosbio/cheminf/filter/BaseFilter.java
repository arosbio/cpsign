package com.arosbio.cheminf.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.arosbio.cheminf.ChemFilter;
import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.TypeUtils;
import com.arosbio.commons.config.BooleanConfig;

public abstract class BaseFilter<T> implements ChemFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseFilter.class);
    private final List<String> APPLY_TO_PREDICTIONS_CONF_NAMES = Arrays.asList("applyToPredictions");
    private boolean applyToPredictions = true;

    abstract T getThis();

    public List<ConfigParameter> getConfigParameters() {
        List<ConfigParameter> params = new ArrayList<>();
		params.add(new BooleanConfig.Builder(APPLY_TO_PREDICTIONS_CONF_NAMES, true).description("If the filter should be applied to predictions").build());
		return params;
    }

    @Override
    public void setConfigParameters(Map<String, Object> params) throws IllegalStateException, IllegalArgumentException {
        Map<String,Object> unUsedParams = new HashMap<>();
		for (Map.Entry<String, Object> p : params.entrySet()) {
			if (CollectionUtils.containsIgnoreCase(APPLY_TO_PREDICTIONS_CONF_NAMES, p.getKey())) {
				applyToPredictions = TypeUtils.asBoolean(p.getValue());
			} else {
				unUsedParams.put(p.getKey(), p.getValue());
			}
		}
        LOGGER.debug("Unsed config parameters: {}", unUsedParams);
    }

    public T withApplyToPredictions(boolean apply){
        this.applyToPredictions = apply;
        return getThis();
    }

    @Override
    public boolean applyToPredictions() {
        return applyToPredictions;
    }
    
}
