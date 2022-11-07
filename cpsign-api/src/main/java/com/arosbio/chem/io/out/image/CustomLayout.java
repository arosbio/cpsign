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

public class CustomLayout implements Layout{
	
	private Boarder boarder;
	private Padding padding;
	private Margin margin;
	
	public CustomLayout(Padding padding, Boarder boarder, Margin margin){
		this.padding = padding;
		this.boarder = boarder;
		this.margin = margin;
		if (padding == null)
			this.padding = new Padding(0);
		if (margin==null)
			this.margin = new Margin(0);
	}
	
	public int getAdditionalWidth(){
		return padding.left+padding.right + margin.left+margin.right+(boarder!=null? boarder.getWidth()*2:0);
	}
	
	public int getAdditionalHeight(){
		return padding.top+padding.bottom + margin.top+margin.bottom+(boarder!=null? boarder.getWidth()*2:0);
	}

	public Boarder getBoarder(){
		return boarder;
	}
	public void setBoarder(Boarder boarder){
		this.boarder = boarder;
	}
	
	public Padding getPadding(){
		return padding;
	}
	
	public void setPadding(Padding padding){
		if (padding==null)
			this.padding = new Padding(0);
		else
			this.padding = padding;
	}
	
	public Margin getMargin(){
		return margin;
	}
	
	public void setMargin(Margin margin){
		if(margin==null)
			this.margin = new Margin(0);
		else
			this.margin = margin;
	}
	
	public BufferedImage addLayout(BufferedImage img){
		
		int addedWidth = padding.left+padding.right+
				(boarder!=null?boarder.getWidth()*2:0)+
				 margin.left+margin.right;
		int addedHeight = padding.top+padding.bottom+
				(boarder!=null?boarder.getWidth()*2:0)+
				margin.top+margin.bottom;
		if (addedHeight==0 && addedWidth==0)
			return img;
		
		BufferedImage withOrnament = new BufferedImage(img.getWidth()+addedWidth, img.getHeight()+addedHeight, img.getType());
		Graphics2D g = withOrnament.createGraphics();
		
		if (boarder!=null){
			boarder.paint(g, margin.left, margin.top, withOrnament.getWidth()-margin.right-1, withOrnament.getHeight()-margin.bottom-1);
		}
		
		int imgStartX = padding.left + (boarder!=null?boarder.getWidth():0)+margin.left;
		int imgStartY = padding.top + (boarder!=null?boarder.getWidth():0)+margin.top;
		g.drawImage(img, imgStartX, imgStartY, img.getWidth(), img.getHeight(), null);
		g.dispose();
		return withOrnament;
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
		
		private double roundingFactor = 0.05;
		private Color boarderColor = Color.BLACK; 
		private BasicStroke stroke = new BasicStroke();
		private BoarderShape shape = BoarderShape.RECTANGLE;
		
		
		public enum BoarderShape {
			RECTANGLE, ROUNDED_RECTANGLE
		}
		
		public Boarder() {
			// defaults
		}
		
		public Boarder(BasicStroke stroke){
			this.stroke = stroke;
		}
		
		public Boarder(BoarderShape shape){
			this.shape = shape;
		}
		
		public Boarder(BoarderShape shape, BasicStroke stroke){
			this.stroke = stroke;
			this.shape = shape;
		}
		
		public Boarder(BoarderShape shape, BasicStroke stroke, Color color){
			this.stroke = stroke;
			this.shape = shape;
			this.boarderColor = color;
		}
			
		public void setStroke(BasicStroke stroke){
			this.stroke = stroke;
		}
		
		public void setShape(BoarderShape shape){
			this.shape = shape;
		}
		
		public void setColor(Color color){
			this.boarderColor = color;
		}
		
		/**
		 * Returns the boarder width used (which will be the Math.round value of whatever was set in the {@link BasicStroke})
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
				int archWidth = (int) Math.round((x1-x0)*roundingFactor);
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
