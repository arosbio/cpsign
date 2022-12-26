/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image.layout;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import org.apache.commons.lang3.tuple.Pair;

public class CustomLayout implements Layout {
	
	private final Boarder boarder;
	private final Padding padding;
	private final Margin margin;
	private final Pair<Integer,Integer> addedWidthLeftRight, addedHeightTopBottom;

	public static class Builder {
		private Boarder b;
		private Padding p;
		private Margin m;

		public Builder boarder(Boarder b){
			this.b = b;
			return this;
		}

		public Builder padding(Padding p){
			this.p = p;
			return this;
		}
		public Builder margin(Margin m){
			this.m = m;
			return this;
		}

		public CustomLayout build(){
			return new CustomLayout(this);
		}
	}
	
	private CustomLayout(Builder b){
		if (b.p == null && b.m == null && b.b == null)
			throw new IllegalArgumentException("Custom layout cannot be empty");

		this.padding = b.p != null? b.p : new Padding(0);
		this.boarder = b.b; // note - this may be null!
		this.margin = b.m != null? b.m : new Margin(0);
		int tmpL = padding.left + margin.left,
			tmpR = padding.right + margin.right,
			tmpT = padding.top + margin.top,
			tmpB = padding.top + margin.bottom;
		if (this.boarder != null){
			int w = this.boarder.getWidth();
			tmpL += w;
			tmpR += w;
			tmpT += w;
			tmpB += w;
		}
		this.addedHeightTopBottom = Pair.of(tmpT, tmpB);
		this.addedWidthLeftRight = Pair.of(tmpL,tmpR);

		if (tmpT+tmpB == 0 && tmpL+tmpR == 0)
			throw new IllegalArgumentException("Custom layout cannot be empty");
	}
	
	public int getAdditionalWidth(){
		return addedWidthLeftRight.getLeft() + addedWidthLeftRight.getRight();
	}

	@Override
	public Pair<Integer, Integer> getAddedLRWidth() {
		return addedWidthLeftRight;
	}

	public int getAdditionalHeight(){
		return addedHeightTopBottom.getLeft() + addedHeightTopBottom.getRight();
	}

	@Override
	public Pair<Integer, Integer> getAddedTBHeight() {
		return addedHeightTopBottom;
	}

	public Rectangle2D addLayout(Graphics2D g, Rectangle2D area){
		
		if (boarder!=null){
			boarder.paint(g, 
				(int) (area.getMinX() + margin.left), // x0
				(int) (area.getMinY() + margin.top), // y0
				(int) (area.getWidth() - margin.getW() ), // width
				(int) (area.getHeight() - margin.getH() )); // height
		}
		// Compute the output area
		Rectangle2D out =  new Rectangle2D.Double(
			area.getMinX()+addedWidthLeftRight.getLeft(),
			area.getMinY()+addedHeightTopBottom.getLeft(),
			area.getWidth() - getAdditionalWidth(),
			area.getHeight() - getAdditionalHeight());
		return out;
	}
	
	private static class BoxDimension {
		
		final int left, right, top, bottom;
		
		public BoxDimension(int top, int right, int bottom, int left){
			this.left = Math.max(0, left);
			this.right = Math.max(0, right);
			this.top = Math.max(0, top);
			this.bottom = Math.max(0, bottom);
		}
		
		public int getLeft(){
			return left;
		}
		
		public int getRight(){
			return right;
		}
		
		public int getTop(){
			return top;
		}
		
		public int getBottom(){
			return bottom;
		}

		public int getW(){
			return left+right;
		}
		public int getH(){
			return bottom+top;
		}
		
		public boolean isEmpty(){
			return left==0 && right==0 && top==0 && bottom == 0;
		}
	}
	
	public static class Padding extends BoxDimension implements Layout  {
		public Padding(int padding){
			this(padding, padding, padding, padding);
		}
		
		public Padding(int topBottom, int leftRight){
			this(topBottom, leftRight, topBottom, leftRight);
		}
		
