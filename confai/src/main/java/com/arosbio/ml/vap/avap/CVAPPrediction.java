/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.vap.avap;

import java.util.List;
import java.util.Map;

public class CVAPPrediction<T> {

    final private List<Double> p0, p1;
    final private T label0,label1;
    final private Map<T,Double> probabilities;
    final private double meanInterval, medianInterval;
    
    public CVAPPrediction(List<Double> p0, 
        List<Double> p1,
        T label0, 
        T label1,
        Map<T,Double> probabilities,
        double meanW, 
        double medianW) 

        throws IllegalArgumentException {

        this.p0 = p0;
        this.p1 = p1;
        this.label0 = label0;
        this.label1 = label1;
        this.probabilities = probabilities;
        this.medianInterval = medianW;
        this.meanInterval = meanW;

        //  Do minor verifications
        if (p0 == null|| p1 == null){
            throw new IllegalArgumentException("Invalid CVAP prediction");
        }
        if (p0.size() != p1.size()){
            throw new IllegalArgumentException("Invalid CVAP prediction");
        }
       
    }

    public List<Double> getP0List(){
        return p0;
    }
    public List<Double> getP1List(){
        return p1;
    }

    public T getLabel0(){
        return label0;
    }

    public T getLabel1(){
        return label1;
    }
    
    public Map<T,Double> getProbabilities(){
        return probabilities;
    }

    public double getMeanP0P1Width(){
        return meanInterval;
    }
    
    public double getMedianP0P1Width(){
        return medianInterval;
    }
}
