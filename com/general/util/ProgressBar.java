package com.general.util;

import java.awt.*;
import java.awt.event.*;

public class ProgressBar extends Panel {
	public final static int DEFAULT_Y_SIZE = 10;
	public final static int DEFAULT_X_SIZE = 440;

	volatile long min; //valatile for threading
	volatile long max;
	volatile long value;
	
	volatile boolean hasBorder = true;
	
	Dimension doubleBufferSize;
	
	Image im;
	
	volatile Timer updaterTimer;

	public ProgressBar() {
		this(0,100);
		
		init();
	}
	
	public ProgressBar(long min, long max) {
		setMin(min);
		setMax(max);
		
		init();
	}
	
	private void init() {
		doubleBufferSize = getSize(); //! important
	
		addComponentListener(new ComponentAdapter() {
			
			public void componentResized(ComponentEvent e) {
				resetDoubleBuffer();
			}
		});
	}
	
	private synchronized void resetDoubleBuffer() {
		Dimension currentSize = getSize();
		doubleBufferSize = currentSize;
		
		im = createImage(currentSize.width, currentSize.height);
	}
	
	public void update(Graphics g) {
		
		if (im == null) {
			resetDoubleBuffer();
		}
		
       	paint(im.getGraphics());
       	
       	g.drawImage(im, 0, 0, this);
	}
	
	public final synchronized boolean isValueOutOfBounds() { //inline
		return (value < min || value > max);
	}
	
	private synchronized void timerCode(Runnable r) {
		syncRepaint();
	
		if ((! isShowing()) || (! isValueOutOfBounds())){
			System.out.println("Stopping the Progress Window auto update timer.");
			updaterTimer = null;
		} else {
			updaterTimer = new Timer(r, 50);
		}
	}

	public void paint(Graphics g) {
		if (updaterTimer == null && isShowing() && isValueOutOfBounds()) {
			updaterTimer = new Timer(new Runnable() {
				public void run() {
					timerCode(this);
				}
			}, 50);
		}
		
		Dimension size = doubleBufferSize;
		if (max <= min) {
			g.setColor(getBackground());
			g.fillRect(0,0,size.width,size.height);
		} else if (isValueOutOfBounds()) {
			double percent = Math.sin((((double)System.currentTimeMillis()/(double)70))/(2*Math.PI));
			percent = (percent + 1) / 2;
			int gray = (int)((double)(255) * percent);
			g.setColor(new Color(gray, gray, gray));
			g.fillRect(0,0,size.width,size.height);
		} else {
			g.setColor(getBackground());
			g.fillRect(0,0,size.width,size.height);
			
			g.setColor(getForeground());
			g.fillRect(0,0,getXSize(size.width),size.height);
		}
		
		if (getBorder()) {
			g.setColor(Color.black);
			g.drawRect(0,0,size.width-1,size.height-1);
		}
	}
	
	private synchronized void syncRepaint() {
		repaint();
	}
	
	private int getXSize(int maxWidth) {
		double percent = (double)(value - min) / (double)(max - min);

		return (int)(percent * maxWidth);
	}
	
	public synchronized Dimension getPreferredSize() {
		return new Dimension(DEFAULT_X_SIZE, DEFAULT_Y_SIZE);
	}
	
	public synchronized Dimension getMinimumSize() {
		return new Dimension(100, DEFAULT_Y_SIZE);
	}
	
	public synchronized void setBorder(boolean hasBorder) {
		this.hasBorder = hasBorder;
		repaint();
	}
	
	public boolean getBorder() {
		return hasBorder;
	}
	
	public synchronized void setMin(long min) {
		this.min = min;
	}
	
	public synchronized void setMax(long max) {
		this.max = max;
	}
	
	public long getMax() {
		return max;
	}
	
	public long getMin() {
		return min;
	}
	
	public long getValue() {
		return value;
	}
	
	int lastValue = 0;	//To make sure not repaint is done if it's not needed.
	public synchronized void setValue(long value) {
		if (this.value == value) return;
		this.value = value;
		
		if (isValueOutOfBounds()) {
			int temp_xsize = getXSize(doubleBufferSize.width);
			if (lastValue == temp_xsize) return;
			
			lastValue = temp_xsize;
		}
		
		repaint();
	}
}