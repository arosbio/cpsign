/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.metrics;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.GlobalConfig;
import com.arosbio.data.Dataset;
import com.arosbio.data.Dataset.SubSet;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.algorithms.svm.LinearSVR;
import com.arosbio.ml.cp.ConformalClassifier;
import com.arosbio.ml.cp.ConformalRegressor;
import com.arosbio.ml.cp.acp.ACPClassifier;
import com.arosbio.ml.cp.acp.ACPRegressor;
import com.arosbio.ml.cp.nonconf.classification.NegativeDistanceToHyperplaneNCM;
import com.arosbio.ml.cp.nonconf.regression.NormalizedNCM;
import com.arosbio.ml.metrics.plots.MergedPlot;
import com.arosbio.ml.metrics.plots.PlotUtils;
import com.arosbio.ml.metrics.regression.MAE;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.ml.sampling.RandomSampling;
import com.arosbio.ml.testing.KFoldCV;
import com.arosbio.ml.testing.RandomSplit;
import com.arosbio.ml.testing.TestRunner;
import com.arosbio.ml.vap.avap.AVAPClassifier;
import com.arosbio.tests.suites.UnitTest;
import com.arosbio.testutils.TestDataLoader;

@Category(UnitTest.class)
public class TestMetricAggregation {
    
    @Test
    public void testAggregateMAE(){
        MetricAggregation<MAE> agg = new MetricAggregation<>(new MAE());
        // Try aggregate wrong type of metric
        try{
            agg.addSplitEval(new RMSE());
            Assert.fail("Should not allow to aggregate the wrong type of argument");
        } catch (IllegalArgumentException e){}

        // One
        MAE m = agg.spawnNewMetricInstance();
        m.addPrediction(0, 1); // 1 
        m.addPrediction(.5, .75); // .25
        m.addPrediction(-1,-.8); // .2
        Assert.assertEquals(1.45/3, m.getScore(), 0.000001);
        agg.addSplitEval(m);
        Assert.assertEquals(m.getScore(), agg.getScore(), 0.000001);
        
        // Two
        m = agg.spawnNewMetricInstance();
        m.addPrediction(5,5.1); // .1
        m.addPrediction(4.5, 5); // 0.5
        Assert.assertEquals(.3, m.getScore(),0.000001);
        agg.addSplitEval(m);
        Assert.assertEquals(((1.45/3)+.3)/2, agg.getScore(),0.00001);

    }