		public Padding(int top, int right, int bottom, int left){
			super(top, right, bottom, left);
		}

		@Override
		public int getAdditionalWidth() {
			return getW();
		}

		@Override
		public Pair<Integer, Integer> getAddedLRWidth() {
			return Pair.of(left, right);
		}

		@Override
		public int getAdditionalHeight() {
			return getH();
		}

		@Override
		public Pair<Integer, Integer> getAddedTBHeight() {
			return Pair.of(top,bottom);
		}

		@Override
		public Rectangle2D addLayout(Graphics2D g, Rectangle2D area) {
			return new Rectangle2D.Double(
				area.getMinX()+left,
				area.getMinY()+top,
				area.getWidth()-left-right,
				area.getHeight()-top-bottom);
		}
	}
	
	public static class Margin extends BoxDimension implements Layout {
		public Margin(int margin){
			this(margin, margin, margin, margin);
		}
		
		public Margin(int topBottom, int leftRight){
			this(topBottom, leftRight, topBottom, leftRight);
		}
		
		public Margin(int top, int right, int bottom, int left){
			super(top, right, bottom, left);
		}

		@Override
		public int getAdditionalWidth() {
			return getW();
		}

		@Override
		public Pair<Integer, Integer> getAddedLRWidth() {
			return Pair.of(left, right);
		}

		@Override
		public int getAdditionalHeight() {
			return getH();
		}

		@Override
		public Pair<Integer, Integer> getAddedTBHeight() {
			return Pair.of(top,bottom);
		}

		@Override
		public Rectangle2D addLayout(Graphics2D g, Rectangle2D area) {
			return new Rectangle2D.Double(
				area.getMinX()+left,
				area.getMinY()+top,
				area.getWidth()-left-right,
				area.getHeight()-top-bottom);
		}
	}
	
	public static class Boarder {
		
		private static final double ROUNDING_FACTOR = 0.05;
		
		private final Color boarderColor;
		private final BasicStroke stroke;
		private final BoarderShape shape;
		
		public static class Builder {
			private BoarderShape shape = BoarderShape.RECTANGLE;
			private BasicStroke stroke = new BasicStroke();
			private Color color = Color.BLACK;

			public Builder shape(BoarderShape shape){
				this.shape = shape;
				return this;
			}
			public Builder stroke(BasicStroke stroke){
				if (stroke == null)
					throw new NullPointerException("Stroke cannot be null");
				this.stroke = stroke;
				return this;
			}
			public Builder color(Color color){
				this.color = color;
				return this;
			}
			public Boarder build(){
				return new Boarder(this);
			}

		}

		public enum BoarderShape {
			RECTANGLE, ROUNDED_RECTANGLE
		}
		
		private Boarder(Builder b) {
			this.stroke = b.stroke;
			this.boarderColor = b.color;
			this.shape = b.shape;
		}

		/**
		 * Returns the boarder width used (which will be the Math.ceil value of whatever was set in the {@link BasicStroke})
		 * @return the width
		 */
		public int getWidth(){
			return (int) Math.ceil(stroke.getLineWidth());
		}
		
		public void paint(Graphics2D g2d, int x0, int y0, int w, int h){
			Color tmpC = g2d.getColor();
			Stroke tmpStroke = g2d.getStroke();
			g2d.setColor(boarderColor);
			g2d.setStroke(stroke);
			
			w -= getWidth();
			h -= getWidth();
			
			if (shape == BoarderShape.ROUNDED_RECTANGLE){
				int archWidth = (int) Math.round(h*ROUNDING_FACTOR);
				g2d.drawRoundRect(x0, y0,
						w, 
						h, 
						archWidth, archWidth);
			} else {
				g2d.drawRect(x0, y0,
						w, 
						h);
			}
			// Set back the original settings
			g2d.setColor(tmpC);
			g2d.setStroke(tmpStroke);
		}

	}



}
