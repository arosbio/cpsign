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

import org.apache.commons.lang3.tuple.Pair;

/**
 * This project include an implementation of the isotonic regression algorithm described in 
 * Vovk, V., Petej, I., {@literal &} Fedorova, V. (2015, November). Large-scale probabilistic prediction with and without validity guarantees. In Proceedings of NIPS (Vol. 2015).
 * 
 * This algorithm relies on pre-computing the isotonic regression for all possible scores <i>s</i> and for both possible class labels ({@code 0} and {@code 1}). 
 * Those precomputed values are then stored in a binary search tree which give back the {@code p0} and {@code p1} values (i.e. the multi-probability for both 
 * of the possible labels, {@code y=0} and {@code y=1}, of the test-object). For further details we refer to the paper and the algorithms presented in the referenced paper.
 * 
 * @see <a href="http://alrw.net/articles/13.pdf">Vovk et al. 2015</a> 
 * @see <a href="https://github.com/ptocca/VennABERS">VennABERS python package</a>
 */
public class IsotonicRegressionCalibrator {

    protected static boolean DEBUG_MODE = false;

    /**
     * A point in 2D, with a x (the score to calibrate) vs the y (i.e. 0 or 1 for binary class labels)
     */
    public static class Point2D implements Comparable<Point2D> {
        public final double x,y;
        
        public Point2D(final double x, final double y){
            this.x = x;
            this.y = y;
        }

        /**
         * Vector addition, interpreting the points as vectors
         * @param toAdd vector to add to current
         * @return a new Point, interpreted as a vector with the result
         */
        public Point2D add(Point2D toAdd){
            return new Point2D(this.x+toAdd.x, this.y+toAdd.y);
        }
        /**
         * Vector subtraction, interpreting the points as vectors
         * @param toSub vector to subtract
         * @return a new Point, interpreted as a vector
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

    /**
     * A Point in 2D with a weight, i.e. when merging several points with the same x/score value.
     */
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

    /* 
     * =======================================================
     * Utility methods for the algorithms
     * =======================================================
     */

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

    private static WPoint2D toWeighted(Point2D p ){
        if (p instanceof WPoint2D){
            return new WPoint2D((WPoint2D)p);
        }
        return new WPoint2D(p);
    }



    /** Holds the list of unique and sorted calibration values */
    private List<WPoint2D> calibrationPoints;
    /** Holds the precomputed f0 and f1 values */
    private BST scores;

    // For testing purposes
    protected Stack<Point2D> firstStack, secondStack;
    protected double[] F1;
    protected double[] F0;


    private IsotonicRegressionCalibrator(List<WPoint2D> sortedPoints){
        this.calibrationPoints = sortedPoints;
        fit();
    }

    /**
     * Create an {@code IsotonicRegressionCalibrator} based on sorted and 'cleaned' points,
     * e.g. when fitting from a previously trained model.
     * @param points calibration scores
     * @return a fitted {@link IsotonicRegressionCalibrator} instance
     */
    public static IsotonicRegressionCalibrator fitFromClean(List<WPoint2D> points){
        return new IsotonicRegressionCalibrator(points);
    }

    /**
     * Create an {@code IsotonicRegressionCalibrator} based on raw calibration points,
     * which does not assume the points are sorted and merged for identical scores
     * @param <P> The type of the input
     * @param points calibration scores
     * @return a fitted {@link IsotonicRegressionCalibrator} instance
     */
    public static <P extends Point2D> IsotonicRegressionCalibrator fitFromRaw(List<P> points){
        // Create a copy and sort the list
        List<WPoint2D> copy = points.stream().map(p -> toWeighted(p))
            .collect(Collectors
            .toCollection(ArrayList::new)); // collect in a list that allows reshape and sorting
        Collections.sort(copy);

        // Merge points with identical scores
        for (int i = 0; i<copy.size()-1; i++){
            if (copy.get(i).equalX(copy.get(i+1))){
                copy.set(i, WPoint2D.merge(copy.get(i),copy.remove(i+1)));
                i--; // Re-do current index
            }
        }

        return new IsotonicRegressionCalibrator(copy);
    }

