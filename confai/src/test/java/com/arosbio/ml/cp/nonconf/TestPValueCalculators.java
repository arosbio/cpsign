/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.ml.cp.nonconf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.arosbio.commons.CollectionUtils;
import com.arosbio.commons.MathUtils;
import com.arosbio.ml.cp.nonconf.calc.LinearInterpolationPValue;
import com.arosbio.ml.cp.nonconf.calc.NCSInterpolationHelper;
import com.arosbio.ml.cp.nonconf.calc.PValueCalculator;
import com.arosbio.ml.cp.nonconf.calc.SmoothedPValue;
import com.arosbio.ml.cp.nonconf.calc.SplineInterpolatedPValue;
import com.arosbio.ml.cp.nonconf.calc.StandardPValue;
import com.arosbio.tests.suites.UnitTest;

@Category(UnitTest.class)
public class TestPValueCalculators {

	private static List<Double> ncScores = new ArrayList<Double>();
	private static List<Double> nc2 = Arrays.asList(1d, 2d);
	private static List<Double> confs = Arrays.asList(0.0, 0.05, 0.1, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 1.0);

	private static List<Double> nc4_duplicates = Arrays.asList(1d, 2d, 2d, 4d);

	private static boolean DO_PRINT = false; 

	@BeforeClass
	public static void setup() {
		for (double i=1; i<=10; i+=1) {
			ncScores.add(i);
		}
		//		System.err.println("ncs:"+ncScores);
		//		System.err.println("confs:"+confs);
	}
	
//	@Test
	public void testInfTimesValue() {
		System.err.println(Double.POSITIVE_INFINITY*5);
	}


	/// STANDARD 

	@Test
	public void testStandardConf2NCS() {
		PValueCalculator estimator = new StandardPValue();
		assertFailIfNotBuilt(estimator);

		if (DO_PRINT) {
			printCurve(estimator, ncScores, confs);
			System.out.println("\n\n");
			printCurve(estimator, nc2, confs);
		}

		estimator.build(nc2);
		assertFailOutsideConfRegion(estimator);
		// lowest possible - should get lowest NCS
		Assert.assertEquals(Collections.min(nc2), estimator.getNCScore(0d), 0.00001);
		Assert.assertEquals(Collections.min(nc2), estimator.getNCScore(1d/3), 0.00001);
		// After 1/3 - next ncs
		Assert.assertEquals(Collections.max(nc2), estimator.getNCScore(1d/3+0.001), 0.00001);
		Assert.assertEquals(Collections.max(nc2), estimator.getNCScore(2d/3), 0.00001);
		// After 2/3 - infinity
		Assert.assertTrue(Double.isInfinite(estimator.getNCScore(2d/3+0.00001)) );
		Assert.assertTrue(Double.isInfinite(estimator.getNCScore(.95)) );

		estimator.build(ncScores);
		assertFailOutsideConfRegion(estimator);

		//		for (double c: confs) {
		//			System.out.println("conf:"+c+"\tnc:"+std.getNCScore(ncScores, c));
		//		}

		testGetPvalue_4ncs_Standard(estimator);
	}

	@Test
	public void testStandardNCS2pvalue() {
		PValueCalculator std = new StandardPValue();
		std.build(nc2);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(std, nc2);

		std.build(ncScores);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(std, ncScores);

	}


	// SMOOTHED

	@Test
	public void testSmoothedConf2Ncs() {
		PValueCalculator estimator = new SmoothedPValue();
		estimator.build(nc2);
		assertFailOutsideConfRegion(estimator);

		estimator.build(ncScores);
		assertFailOutsideConfRegion(estimator);

		assertFailIfNotBuilt(estimator);

		// when no NCS are identical, we should get the same result as the standard one
		PValueCalculator standard = new StandardPValue();
		standard.build(ncScores);
		PValueCalculator smooth = estimator.clone();
		smooth.build(ncScores);
		for (double c : confs) {
			Assert.assertEquals(standard.getNCScore(c), smooth.getNCScore(c), 0.000001);
		}

		testGetPvalue_4ncs_SMOOTHED(estimator);


	}

	@Test
	public void testSmoothedNcs2pvalue() {
		PValueCalculator estimator = new StandardPValue();
		estimator.build(nc2);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, nc2);

