package com.general.util;

import java.awt.*;

public class ProgressBar extends Panel {
	long min;
	long max;
	long value;
	
	boolean hasBorder;
	
	Dimension doubleBufferSize;
	
	Image im;

	public ProgressBar() {
		this(0,100);
	}
	
	public ProgressBar(long min, long max) {
		setMinSize(min);
		setMaxSize(max);
	}
	
	public void update(Graphics g) {
		Dimension currentSize = getSize();
	
		if (doubleBufferSize==null) doubleBufferSize = currentSize;
		
		if (! currentSize.equals(doubleBufferSize) || (im == null)) {
			im = createImage(currentSize.width, currentSize.height);
		}
		
       	paint(im.getGraphics());
       	
       	g.drawImage(im, 0 , 0, this);
	}

	public void paint(Graphics g) {
		Dimension size = doubleBufferSize;
					
		if (max <= min) {
			g.setColor(getBackground());
			g.fillRect(0,0,size.width,size.height);
		} else if (value < min || value > max) {
			g.setColor(Color.red);
			g.fillRect(0,0,size.width,size.height);
		} else {
			g.setColor(getBackground());
			g.fillRect(0,0,size.width,size.height);
			
			g.setColor(getForeground());
			g.fillRect(0,0,getXSize(size.width),size.height);
			
			if (getBorder()) {
				g.setColor(Color.black);
				g.drawRect(0,0,size.width,size.height);
			}
		}
	}
	
	private long getXSize(int maxWidth) {
		double percent = (value - min) / (max - min);
		
		return (int)(value * maxWidth);
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(max-min, 10);
	}
	
	public void setBorder(boolean hasBorder) {
		this.hasBorder = hasBorder;
		repaint();
	}
	
	public boolean getBorder() {
		return hasBorder;
	}
	
	public long setMinSize(int min) {
		this.min = min;
	}
	
	public long setMaxSize(int max) {
		this.max = max;
	}
	
	public void setValue(long value) {
		this.value = value;
		//repaint?
	}
}