    /**
     * Get the cleaned calibration points (i.e. sorted and merged the ones with identical scores)
     * @return cleaned calibration points
     */
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

        this.scores = BST.build(f0, f1, calibrationPoints, kPrime);
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

        double[] F1 = new double[kPrime+2]; // First index i=0 will not be used, k'+1 = 1
        F1[kPrime+1] = 1; // k'+1 = 1 as defined in the paper
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

    // Algorithm 3
    private Stack<Point2D> computeCornersF0(Map<Integer, Point2D> csd, int kPrime){
        Stack<Point2D> s = new Stack<>();
        s.push(csd.get(kPrime+1));
        s.push(csd.get(kPrime));

        for (int i = kPrime-1; i >=0; i--){
            while (s.size()>1 && nonrightTurn(nextToTop(s), s.peek(),csd.get(i))){
                s.pop();
            }
            s.push(csd.get(i));
        }
        return s;
    }

    // Algorithm 4
    private double[] computeF0(Stack<Point2D> s, Map<Integer,Point2D> csd, int kPrime){
        // Reverse the stack
        Stack<Point2D> sPrime = new Stack<>();
        while(!s.empty()){
            sPrime.push(s.pop());
        }

        double[] F0 = new double[kPrime+1]; // First index i=0 will not be used
        for (int i=kPrime; i>0; i--){
            F0[i] = Math.abs(slope(sPrime.peek(), nextToTop(sPrime)));
            
            csd.put(i, csd.get(i-1).add(csd.get(i+1)).subtract(csd.get(i)));
            
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

    public Pair<Double,Double> calibrate(double score){
        return scores.search(score);
    }

    protected static class BST {

        static class Node {
            private Node left, right;
            public Pair<Double,Double> payload;
            public double key;

            private Node(){}
            public Node(double key){
                this.key = key;
            }

            public static Node leaf(Pair<Double,Double> payload){
                return new Node().payload(payload);
            }

            public static Node leaf(double v1, double v2){
                return leaf(Pair.of(v1, v2));
            }

            public Node payload(Pair<Double,Double> payload){
                this.payload = payload;
                return this;
            }
            public Node left(Node left){
                this.left = left;
                return this;
            }
            public Node right(Node right){
                this.right = right;
                return this;
            }

            public boolean isLeaf(){
                return left == null && right == null;
            }
        }

        private Node root;

        public Pair<Double,Double> search(double score){
            Node n = root;

            while (! n.isLeaf()){
                if ( score < n.key){
                    n = n.left;
                } else if (score > n.key){
                    n = n.right;
                } else {
                    return n.payload;
                }
            }
            return n.payload;
        }

        public static BST build(double[]f0, double[]f1, List<WPoint2D> calibrationScores, int kPrime){
            BST tree = new BST();
            tree.root = setupTree(f0, f1, calibrationScores, 1, kPrime);
            return tree;
        }

        private static Node setupTree(double[]f0, double[]f1, List<WPoint2D> calibrationScores, int a, int b){
            if (a == b){
                return new Node(calibrationScores.get(a-1).x)
                    .payload(Pair.of(f0[a],f1[a]))
                    .left(Node.leaf(Pair.of(f0[a-1],f1[a])))
                    .right(Node.leaf(Pair.of(f0[a],f1[a+1])));
            } else if (b == a+1){
                Node r = new Node(calibrationScores.get(a-1).x)
                    .payload(Pair.of(f0[a],f1[a]))
                    .left(Node.leaf(Pair.of(f0[a], f1[a])));
                r.right = setupTree(f0, f1, calibrationScores, b, b);
                return r;
            } else {
                int c = (a+b)/2;
                Node r = new Node(calibrationScores.get(c-1).x)
                    .payload(Pair.of(f0[c],f1[c]));
                r.left = setupTree(f0, f1, calibrationScores, a, c-1);
                r.right = setupTree(f0, f1, calibrationScores, c+1, b);
                return r;
            }
        }

        
    }
    
}