		estimator.build(ncScores);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, ncScores);
	}

	// Linear Interpolation

	@Test
	public void testLinearInterpolationConf2Ncs() {
		PValueCalculator estimator = new LinearInterpolationPValue();
		estimator.build(nc2);
		assertFailOutsideConfRegion(estimator);

		estimator.build(ncScores);
		assertFailOutsideConfRegion(estimator);

		assertFailIfNotBuilt(estimator);
		testGetPvalue_4ncs_Interpolation(estimator);
	}

	@Test
	public void testLinearInterpolationNcs2pvalue() {
		PValueCalculator estimator = new LinearInterpolationPValue();
		estimator.build(nc2);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, nc2);

		estimator.build(ncScores);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, ncScores);
	}

	// Spline Interpolation

	@Test
	public void testSplineInterpolationConf2Ncs() {
		PValueCalculator estimator = new SplineInterpolatedPValue();
		assertFailIfNotBuilt(estimator);
		try {
			estimator.build(nc2);
			Assert.fail();
		} catch (IllegalArgumentException e) {}
		//		estimator.build(nc2);
		//		assertFailOutsideConfRegion(estimator);

		estimator.build(ncScores);
		assertFailOutsideConfRegion(estimator);
		testGetPvalue_4ncs_Interpolation(estimator);
	}

	@Test
	public void testSplineInterpolationNcs2pvalue() {
		PValueCalculator estimator = new SplineInterpolatedPValue();
		//		estimator.build(nc2);
		//		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, nc2);

		estimator.build(ncScores);
		assertMinOrMaxPvalueLargerSmallerNCSthanFound(estimator, ncScores);
	}

	@Test
	public void testSplineInterpol(){
		Random r = new Random(567890);
		int n_test = 10;
		List<Double> ncs0 = new ArrayList<>(), ncs1 = new ArrayList<>();


		for (int i = 0; i<n_test; i++){
			double p0 = r.nextDouble();
			ncs0.add(p0);
			ncs1.add(1-p0);
		}

		SplineInterpolatedPValue calc = new SplineInterpolatedPValue();
		calc.build(ncs0);
		List<Double> pvals = new ArrayList<>();
		List<Double> rev = new ArrayList<>();

		for (double d : CollectionUtils.listRange(0, 1, 0.01)){
			pvals.add(calc.getPvalue(d));
			rev.add(calc.getNCScore(d));
		}
		
		// Over the p-values
		Assert.assertEquals(1d/11, MathUtils.min(pvals),0.00001);
		Assert.assertEquals(1d, MathUtils.max(pvals),0.00001);

		// Over the nonconformity scores
		Assert.assertEquals(MathUtils.min(ncs0), MathUtils.min(rev),0.00001);
		Assert.assertTrue(Double.isInfinite(MathUtils.max(rev)));
		double maxNoInf = rev.stream().filter(d -> !d.isInfinite()).reduce(Math::max).get();
		Assert.assertEquals(MathUtils.max(ncs0), maxNoInf,0.00001);
		
	}
	
	// @Test
	public void testSplinesFail() {
		List<Double> scores = Arrays.asList(0.025471188130503704, 0.040920329179098984, 0.053767299813386266, 0.21923301905847378, 0.8231111624862107, 0.8254958836557013, 0.9739594280368261, 1.0668113084484547, 1.1016929776972415, 1.1106761412530957, 1.1337659278408283, 1.3432533579353894, 1.4669405524658297, 1.7545665542382927, 1.8234847141524364, 1.84601905342033, 1.8834717600542261, 2.347505723753928, 2.6998991528616774, 2.764477388008516, 2.936015247403641, 3.2499461248333446, 3.8267363678498634, 4.172847325573735, 4.22221111312298, 4.39127452544661, 4.654250920410693, 5.904234159280101, 6.106822674680226, 7.059585687555863, 7.107934004755621, 7.679509339666554, 8.489584657100718, 8.954077317118166, 9.787921074719064, 10.101277996331431, 11.287641334460465, 11.610236957854957, 13.199529290300358, 14.147131258696971, 14.757881893494543, 17.579263288408534, 18.49271406127104, 19.29705165601318, 21.592457276158306, 23.343043649766283, 38.403424908411026, 38.56769375834052, 77.32560555728047, 954.2800391002047);
		Pair<double[], double[]> xy = NCSInterpolationHelper.getConfidence2NCS(scores);
		double[] x = xy.getLeft();
		double[] y = xy.getRight();
		
		System.err.print("x=[");
		for (int i=0;i<x.length;i++)
			System.err.print(x[i]+",");
		System.err.println("]");
		
		System.err.print("y=[");
		for (int i=0;i<y.length;i++)
			System.err.print(y[i]+",");
		System.err.println("]");
		
		
//		for (int i=0;i<x.length;i++)
//			System.out.println(x[i] + " " + y[i]);
		double min = x[0];
		double max = x[x.length-1];
		UnivariateInterpolator interpol = new SplineInterpolator();
		UnivariateFunction func = interpol.interpolate(x, y);
		
		System.err.println("X,Y");
		for (double c : CollectionUtils.listRange(min, max, 0.001)) {
			System.err.println(c+","+func.value(c));
		}
	}
	
	
	@Test
	public void testSplinesCannotGiveNegativeValues() throws Exception {
		SplineInterpolatedPValue splines = new SplineInterpolatedPValue();
		List<Double> scores = Arrays.asList(0.025471188130503704, 0.040920329179098984, 0.053767299813386266, 0.21923301905847378);
//		List<Double> scores = Arrays.asList(0.011603284665476046, 0.17172137887730615, 0.18947119605712756, 0.41798059593078357, 0.7078893722141762, 1.0863895906494054, 1.2092222436224143, 1.2157672444840384, 1.4420949122527582, 1.5528900735420268, 1.6060468066288196, 1.6590110710948873, 1.7189213755144253, 2.14252444293171, 2.289631488842299, 2.3361865470590697, 2.5496929722060995, 2.580068618396224, 2.7702952325696812, 3.2122136207156475, 3.3045559075567628, 3.559899611442651, 4.029409208696232, 4.336698229612697, 4.4569373336573195, 4.604064053777524, 5.157588133480521, 5.700253711332983, 6.218451947758096, 6.481009198468972, 6.706504200108331, 7.29431336857598, 7.412311229287037, 7.946155345242293, 8.535931630937187, 9.448502679137565, 10.494239470270182, 10.736766767578725, 12.835189906750951, 16.832044364996356, 17.72647964097937, 21.67445884835068, 21.738083663167988, 22.167613682313846, 22.817129699828104, 23.05499803436829, 25.9990717372807, 26.869649302470094, 29.12040051061226, 35.60847681811573);
		// List<Double> scores = Arrays.asList(0.025471188130503704, 0.040920329179098984, 0.053767299813386266, 0.21923301905847378, 0.8231111624862107, 0.8254958836557013, 0.9739594280368261, 1.0668113084484547, 1.1016929776972415, 1.1106761412530957, 1.1337659278408283, 1.3432533579353894, 1.4669405524658297, 1.7545665542382927, 1.8234847141524364, 1.84601905342033, 1.8834717600542261, 2.347505723753928, 2.6998991528616774, 2.764477388008516, 2.936015247403641, 3.2499461248333446, 3.8267363678498634, 4.172847325573735, 4.22221111312298, 4.39127452544661, 4.654250920410693, 5.904234159280101, 6.106822674680226, 7.059585687555863, 7.107934004755621, 7.679509339666554, 8.489584657100718, 8.954077317118166, 9.787921074719064, 10.101277996331431, 11.287641334460465, 11.610236957854957, 13.199529290300358, 14.147131258696971, 14.757881893494543, 17.579263288408534, 18.49271406127104, 19.29705165601318, 21.592457276158306, 23.343043649766283, 38.403424908411026, 38.56769375834052, 77.32560555728047, 954.2800391002047);
		double minNCS = scores.get(0);
		double maxNCS = scores.get(scores.size()-1);
		splines.build(scores);

		// StringBuilder sb = new StringBuilder();
		// for (double conf=0;conf<=1;conf+=0.001) {
		// 	sb.append(conf).append(',').append(splines.getNCScore(conf)).append(',').append('\n');
		// }
		// try (FileWriter fw = new FileWriter("/Users/star/Desktop/scores_small_trunk.csv")){
		// 	fw.write(sb.toString());
		// }
		
		// Test conf -> NCS
		int n=scores.size();
		
		int numFailed = 0;
		for (double conf=0;conf<=1;conf+=0.001) {
			double ncs = splines.getNCScore(conf);
			if (ncs<minNCS) {
				System.err.println("ncs="+ncs+" for conf="+conf);
				numFailed++;
			}
//			Assert.assertTrue("ncs="+ncs+" for conf="+conf, ncs>=minNCS);
			
			if (conf <= ((double) n)/(n+1)) {
				Assert.assertTrue("min="+minNCS+",max="+maxNCS+";ncs="+ncs, ncs <= maxNCS);
			} else {
				Assert.assertTrue(Double.isInfinite(ncs));
			}
			
//			Assert.assertTrue("min="+minNCS+",max="+maxNCS+";ncs="+ncs,ncs>=minNCS && ncs <= maxNCS);
//			System.err.println("conf: "+conf + " ncs = " + ncs);
		}
		if (numFailed>0)
			Assert.fail("Num failed: " + numFailed);
		
		// test ncs -> p-value
		// StringBuilder sb = new StringBuilder();
		// for (double ncs=0;ncs<=1;ncs+=0.001) {
		// 	sb.append(ncs).append(',').append(splines.getPvalue(ncs)).append(',').append('\n');
		// }
		// try (FileWriter fw = new FileWriter("/Users/star/Desktop/ncs_to_pval_small_trunk.csv")){
		// 	fw.write(sb.toString());
		// }
		
		for (double ncs=minNCS-1; ncs< maxNCS+1; ncs+=0.01) {
			double pval = splines.getPvalue(ncs);
			Assert.assertTrue("pValue="+pval, pval<=1 && pval>= (1d/(n+1)));
//			System.out.println("ncs= " + ncs + " pvalue= " + pval);
		}
		
	}


	public void testGetPvalue_4ncs_Standard(PValueCalculator estimator) {
		PValueCalculator tmp = estimator.clone();
		tmp.build(nc4_duplicates);

		// NCS -> p-value
		Assert.assertEquals(1d, tmp.getPvalue(-100), 0.0001); // smaller then encountered before - should be 1!
		Assert.assertEquals(1d, tmp.getPvalue(.9999), 0.0001); // smaller then encountered before - should be 1!
		Assert.assertEquals(1d, tmp.getPvalue(1d), 0.0001); // identical with first ncs (all are larger)
		for (int i=1; i<10; i++) {
			Assert.assertTrue(1d > tmp.getPvalue(1d+i*.1));
			Assert.assertTrue("testing: " + (1d+i*.1), 4d/5 <= tmp.getPvalue(1d+i*.1));
		}
		Assert.assertEquals(4d/5, tmp.getPvalue(2d), 0.0001); // identical with second ncs (all but 1 larger, counting the record itself)
		for (int i=1; i<10; i++) {
			Assert.assertTrue(4d/5 > tmp.getPvalue(2d+i*.1));
			Assert.assertTrue(2d/5 <= tmp.getPvalue(2d+i*.1));
		}
		Assert.assertEquals(2d/5, tmp.getPvalue(4d), 0.0001); // identical with largest ncs (record itself + the identical in calib-set)
		Assert.assertEquals(1d/5, tmp.getPvalue(4.00001), 0.0001); // larger then encountered before - should be 1/(n+1)
		Assert.assertEquals(1d/5, tmp.getPvalue(100), 0.0001); // larger then encountered before - should be 1/(n+1)


		// Conf -> NCS
		Assert.assertEquals(1d, tmp.getNCScore(1d/5-0.0001), 0.0001);

		Assert.assertEquals(1d,  tmp.getNCScore(1d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(2d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(3d/5), 0.0001);
		Assert.assertEquals(4d,  tmp.getNCScore(4d/5), 0.0001);

		Assert.assertTrue(Double.isInfinite(tmp.getNCScore(4d/5+.0001)));

	}

	public void testGetPvalue_4ncs_Interpolation(PValueCalculator estimator) {
		PValueCalculator tmp = estimator.clone();
		tmp.build(nc4_duplicates);

		// NCS -> p-value
		Assert.assertEquals(1d, tmp.getPvalue(-100), 0.0001); // smaller then encountered before - should be 1!
		Assert.assertEquals(1d, tmp.getPvalue(.9999), 0.0001); // smaller then encountered before - should be 1!
		Assert.assertEquals(1d, tmp.getPvalue(1d), 0.0001); // identical with first ncs (all are larger)
		for (int i=1; i<10; i++) {
			Assert.assertTrue(4/5d >= tmp.getPvalue(1d+i*.1));
			Assert.assertTrue("testing: " + (1d+i*.1) + " got: " + tmp.getPvalue(1d+i*.1), 2d/5 <= tmp.getPvalue(1d+i*.1));
		}
		Assert.assertEquals(2d/5, tmp.getPvalue(2d), 0.0001); // identical with second ncs (all but 1 larger, counting the record itself)
		for (int i=1; i<20; i++) {
			Assert.assertTrue(3d/5 >= tmp.getPvalue(2d+i*.1));
			Assert.assertTrue(1d/5 <= tmp.getPvalue(2d+i*.1));
		}
		Assert.assertEquals(1d/5, tmp.getPvalue(4d), 0.0001); // identical with largest ncs (record itself + the identical in calib-set)
		Assert.assertEquals(1d/5, tmp.getPvalue(4.00001), 0.0001); // larger then encountered before - should be 1/(n+1)
		Assert.assertEquals(1d/5, tmp.getPvalue(100), 0.0001); // larger then encountered before - should be 1/(n+1)


		// Conf -> NCS
		Assert.assertEquals(1d, tmp.getNCScore(1d/5-0.0001), 0.0001);

		Assert.assertEquals(1d,  tmp.getNCScore(1d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(2d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(3d/5), 0.0001);
		Assert.assertEquals(4d,  tmp.getNCScore(4d/5), 0.0001);

		Assert.assertTrue(Double.isInfinite(tmp.getNCScore(4d/5+.0001)));

	}

	public void testGetPvalue_4ncs_SMOOTHED(PValueCalculator estimator) {
		PValueCalculator tmp = estimator.clone();
		tmp.build(nc4_duplicates);

		// NCS -> p-value
		pIn(tmp, -100, 4d/5, 1d); // smaller then encountered before
		pIn(tmp,.99999, 4d/5, 1d);
		pIn(tmp,1, 3d/5, 1d);
		for (int i=1; i<10; i++) {
			pIn(tmp,1+i*.1, 3d/5, 1d);
		}
		pIn(tmp, 2d, 1d/5, 4d/5);
		for (int i=1; i<20; i++) {
			pIn(tmp,2+i*.1, 1d/5, 2d/5);
		}
		pIn(tmp, 4d, 0d, 2d/5);
		pIn(tmp, 4.0001, 0d, 2d/5);
		pIn(tmp, 100, 0d, 2d/5);


		// Conf -> NCS
		Assert.assertEquals(1d, tmp.getNCScore(1d/5-0.0001), 0.0001);

		Assert.assertEquals(1d,  tmp.getNCScore(1d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(2d/5), 0.0001);
		Assert.assertEquals(2d,  tmp.getNCScore(3d/5), 0.0001);
		Assert.assertEquals(4d,  tmp.getNCScore(4d/5), 0.0001);

		Assert.assertTrue(Double.isInfinite(tmp.getNCScore(4d/5+.0001)));

	}

	private void pIn(PValueCalculator est, double ncs, Double low, Double high) {
		if (low != null)
			Assert.assertTrue("ncs="+ncs+", p="+est.getPvalue(ncs), low <= est.getPvalue(ncs));
		if (high != null)
			Assert.assertTrue("ncs="+ncs+", p="+est.getPvalue(ncs),high >= est.getPvalue(ncs));
	}



	public void assertFailIfNotBuilt(PValueCalculator estimator) {
		PValueCalculator tmp = estimator.clone();
		try {
			tmp.getNCScore(.5);
			Assert.fail();
		} catch (IllegalStateException e) {}
		try {
			tmp.getPvalue(.5);
			Assert.fail();
		} catch (IllegalStateException e) {}
	}

	public void assertMinOrMaxPvalueLargerSmallerNCSthanFound(PValueCalculator estimator, List<Double> ncsScores) {

		Assert.assertEquals(1d,estimator.getPvalue(Collections.min(ncsScores)-1d), 0.0001);
		Assert.assertEquals(1d/(ncsScores.size()+1),estimator.getPvalue(Collections.max(ncsScores)+1d),0.0001);
	}

	public void assertFailOutsideConfRegion(PValueCalculator ncsDist) {
		try {
			ncsDist.getNCScore(-0.0001);
			Assert.fail();
		} catch (IllegalArgumentException e) {}
		try {
			ncsDist.getNCScore(1.0001);
			Assert.fail();
		} catch (IllegalArgumentException e) {}
	}

	public static void printCurve(PValueCalculator ncExtractor, List<Double> nc, List<Double> confs) {
		ncExtractor.build(nc);
		for (double c: confs) {
			//			System.out.println("conf:"+c+" remain:"+ c*10 % 1);
			System.out.println("conf:"+c+"\tnc:"+ncExtractor.getNCScore(c));
		}
	}


	//	@Test
	//	public void testLinearInterpoolationExtractor() {
	//
	//		PValueCalculator ip = new LinearInterpolationPValue();
	//		ip.build(nc2);
	//
	//		printCurve(ip, nc2, confs);
	//
	//		Assert.assertEquals(0.5*(nc2.get(1)+nc2.get(0)), ip.getNCScore(.5), 0.000001);
	//
	//		//		
	//		//		for (double c: confs) {
	//		////			System.out.println("conf:"+c+" remain:"+ c*10 % 1);
	//		//			System.out.println("conf:"+c+"\tnc:"+std.getNCScore(nc2, c));
	//		//		}
	//	}

}
