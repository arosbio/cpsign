/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.gridsearch;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.config.Configurable.ConfigParameter;
import com.arosbio.commons.config.Configurable.Sorter;
import com.arosbio.commons.config.Configurable.Sorter.Direction;
import com.arosbio.commons.config.Configurable.Sorter.Priority;
import com.arosbio.commons.config.EnumConfig;
import com.arosbio.commons.config.IntegerConfig;
import com.arosbio.commons.config.NumericConfig;
import com.arosbio.ml.algorithms.impl.LibSvm;
import com.arosbio.ml.algorithms.impl.LibSvm.KernelType;
import com.arosbio.ml.algorithms.svm.LinearSVC;
import com.arosbio.ml.gridsearch.GridSearch.EvalStatus;
import com.arosbio.ml.gridsearch.GridSearch.GSResult;
import com.arosbio.ml.gridsearch.utils.GSResComparator;
import com.arosbio.ml.metrics.classification.F1Score;
import com.arosbio.ml.metrics.regression.RMSE;
import com.arosbio.tests.suites.UnitTest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

@Category(UnitTest.class)
public class TestGSResultComp {

    public static final PrintStream SYS_OUT = System.out;
    public static final PrintStream SYS_ERR = System.err;

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    public void printLogs() {
        SYS_ERR.println(systemErrRule.getLog());
        SYS_OUT.println(systemOutRule.getLog());
    }

    @Test
	public void testSortingGSResults() {
		List<GSResult> results = new ArrayList<>();
		results.add(GSResult.Builder.success(ImmutableMap.of(), 1d, new RMSE(), 1000).build());
		results.add(GSResult.Builder.success(ImmutableMap.of(), 2d, new RMSE(), 1000).build());
		results.add(GSResult.Builder.success(ImmutableMap.of(), .5d, new RMSE(), 1000).build());
		results.add(GSResult.Builder.success(ImmutableMap.of(), Double.NaN, new RMSE(), 1000).build());

		Collections.sort(results, new GSResComparator(new LinearSVC()));

		Assert.assertEquals(.5d, results.get(0).getResult(), 0.0001);
		Assert.assertEquals(1d, results.get(1).getResult(), 0.0001);
		Assert.assertEquals(2d, results.get(2).getResult(), 0.0001);
		Assert.assertEquals(Double.NaN, results.get(3).getResult(), 0.0001);
		// SYS_ERR.println(results);
		// printLogs();

		// Sort using runtime when identical results
		results.add(GSResult.Builder.success(ImmutableMap.of(), 1d, new RMSE(), 900).build());
		Collections.sort(results, new GSResComparator(new LinearSVC()));
		// Both 1 and 2 results should have score 1.0 (the 0.5 should be the best)
		Assert.assertEquals(1d, results.get(1).getResult(), 0.0001);
		Assert.assertEquals(1d, results.get(2).getResult(), 0.0001);
		Assert.assertEquals(900, results.get(1).getRuntime()); // Fastest should be first!

		// SYS_ERR.println(results);
	}

    @Test
    public void testSortGridResults() {
        List<GSResult> resList = new ArrayList<>();
        GSResult failed = GSResult.Builder.failed(ImmutableMap.of("C", 10), new F1Score(), EvalStatus.FAILED, "").build();
        resList.add(failed);
        resList.add(GSResult.Builder.failed(ImmutableMap.of("C", 10), new F1Score(), EvalStatus.FAILED, "").build());
        resList.add(GSResult.Builder.failed(ImmutableMap.of("C", 10), new F1Score(), EvalStatus.FAILED, "").build());
        resList.add(GSResult.Builder.failed(ImmutableMap.of("C", 10), new F1Score(), EvalStatus.FAILED, "").build());
        Collections.sort(resList, new GSResComparator(new LinearSVC()));

        System.err.println(resList);

        // SORT BASED ON RUNTIME (SAME OTHER ONES)
        resList.clear();
        // One failed
        resList.add(failed);
        // Some successes
        GSResult fast = GSResult.Builder.success(ImmutableMap.of("C", 100, "param2", 78), .5, new F1Score(), 678).build();
        GSResult slow = GSResult.Builder.success(ImmutableMap.of("C", 100, "param2", 79), .5, new F1Score(), 45678).build();
        resList.add(slow);
        resList.add(fast);
        Collections.sort(resList, new GSResComparator(new LinearSVC()));
        Assert.assertEquals(fast, resList.get(0));
        Assert.assertEquals(slow, resList.get(1));
        Assert.assertEquals(failed, resList.get(2));

        // SORT BASED ON LOWER Cost
        GSResult lowerC = GSResult.Builder.success(ImmutableMap.of("C", 10, "param2", 78), .5, new F1Score(), 67878).build();
        resList.add(lowerC);
        Collections.sort(resList, new GSResComparator(new LinearSVC()));
        Assert.assertEquals(lowerC, resList.get(0));
        Assert.assertEquals(fast, resList.get(1));
        Assert.assertEquals(slow, resList.get(2));
        Assert.assertEquals(failed, resList.get(3));

        // DIFFERENT SCORES
        GSResult bestScore = GSResult.Builder.success(ImmutableMap.of("C", 10, "param2", 78), .75, new F1Score(),
                67878).build();
        GSResult worstScore = GSResult.Builder.success(ImmutableMap.of("C", 10, "param2", 78), .35, new F1Score(),
                67878).build();
        resList.add(bestScore);
        resList.add(worstScore);
        Collections.sort(resList, new GSResComparator(new LinearSVC()));
        Assert.assertEquals(bestScore, resList.get(0));
        Assert.assertEquals(lowerC, resList.get(1));
        Assert.assertEquals(fast, resList.get(2));
        Assert.assertEquals(slow, resList.get(3));
        Assert.assertEquals(worstScore, resList.get(4));
        Assert.assertEquals(failed, resList.get(5));

        List<ConfigParameter> sortConfs = new ArrayList<>();
        sortConfs.add(new NumericConfig.Builder(Arrays.asList("C"), 1).sorting(new Sorter(Priority.HIGH,Direction.PREFER_LOWER)).build());
        sortConfs.add(new NumericConfig.Builder(Arrays.asList("param2"), 1).sorting(new Sorter(Priority.MEDIUM,Direction.PREFER_HIGHER)).build());

        // This should sort primarily based on C and then secondarily on param2 (high vs medium)
        Collections.sort(resList,new GSResComparator(sortConfs));
        Assert.assertEquals(bestScore, resList.get(0)); // best score
        Assert.assertEquals(lowerC, resList.get(1)); // best of highest sorting prio
        Assert.assertEquals(slow, resList.get(2)); // then highest param2 
        Assert.assertEquals(fast, resList.get(3)); // then this one last 
        Assert.assertEquals(worstScore, resList.get(4));
        Assert.assertEquals(failed, resList.get(5));
        

        System.err.println(resList);

        // printLogs();
    }

