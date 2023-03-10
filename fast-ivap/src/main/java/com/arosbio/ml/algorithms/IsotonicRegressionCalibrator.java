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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

public class IsotonicRegressionCalibrator {

    protected static boolean DEBUG_MODE = false;

    public static class Point2D implements Comparable<Point2D> {
        public final double x,y;
        
        public Point2D(final double x, final double y){
            this.x = x;
            this.y = y;
        }

        /**
         * Vector addition, interpreting the points as vectors
         * @param toAdd
         * @return
         */
        public Point2D add(Point2D toAdd){
            return new Point2D(this.x+toAdd.x, this.y+toAdd.y);
        }
        /**
         * Vector subtraction, interpreting the points as vectors
         * @param toSub
         * @return
         */
        public Point2D subtract(Point2D toSub){
            return new Point2D(this.x-toSub.x, this.y - toSub.y);
        }

        public static double cross(Point2D p1, Point2D p2){
            return p1.x*p2.y - p2.x*p1.y;
        }

        public boolean equalX(Point2D p){
            return this.x == p.x;
        }

        public int compareTo(Point2D p){
            return Double.compare(this.x, p.x);
        }

        public WPoint2D toWPoint(){
            return new WPoint2D(x, y);
        }

        public String toString(){
            return x+","+y;
        }
    }

    public static class WPoint2D extends Point2D {
        public final double w;

        public WPoint2D(double x, double y, double w){
            super(x,y);
            this.w = w;
        }
        public WPoint2D(double x, double y){
            this(x,y,1);
        }
        public WPoint2D(Point2D p){
            this(p.x,p.y);
        }
        public WPoint2D(WPoint2D p){
            this(p.x,p.y,p.w);
        }

        public double x(){
            return x;
        }

        public double y(){
            return y;
        }

        public double w(){
            return w;
        }

        public static WPoint2D merge(WPoint2D p1, WPoint2D p2){
            if (p1.x != p2.x)
                throw new IllegalArgumentException("Point p1 and p2 not for the same x value - should not be merged");
            return new WPoint2D(p1.x, (p1.y*p1.w+p2.y*p2.w) /(p1.w+p2.w), p1.w+p2.w);
        }

        public String toString(){
            return String.format(Locale.ENGLISH,"%.3f,%.3f,%.3f", x, y, w);
        }
    }

    

    private static <E> E nextToTop(Stack<E> s){
        return s.elementAt(s.size()-2);
    }
    private static double slope(Point2D p1, Point2D p2){
        return (p2.y - p1.y)/(p2.x - p1.x);
    }

    private static boolean atOrAbove(Point2D p, Point2D pa, Point2D pb){
        // Compute coefficients m and b in y=x*m+b from pa and pb
        double m = slope(pb,pa);
        double b = (pb.x*pa.y - pa.x*pb.y)/(pb.x - pa.x);
        return p.y >= p.x*m+b;
    }

    private static boolean nonleftTurn(Point2D p1, Point2D p2, Point2D p3){
        Point2D d1 = p2.subtract(p1);
        Point2D d2 = p3.subtract(p2);
        return Point2D.cross(d1, d2) <= 0;
    }

    private static boolean nonrightTurn(Point2D p1, Point2D p2, Point2D p3){
        Point2D d1 = p2.subtract(p1);
        Point2D d2 = p3.subtract(p2);
        return Point2D.cross(d1, d2) >= 0;
    }




    /** Holds the list of unique and sorted calibration values */
    private List<WPoint2D> calibrationPoints;

    // For testing purposes
    protected Stack<Point2D> firstStack, secondStack;
    protected double[] F1;
    protected double[] F0;


    private IsotonicRegressionCalibrator(List<WPoint2D> sortedPoints){
        this.calibrationPoints = sortedPoints;
        fit();
    }

    public static IsotonicRegressionCalibrator fitFromClean(List<WPoint2D> points){
        return new IsotonicRegressionCalibrator(points);
    }

    private static WPoint2D toWeighted(Point2D p ){
        if (p instanceof WPoint2D){
            return new WPoint2D((WPoint2D)p);
        }
        return new WPoint2D(p);
    }

    public static <P extends Point2D> IsotonicRegressionCalibrator fitFromRaw(List<P> points){
        // Create a copy and sort the list
        List<WPoint2D> copy = points.stream().map(p -> toWeighted(p))
            .collect(Collectors
            .toCollection(ArrayList::new)); // collect in a list that allows reshape and sorting
        Collections.sort(copy);

        // Merge points with identical scores
        int i = 0;
        for (i=0; i<copy.size()-1; i++){
            if (copy.get(i).equalX(copy.get(i+1))){
                copy.set(i, WPoint2D.merge(copy.get(i),copy.remove(i+1)));
                i--; // Re-do current index
            }
        }

        return new IsotonicRegressionCalibrator(copy);
    }

