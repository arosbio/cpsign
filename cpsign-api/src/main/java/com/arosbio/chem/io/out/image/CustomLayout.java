/*
 * Copyright (C) Aros Bio AB.
 *
 * CPSign is an Open Source Software that is dual licensed to allow you to choose a license that best suits your requirements:
 *
 * 1) GPLv3 (GNU General Public License Version 3) with Additional Terms, including an attribution clause as well as a limitation to use the software for commercial purposes.
 *
 * 2) CPSign Proprietary License that allows you to use CPSign for commercial activities, such as in a revenue-generating operation or environment, or integrate CPSign in your proprietary software without worrying about disclosing the source code of your proprietary software, which is required if you choose to use the software under GPLv3 license. See arosbio.com/cpsign/commercial-license for details.
 */
package com.arosbio.chem.io.out.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class CustomLayout implements Layout {
	
	private final Boarder boarder;
	private final Padding padding;
	private final Margin margin;
	private final int addedW, addedH;

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
	
	public CustomLayout(Builder b){
		if (b.p == null && b.m == null && b.b == null)
			throw new IllegalArgumentException("Custom layout cannot be empty");

		this.padding = b.p != null? b.p : new Padding(0);
		this.boarder = b.b; // note - this may be null!
		this.margin = b.m != null? b.m : new Margin(0);
		int tmpW = padding.getW() + margin.getW();
		int tmpH = padding.getH() + margin.getH();
		if (this.boarder != null){
			tmpW += this.boarder.getWidth()*2;
			tmpH += this.boarder.getWidth()*2;
		}
		this.addedH = tmpH;
		this.addedW = tmpW;

		if (addedH == 0 && addedW == 0)
			throw new IllegalArgumentException("Custom layout cannot be empty");
	}
	
	public int getAdditionalWidth(){
		return addedW;
	}
	
	public int getAdditionalHeight(){
		return addedH;
	}

	
	public BufferedImage addLayout(BufferedImage img){
		
		BufferedImage withLayout = new BufferedImage(img.getWidth()+addedW, img.getHeight()+addedH, img.getType());
		Graphics2D g = withLayout.createGraphics();
		
		if (boarder!=null){
			boarder.paint(g, margin.left, margin.top, withLayout.getWidth()-margin.right-1, withLayout.getHeight()-margin.bottom-1);
		}
		
		int imgStartX = padding.left + (boarder!=null?boarder.getWidth():0)+margin.left;
		int imgStartY = padding.top + (boarder!=null?boarder.getWidth():0)+margin.top;
		g.drawImage(img, imgStartX, imgStartY, img.getWidth(), img.getHeight(), null);
		g.dispose();
		return withLayout;
	}
	
	private static class BoxSpace {
		
		final int left, right, top, bottom;
		
		public BoxSpace(int top, int right, int bottom, int left){
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
	
	public static class Padding extends BoxSpace {
		public Padding(int padding){
			this(padding, padding, padding, padding);
		}
		
		public Padding(int topBottom, int leftRight){
			this(topBottom, leftRight, topBottom, leftRight);
		}
		
		public Padding(int top, int right, int bottom, int left){
			super(top, right, bottom, left);
		}
	}
	
	public static class Margin extends BoxSpace {
		public Margin(int margin){
			this(margin, margin, margin, margin);
		}
		
		public Margin(int topBottom, int leftRight){
			this(topBottom, leftRight, topBottom, leftRight);
		}
		
		public Margin(int top, int right, int bottom, int left){
			super(top, right, bottom, left);
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
		
		public void paint(Graphics g, int x0, int y0, int x1, int y1){
			Graphics2D g2d = (Graphics2D) g;
			g2d.setColor(boarderColor);
			g2d.setStroke(stroke);
			
			int startX = x0+(int)(stroke.getLineWidth()/2);
			int width =  (x1-startX)-(int)(stroke.getLineWidth()/2.001); // - Math.round(stroke.getLineWidth());
			int startY = y0+(int)(stroke.getLineWidth()/2);
			int height =  (y1-startY)-(int)(stroke.getLineWidth()/2.001); // - (int)stroke.getLineWidth();//Math.ceil(
			
			if(shape == BoarderShape.ROUNDED_RECTANGLE){
				int archWidth = (int) Math.round((x1-x0)*ROUNDING_FACTOR);
				g2d.drawRoundRect(startX, startY,
						width, 
						height, 
						archWidth, archWidth);
			} else {
				g2d.drawRect(startX, startY, 
						width, 
						height);
			}
			
		}

	}

}