    @Test
    public void testAggregatePlotCVAP() throws Exception {
        GlobalConfig.getInstance().setRNGSeed(21987);

        // First running with a single test-set (i.e. no aggregation will occur)
        // Too much hassle to generate things manually, use a small test-set
        Dataset data = TestDataLoader.getInstance().getDataset(true,true);
        SubSet[] sets = data.getDataset().splitRandom( .7); // Only use 70% of the data
        data.withDataset(sets[0]);
        // System.err.println(data);
        // System.err.println(data.getLabelFrequencies());
        AVAPClassifier vap = new AVAPClassifier(new LinearSVC(), new RandomSampling(1, .2));

        TestRunner runner = new TestRunner
            .Builder(new RandomSplit(12))
            .calcMeanAndStd(true)
            .build();
        
        List<Metric> metrics = runner.evaluate(data, vap);
        MergedPlot plot = PlotUtils.mergePlots(metrics);
        Map<String,List<Number>> p1 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p1.get(plot.getXlabel().label())));
        // Should not be any NaN values in this plot (given seed splits etc)
        checkNaNs(p1, false);
        // System.err.println(plot.getAsCSV(','));

        // Then check with K-fold CV
        runner = new TestRunner
            .Builder(new KFoldCV(10))
            .calcMeanAndStd(true)
            .build();
        
        metrics = runner.evaluate(data, vap);
        plot = PlotUtils.mergePlots(metrics,true);
        Map<String,List<Number>> p2 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p2.get(plot.getXlabel().label())));
        // Should be some NaN values in this plot (given seed splits etc)
        checkNaNs(p2, true);
        // System.err.println(plot.getAsCSV(','));
    }

    @Test
    public void testAggregateCPClassifierValues() throws Exception {
        GlobalConfig.getInstance().setRNGSeed(21987);

        // First running with a single test-set (i.e. no aggregation will occur)
        // Too much hassle to generate things manually, use a small test-set
        Dataset data = TestDataLoader.getInstance().getDataset(true,true);
        SubSet[] sets = data.getDataset().splitRandom( .7); // Only use 70% of the data
        data.withDataset(sets[0]);
        // System.err.println(data);
        // System.err.println(data.getLabelFrequencies());
        ConformalClassifier classifier = new ACPClassifier(new NegativeDistanceToHyperplaneNCM(new LinearSVC()),
             new RandomSampling(1, .2));

        TestRunner runner = new TestRunner
            .Builder(new RandomSplit(12))
            .calcMeanAndStd(true)
            .build();
        
        List<Metric> metrics = runner.evaluate(data, classifier);
        MergedPlot plot = PlotUtils.mergePlots(metrics);
        Map<String,List<Number>> p1 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p1.get(plot.getXlabel().label())));
        // Should not be any NaN values in this plot (given seed splits etc)
        checkNaNs(p1, false);
        // System.err.println(plot.getAsCSV(','));

        // Then check with K-fold CV
        runner = new TestRunner
            .Builder(new KFoldCV(10))
            .calcMeanAndStd(true)
            .build();
        
        metrics = runner.evaluate(data, classifier);
        plot = PlotUtils.mergePlots(metrics,true);
        Map<String,List<Number>> p2 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p2.get(plot.getXlabel().label())));
        // Should not be any NaN values here either
        checkNaNs(p2, false); // Non 
        // System.err.println(plot.getAsCSV(','));
    }

    @Test
    public void testAggregateCPRegressorValues() throws Exception {
        GlobalConfig.getInstance().setRNGSeed(21987);

        // First running with a single test-set (i.e. no aggregation will occur)
        // Too much hassle to generate things manually, use a small test-set
        Dataset data = TestDataLoader.getInstance().getDataset(false,true);
        // SubSet[] sets = data.getDataset().splitRandom( .7); // Only use 70% of the data
        // data.withDataset(sets[0]);
        // System.err.println(data);
        ConformalRegressor regressor = new ACPRegressor(new NormalizedNCM(new LinearSVR()),
             new RandomSampling(1, .2));

        TestRunner runner = new TestRunner
            .Builder(new RandomSplit(5))
            .calcMeanAndStd(true)
            .build();
        
        List<Metric> metrics = runner.evaluate(data, regressor);
        MergedPlot plot = PlotUtils.mergePlots(metrics);
        Map<String,List<Number>> p1 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p1.get(plot.getXlabel().label())));
        // Should not be any NaN values in this plot (given seed splits etc)
        checkNaNs(p1, false);
        // System.err.println(plot.getAsCSV(','));

        // Then check with K-fold CV
        runner = new TestRunner
            .Builder(new KFoldCV(10))
            .calcMeanAndStd(true)
            .build();
        
        metrics = runner.evaluate(data, regressor);
        plot = PlotUtils.mergePlots(metrics,true);
        Map<String,List<Number>> p2 = plot.getCurves();
        Assert.assertFalse("should not be any NaNs in the x-ticks",anyNaNs(p2.get(plot.getXlabel().label())));
        // Here there are some NaNs due to infinitly large intervals, computing the Standard deviation causes NaNs
        checkNaNs(p2, true); // Non 
        // System.err.println(plot.getAsCSV(','));
    }

    static void checkNaNs(Map<String,List<Number>> plot, boolean shouldContainNaN){
        boolean containsNaN = false;
        for (List<Number> row : plot.values()){
            containsNaN = containsNaN || anyNaNs(row);
        }
        Assert.assertEquals(shouldContainNaN, containsNaN);
    }

    static boolean anyNaNs(List<Number> values){
        boolean foundNaN = false;
        for (Number v : values){
            if (Double.isNaN(v.doubleValue())){
                foundNaN = true;
            }
        }
        return foundNaN;
    }


}