    public List<WPoint2D> getCalibrationPoints(){
        return calibrationPoints;
    }


    @SuppressWarnings("unchecked")
    private void fit(){
        int kPrime = calibrationPoints.size();

        // Compute the F1 list
        Map<Integer,Point2D> CSD = getCSD();
        CSD.put(-1, new Point2D(-1, -1));
        CSD.put(0, new Point2D(0, 0));
        Stack<Point2D> corners = computeCornersF1(CSD, kPrime);
        if (DEBUG_MODE){
            firstStack = (Stack<Point2D>) corners.clone();
        }
        
        double[] f1 = computeF1(corners, CSD, kPrime);
        if (DEBUG_MODE)
            F1 = f1.clone();


        // Compute the F0 list
        CSD = getCSD();
        CSD.put(0, new Point2D(0, 0));
        CSD.put(kPrime+1, CSD.get(kPrime).add(new Point2D(1, 0)));

        corners = computeCornersF0(CSD, kPrime);
        if (DEBUG_MODE){
            secondStack = (Stack<Point2D>) corners.clone();
        }
        double[] f0 = computeF0(corners, CSD, kPrime);
        if (DEBUG_MODE)
            F0 = f0.clone();

        // TODO - Set up BST for predictions

    }

    private Map<Integer,Point2D> getCSD(){
        Map<Integer,Point2D> CSD = new HashMap<>();
        // Go through all the points in the calibration set
        double xCumSum=0, yCumSum=0;
        int i = 1;
        for (WPoint2D pI : calibrationPoints){
            xCumSum += pI.w;
            yCumSum += pI.y*pI.w;
            CSD.put(i, new Point2D(xCumSum, yCumSum));
            i++;
        }
        return CSD;
    }

    // Algorithm 1
    private Stack<Point2D> computeCornersF1(Map<Integer,Point2D> csd, int kPrime){
        Stack<Point2D> s = new Stack<>();
        s.push(csd.get(-1));
        s.push(csd.get(0));

        // Go through all the points in the calibration set
        for (int i = 1; i <= kPrime; i++){
            while (s.size()>1 && nonleftTurn(nextToTop(s), s.peek(),csd.get(i))){
                s.pop();
            }
            s.push(csd.get(i));
        }
        return s;
    }



    // Algorithm 2
    private double[] computeF1(Stack<Point2D> s, Map<Integer,Point2D> csd, int kPrime){
        // Reverse the stack - into S'
        Stack<Point2D> sPrime = new Stack<>();
        while(!s.empty()){
            sPrime.push(s.pop());
        }

        double[] F1 = new double[kPrime+1]; // First index i=0 will not be used
        for (int i=1; i<=kPrime; i++){
            F1[i] = slope(sPrime.peek(), nextToTop(sPrime));
            csd.put(i-1, csd.get(i-2).add(csd.get(i)).subtract(csd.get(i-1)));
            
            if (atOrAbove(csd.get(i-1), sPrime.peek(),nextToTop(sPrime))){
                continue;
            }

            sPrime.pop();

            while (sPrime.size()>1 && nonleftTurn(csd.get(i-1), sPrime.peek(), nextToTop(sPrime))){
                sPrime.pop();
            }
            
            sPrime.push(csd.get(i-1));
        }

        return F1;
    }


    private Stack<Point2D> computeCornersF0(Map<Integer, Point2D> csd, int kPrime){
        Stack<Point2D> s = new Stack<>();
        s.push(csd.get(kPrime+1));
        s.push(csd.get(kPrime));

        // System.err.println("csd-size: " + csd.size() + " vs kPrime: " + kPrime);
        // Go through all the points in the calibration set
        for (int i = kPrime-1; i >=0; i--){
            while (s.size()>1 && nonrightTurn(nextToTop(s), s.peek(),csd.get(i))){
                s.pop();
            }
            s.push(csd.get(i));
        }
        return s;
    }

    private double[] computeF0(Stack<Point2D> s, Map<Integer,Point2D> csd, int kPrime){
        // Reverse the stack
        Stack<Point2D> sPrime = new Stack<>();
        while(!s.empty()){
            sPrime.push(s.pop());
        }

        double[] F0 = new double[kPrime+2]; // First index i=0 will not be used
        F0[kPrime+1] = 1; // as defined in the paper
        for (int i=kPrime; i>0; i--){
            F0[i] = slope(sPrime.peek(), nextToTop(sPrime));
            
            csd.put(i, csd.get(i-i).add(csd.get(i+1)).subtract(csd.get(i)));
            
            if (atOrAbove(csd.get(i), sPrime.peek(),nextToTop(sPrime))){
                continue;
            }

            sPrime.pop();

            while (sPrime.size()>1 && nonrightTurn(csd.get(i), sPrime.peek(), nextToTop(sPrime))){
                sPrime.pop();
            }
            
            sPrime.push(csd.get(i));
        }
        return F0;
    }
    
}