    @Test
    public void testSortGridResultsMoreParamsInSort() {

        // Simulate more complex situation

        List<ConfigParameter> confs = Arrays.asList(
                new NumericConfig.Builder(Arrays.asList("C"), 6)
                        .sorting(new Sorter(Priority.HIGH, Direction.PREFER_HIGHER)).build(),
                new IntegerConfig.Builder(Arrays.asList("B"), 8)
                        .sorting(new Sorter(Priority.MEDIUM, Direction.PREFER_LOWER)).build(),
                new IntegerConfig.Builder(Arrays.asList("A"), 8)
                        .sorting(new Sorter(Priority.HIGH, Direction.PREFER_LOWER)).build(),
                new IntegerConfig.Builder(Arrays.asList("yt"), 8).build() // One without sorting - should not be keept
        );

        GSResComparator comp = new GSResComparator(confs);

        GSResult r1 = GSResult.Builder.success(ImmutableMap.of("A", 1, "B", 7, "C", 9), 7, new F1Score(), 789).build();
        GSResult r2 = GSResult.Builder.success(ImmutableMap.of("A", 2, "B", 4, "C", 10), 7, new F1Score(), 789).build();
        List<GSResult> resList = new ArrayList<>();
        resList.add(r1);
        resList.add(r2);
        Collections.sort(resList, comp); // Force calculate the sorting
        List<Pair<Sorter, String>> sortAlg = comp.getSortingList();

        // So 4 params, one without a sorting specified (should be excluded)
        Assert.assertEquals(3, sortAlg.size());
        // The 2 first have tha same priority (A and C)
        Assert.assertEquals(ImmutableSet.of("C", "A"),
                ImmutableSet.of(sortAlg.get(0).getRight(), sortAlg.get(1).getRight()));
        // The third should be the one with lowest priority
        Assert.assertEquals("B", sortAlg.get(2).getRight());

        System.err.println(sortAlg);
        System.err.println(resList);
        // printLogs();
    }

    @Test
    public void testGSResSortCustomSorter() {

        Comparator<Object> sortRBFfirst = new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                KernelType k1 = (KernelType)o1;
                KernelType k2 = (KernelType)o2;
                if (k1 == KernelType.RBF && k2 != KernelType.RBF){
                    return -1; // RBF SHOULD BE FIRST
                } else if (k2 == KernelType.RBF && k1 != KernelType.RBF){
                    return 1;
                }

                return 0;
            }
            
        };


        // Simulate more complex situation
        List<ConfigParameter> confs = Arrays.asList(
                        new EnumConfig.Builder<>(Arrays.asList("kernel"), EnumSet.allOf(LibSvm.KernelType.class),
                                KernelType.RBF)
                                .sorting(new Sorter(Priority.HIGH, Direction.PREFER_LOWER,sortRBFfirst)).build()
                );

        GSResComparator sorter = new GSResComparator(confs);

        GSResult resLINEAR = GSResult.Builder.success(ImmutableMap.of("kernel", KernelType.LINEAR), 7, new F1Score(), 789).build();
        GSResult resRBF = GSResult.Builder.success(ImmutableMap.of("kernel", KernelType.RBF), 7, new F1Score(), 7890).build();

        List<GSResult> resList = new ArrayList<>();
        resList.add(resLINEAR);
        resList.add(resRBF);
        Collections.sort(resList, sorter); // Force calculate the sorting
        Assert.assertEquals(1, sorter.getSortingList().size());
        Assert.assertEquals(resRBF, resList.get(0)); // RBF FIRST
        Assert.assertEquals(resLINEAR, resList.get(1)); // RBF FIRST
        // System.err.println(sorter.getSortingList());
        System.err.println(resList);
        // printLogs();

    }

    @Test
    public void testSortEnums(){

    }

}
