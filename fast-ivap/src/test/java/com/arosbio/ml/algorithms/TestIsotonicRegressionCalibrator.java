package com.arosbio.ml.algorithms;
/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import org.junit.Assert;
import org.junit.Test;

import com.arosbio.ml.algorithms.IsotonicRegressionCalibrator.Point2D;
import com.arosbio.ml.algorithms.IsotonicRegressionCalibrator.WPoint2D;
import com.arosbio.utils.Stopwatch;

public class TestIsotonicRegressionCalibrator {

    @Test
    public void testSetupOfCalibrationPoints(){
        List<WPoint2D> points = new ArrayList<>();
        points.add(new WPoint2D(-1,3));
        points.add(new WPoint2D(-1,2));
        
        List<WPoint2D> sorted = IsotonicRegressionCalibrator.fitFromRaw(points).getCalibrationPoints();
        Assert.assertEquals(1,sorted.size());
        WPoint2D p = sorted.get(0);
        Assert.assertEquals(-1,p.x,.0000001);
        Assert.assertEquals(2.5,p.y,.0000001);

        // New score - 2 values
        points.add(0,new WPoint2D(1,6));
        sorted = IsotonicRegressionCalibrator.fitFromRaw(points).getCalibrationPoints();
        Assert.assertEquals(2,sorted.size());

        // 
        points.add(new WPoint2D(1,5));
        points.add(new WPoint2D(1,5));
        sorted = IsotonicRegressionCalibrator.fitFromRaw(points).getCalibrationPoints();
        Assert.assertEquals(2,sorted.size());
        p = sorted.get(1);
        Assert.assertEquals(1,p.x,.0000001);
        Assert.assertEquals(16d/3,p.y,.0000001);

        points.add(0,new WPoint2D(3,5));
        points.add(2,new WPoint2D(3.5,5));
        sorted = IsotonicRegressionCalibrator.fitFromRaw(points).getCalibrationPoints();
        Assert.assertEquals(4,sorted.size());

        Assert.assertEquals("The given list should not be modified",7, points.size()); 
    }

    @Test
    public void testSerializePoints() throws IOException{
        // Old points from PAV algorithm had x,y,w all in floats 
        Assert.assertTrue(equals(new com.github.sanity.pav.Point(1.5,7.4,1), new WPoint2D(1.5, 7.4)));

        List<com.github.sanity.pav.Point> original = new ArrayList<>();
        for (int i=0; i<100; i++){
            original.add(new com.github.sanity.pav.Point(Math.random(), Math.random()));
        }
        Assert.assertEquals(100, original.size());
        
        // ByteArrayOutputStream calibStream = null;
        byte[] written = null;

        try(
                    ByteArrayOutputStream calibStream = new ByteArrayOutputStream(); 
					BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(calibStream));
					){
				writeCalibrationPoints(calibWriter, original);
                written = calibStream.toByteArray();
			}
        
        List<WPoint2D> loaded = null;
        try(InputStream buffStream = new ByteArrayInputStream(written)){
            loaded = loadCalibrationPoints(buffStream);
        }

        Assert.assertEquals(original.size(), loaded.size());

        for(int i=0;i<original.size(); i++){
            Assert.assertTrue(equals(original.get(i),loaded.get(i)));
        }

    }

    @Test
    public void testHowToPrint() throws IOException{
        // generate data to save
        List<WPoint2D> original = new ArrayList<>();
        for (int i=0; i<10000; i++){
            original.add(new WPoint2D(Math.random(), Math.random()));
        }

        Stopwatch sw = new Stopwatch();
        
        try(
            ByteArrayOutputStream calibStream = new ByteArrayOutputStream(); 
            BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(calibStream));
            ){
            sw.start();
            writeCalibrationPoints_1(calibWriter, original);
            sw.stop();
        }
        
        System.err.println("First: " + sw);

        try(
            ByteArrayOutputStream calibStream = new ByteArrayOutputStream(); 
            BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(calibStream));
            ){
            sw.start();
            writeCalibrationPoints_2(calibWriter, original);
            sw.stop();
        }
        
        System.err.println("Second: " + sw);

        try(
            ByteArrayOutputStream calibStream = new ByteArrayOutputStream(); 
            BufferedWriter calibWriter = new BufferedWriter(new OutputStreamWriter(calibStream));
            ){
            sw.start();
            writeCalibrationPoints_3(calibWriter, original);
            sw.stop();
        }
        
        System.err.println("Third: " + sw);

    }

    private void writeCalibrationPoints_3(BufferedWriter writer, List<WPoint2D> calibrationPoints) throws IOException{
        for (WPoint2D p : calibrationPoints) {
            writer.write(""+p.x +',' + p.y +','+p.w+ '\n');
			// writer.write(""+p.x);
			// writer.write(',');
			// writer.write(""+p.y);
			// writer.write(',');
			// writer.write(""+p.w);
			// writer.write(',');
			// writer.newLine();
		}
		writer.flush();
    }

    private void writeCalibrationPoints_2(BufferedWriter writer, List<WPoint2D> calibrationPoints) throws IOException{
        for (WPoint2D p : calibrationPoints) {
            writer.write(String.format("%f,%f,%f\n", p.x,p.y,p.w));
			// writer.write(""+p.x);
			// writer.write(',');
			// writer.write(""+p.y);
			// writer.write(',');
			// writer.write(""+p.w);
			// writer.write(',');
			// writer.newLine();
		}
		writer.flush();
    }
    
    private void writeCalibrationPoints_1(BufferedWriter writer, List<WPoint2D> calibrationPoints) throws IOException{
		for (WPoint2D p : calibrationPoints) {
			writer.write(""+p.x);
			writer.write(',');
			writer.write(""+p.y);
			writer.write(',');
			writer.write(""+p.w);
			writer.write(',');
			writer.newLine();
		}
		writer.flush();
		// LOGGER.debug("Saved calibration points to writer");
	}


    private void writeCalibrationPoints(BufferedWriter writer, List<com.github.sanity.pav.Point> calibrationPoints) throws IOException{
		for (com.github.sanity.pav.Point p : calibrationPoints) {
			writer.write(""+p.getX());
			writer.write(',');
			writer.write(""+p.getY());
			writer.write(',');
			writer.write(""+p.getWeight());
			writer.write(',');
			writer.newLine();
		}
		writer.flush();
		// LOGGER.debug("Saved calibration points to writer");
	}

    private List<WPoint2D> loadCalibrationPoints(InputStream calibrationStream) throws IOException{
        List<WPoint2D> calibrationPoints = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(calibrationStream))){
			
			String line =null;
			while ( (line=reader.readLine())!=null) {
				String[] splits = line.split(",");
				calibrationPoints.add(new WPoint2D(
						Double.parseDouble(splits[0]), 
						Double.parseDouble(splits[1]), 
						Double.parseDouble(splits[2])));
			}
		} catch (Exception e){
			throw e;
		}
        return calibrationPoints;
	}

    public static boolean equals(com.github.sanity.pav.Point p1, WPoint2D p2){
        return Double.compare(p1.getX(), p2.x) == 0 && Double.compare(p1.getY(), p2.y)==0 && Double.valueOf(p1.getWeight()).intValue() == p2.w;
    }


    // @Test
    public void testStackAccess(){
        Stack<Integer> s = new Stack<>();
        s.push(1);
        s.push(2);
        s.push(3);
        System.err.println(s.elementAt(s.size()-2));
    }


    List<Double> xs = Arrays.asList(0.93900124, -4.1276636 ,  9.36408782, -5.47607398, -9.68523521,
    -3.48290316,  0.05018794, -9.43274132,  1.184966  ,  7.4856555 ,
     4.09464389,  2.45936646,  9.11923501,  9.16558668,  6.48532939,
     2.15483695, -0.24470881, -9.73367774,  2.12523812,  9.78176151,
     6.362021  , -3.18790712, -6.9590595 ,  5.68117229,  4.87875641,
     9.34093584,  7.49684724,  1.11325251, -7.97431506, -0.32998683,
    -3.72609892,  0.24816964, -3.96596851,  7.23645984,  6.88654028,
    -3.69069686,  1.99162693, -1.39638285,  8.18185514, -6.25278197,
     3.95456804,  9.40750656, -6.49448961, -5.96067143,  3.87446677,
     5.58307849, -0.1890189 ,  2.1937293 , -5.74635182, -0.46771543,
    -7.75856238, -3.57156149, -4.3044059 , -1.1074928 ,  8.60252724,
    -6.37464676, -1.97223507,  2.31194438,  8.93114172, -7.33703617,
     8.35753234, -8.37892423, -0.38517206, -0.90820266, -5.80794542,
    -3.05080664, -0.91669463,  7.30422926,  9.10128291,  0.37851357,
     7.40199581,  2.16343171, -3.01825316, -6.11611585, -1.73730456,
     0.45648566, -9.11113223, -7.08317668,  2.00368848, -5.49996811,
     6.74652752, -3.461155  , -7.90331585, -8.32938823,  8.74246041,
    -7.63959378, -7.18180472,  7.25332116, -4.91423739,  3.31902822,
     6.33451374,  2.1436128 ,  9.14977087,  4.17765799, -7.7449698 ,
     1.16820053,  4.36373052,  6.03914481, -9.47357329,  4.37757831,
     6.51361642,  4.93667622,  0.24698291, -0.83958074,  0.98837167,
     4.09287398,  8.45828581,  2.34070421,  7.75668613,  4.02513695,
    -8.63327245,  0.0165638 , -4.27027303, -4.29650144, -2.88144898,
    -3.70534456,  1.5721996 ,  3.67203001, -4.62501226, -7.40474755,
    -8.82382558,  1.5150569 , -6.27739667, -9.81504012,  8.55506159,
     0.74280838, -8.1510364 ,  6.85842224,  9.66405477, -1.02798676,
    -9.15020784, -7.64908118, -2.36692507,  7.71045275, -7.03922641,
     6.47980188, -9.7004748 , -0.85222602,  2.88794282, -8.79241036,
     2.29525501,  8.88808243, -6.7948016 ,  4.59222766,  2.18187796,
    -6.29767222, -9.87593171, -9.81431099,  0.64184818,  8.85558819,
     2.88597255,  4.28599693, -0.12269026,  1.63777885, -7.47264949,
     7.53641241,  5.21585257,  9.96397906, -4.04554105, -5.45964456,
    -7.49676684,  9.28419513,  5.61770369, -6.67350773,  1.05372942,
    -1.72463584, -6.97027984, -6.7585403 ,  9.26939989, -3.90071634,
     8.82878583, -8.48778654, -0.78393915, -7.40761903, -9.9042523 ,
     1.07532147, -7.7221179 ,  4.44049062,  3.96232751, -6.47334185,
     8.83484287,  4.42086817, -4.04059472,  4.18467528,  4.63860555,
    -3.15547337, -2.48822875, -2.81786985,  2.33236887,  8.00820293,
    -6.53613528,  7.5039922 , -9.44693687,  3.20677192, -1.71122254,
     5.82563104,  4.42396226, -0.39784386,  2.87728073,  0.03546261,
     6.23036941, -0.47832028,  0.4631198 , -4.98958827,  2.10086034,
    -3.94190383,  1.54568029, -6.60643769, -6.81061815, -1.65940518,
    -1.4636097 , -4.6378147 , -7.36806299, -9.21578922, -9.49536345,
    -4.5689942 , -0.76293116,  4.52486563, -0.50256599,  8.08101639,
    -9.29560391, -6.38678757, -3.22971014,  1.54992376,  7.05472316,
    -2.99596096, -4.64022635, -8.76221662,  6.42606955, -2.40667114,
     1.43100391,  9.67110836, -9.96810859, -7.09099719,  5.58221988,
     6.1025497 ,  5.38494238,  0.73997782,  9.57713962, -2.0763088 ,
     2.03887396, -8.73261991, -1.802851  ,  4.45000175, -5.22522318,
     8.87655178,  3.73566735, -4.24849234,  5.37997845, -8.33670456,
     9.49548845, -9.01429482,  8.66911782, -4.94292245,  5.15648215,
    -9.99852601, -4.91519822,  4.98201213,  0.64672142, -7.70095701,
    -2.12740508, -2.4890129 ,  1.36324488,  3.35954145,  6.81660485,
    -0.05537206, -2.15956565, -7.12046932,  6.0964593 ,  4.26740811,
    -1.82645205,  0.3686462 ,  3.30365689, -6.7038882 , -9.45604411,
    -3.64992602,  1.91170039, -0.26787817,  3.85109254,  6.39379611,
    -0.23115067, -7.31465953,  7.01256   ,  1.49980655,  4.79874962,
     4.09329307,  9.36423543, -4.09385355,  4.10613546, -2.68647334,
    -2.09178558, -5.38810727, -3.11979649,  8.96593503, -4.14858306,
    -5.08018788,  1.66275957, -4.83928087, -0.53228549,  6.68352513,
    -5.39199371, -1.46617176,  2.2097947 ,  0.91257849,  9.49446475,
     3.60740509,  4.79892484,  9.33911896, -1.7112395 , -2.8924037 ,
    -9.1227514 , -6.31591387, -5.25620736, -6.32991066,  5.09567786,
     0.71765963,  3.35267603,  6.40924322, -5.38452117, -3.4815212 ,
     4.16720534, -2.14482108, -9.4145812 , -1.30089562,  8.16546173,
    -1.81956977, -3.35502167,  9.79050166,  2.88831128, -2.68003947,
    -7.95960937,  5.7569889 ,  4.16149867,  8.43831598, -5.6544874 ,
    -7.701513  ,  4.48145053, -5.93208316, -6.47792324, -3.60385329,
     6.33650283,  0.7907321 , -9.0829929 , -0.72210651,  3.67959223,
     0.76736877,  1.44900436, -5.50445343,  6.95478663,  1.22797435,
     4.26492026,  9.63728434, -1.43602685,  7.62133225, -9.85437972,
    -9.33185415,  1.80559843, -3.77101209, -5.03446866, -4.44129286,
    -3.63194131,  4.57895392,  1.38391944,  5.78071945,  6.6039316 ,
     6.85869719, -1.70711702, -1.57453207,  8.5253176 ,  3.23527189,
    -8.39065632,  0.84373908, -2.87985481,  9.74869979, -9.72689187,
     2.24361746,  4.47246189, -4.22186457,  9.47283037,  7.19073254,
     8.31305682, -9.61535881,  1.39744303, -4.10699516,  6.9805727 ,
     2.65699314,  0.77754009, -7.70823661,  0.8044561 ,  2.63808293,
     9.11824619,  1.70102021,  9.34801203,  9.23212231,  3.00400672,
     0.11815968, -0.67956512,  7.80757122, -9.43486634, -7.72383604,
     0.93900124,  4.09464389,  6.362021  , -3.72609892,  3.95456804,
    -7.75856238,  8.35753234,  7.40199581,  6.74652752,  6.33451374,
     6.51361642, -8.63327245, -8.82382558, -9.15020784,  2.29525501,
     2.88597255, -7.49676684,  8.82878583,  8.83484287, -6.53613528,
     6.23036941, -1.4636097 , -9.29560391,  1.43100391,  2.03887396,
     9.49548845, -2.12740508, -1.82645205, -0.23115067, -2.09178558,
    -5.39199371, -9.1227514 ,  4.16720534, -7.95960937,  6.33650283,
     4.26492026, -3.63194131, -8.39065632,  8.31305682,  9.11824619 );
     List<Double> ys = Arrays.asList(1., 0., 1., 0., 0., 0., 0., 0., 0., 1., 1., 1., 1., 1., 1., 1., 1.,
     0., 1., 1., 1., 0., 1., 1., 1., 1., 1., 0., 0., 0., 0., 1., 0., 1.,
     1., 1., 1., 1., 1., 0., 0., 1., 0., 1., 1., 1., 0., 0., 0., 0., 0.,
     0., 0., 1., 1., 0., 1., 0., 1., 0., 1., 0., 0., 0., 0., 0., 1., 1.,
     1., 1., 1., 0., 0., 0., 1., 0., 0., 0., 0., 0., 1., 0., 0., 0., 1.,
     0., 0., 1., 1., 0., 1., 1., 1., 1., 0., 0., 1., 1., 0., 1., 1., 1.,
     0., 1., 1., 1., 1., 1., 1., 1., 0., 0., 0., 1., 0., 1., 0., 1., 0.,
     0., 0., 1., 0., 0., 1., 1., 0., 1., 1., 1., 0., 0., 1., 1., 0., 1.,
     0., 1., 1., 0., 1., 1., 0., 1., 0., 0., 0., 0., 1., 1., 1., 1., 1.,
     1., 0., 1., 1., 1., 0., 0., 0., 1., 1., 0., 1., 0., 0., 0., 1., 0.,
     1., 0., 1., 0., 0., 1., 0., 1., 1., 0., 1., 1., 1., 1., 1., 0., 1.,
     0., 1., 1., 0., 1., 0., 1., 0., 1., 1., 1., 0., 0., 1., 0., 1., 0.,
     0., 0., 1., 0., 0., 1., 0., 0., 0., 0., 0., 1., 1., 1., 1., 1., 0.,
     0., 0., 0., 1., 0., 1., 0., 1., 0., 0., 1., 0., 0., 1., 1., 1., 1.,
     1., 0., 1., 0., 0., 1., 0., 1., 0., 0., 1., 0., 1., 0., 1., 0., 1.,
     0., 0., 1., 1., 0., 1., 0., 1., 0., 1., 0., 1., 0., 1., 1., 1., 1.,
     0., 0., 0., 0., 1., 1., 1., 1., 1., 0., 1., 0., 1., 1., 1., 0., 1.,
     0., 0., 1., 0., 1., 0., 0., 0., 0., 1., 1., 0., 1., 0., 0., 1., 1.,
     1., 1., 1., 0., 0., 0., 0., 0., 1., 0., 0., 1., 0., 1., 1., 1., 0.,
     0., 1., 1., 0., 1., 0., 1., 0., 1., 1., 1., 0., 0., 1., 0., 0., 1.,
     1., 0., 0., 0., 1., 0., 0., 0., 1., 0., 1., 1., 0., 1., 0., 0., 0.,
     0., 0., 1., 0., 1., 1., 1., 1., 1., 1., 1., 1., 1., 0., 0., 0., 1.,
     0., 1., 1., 1., 1., 1., 1., 0., 0., 0., 1., 0., 1., 0., 1., 1., 1.,
     0., 1., 1., 1., 1., 1., 1., 0., 0., 0., 0., 0., 1., 1., 1., 0., 0.,
     0., 0., 0., 1., 1., 1., 0., 0., 1., 0., 0., 1., 0., 1., 1., 1., 0.,
     0., 0., 0., 0., 1., 1., 1., 0., 1., 0., 0., 1., 1., 0., 0.);

    @Test
    public void testVsPaolosPythonCode(){
        Assert.assertEquals(440, xs.size());
        Assert.assertEquals(440, ys.size());
        List<Point2D> originalPoints = new ArrayList<>();
        for (int i=0;i<440; i++){
            originalPoints.add(new Point2D(xs.get(i), ys.get(i)));
        }

        IsotonicRegressionCalibrator.DEBUG_MODE = true;
        IsotonicRegressionCalibrator calibrator = IsotonicRegressionCalibrator.fitFromRaw(originalPoints);
        Assert.assertEquals(400, calibrator.getCalibrationPoints().size());

        // Check that the stack is the same
        Assert.assertEquals(12, calibrator.firstStack.size());
        // System.err.println(calibrator.firstStack);

        // Check F1
        // System.err.println(Arrays.toString(calibrator.F1));
        Assert.assertEquals(401, calibrator.F1.length); // kPrime+1, i=0,...,k'

        // Check that the stack is the same
        // System.err.println(calibrator.secondStack);
        Assert.assertEquals(11, calibrator.secondStack.size());
        // Check F0
        // System.err.println(Arrays.toString(calibrator.F1));
        Assert.assertEquals(402, calibrator.F0.length); // kPrime + 2, i=1,...,k',k'+1), arrays start at 0
        // System.err.println(calibrator.F1.length);
    }  
    
}
