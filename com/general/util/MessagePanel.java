package com.general.util;

import java.awt.Panel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.FontMetrics;

import java.util.Vector;

/**
*	An awt base Label like component that does text wrapping.
*/

public class MessagePanel extends Panel {
	int height;
	int ascent;
	
	FontMetrics metrics;
	
	String message;
	
	Vector messageVector = new Vector(20);
	
	public MessagePanel(String message) {
		this.message=message;
	}
	
	public java.awt.Dimension getPreferredSize() {
		return getSize();
	}

	private void doMessageSetup() {
		metrics=getFontMetrics(getFont());
		
		height=metrics.getHeight();
		ascent=metrics.getAscent();

		
		MrWrap wrapper=new MrWrap(message, 380, metrics);
		for (int i=0; i<wrapper.numberOfElements(); i++) {
			messageVector.addElement(wrapper.nextElement());
		}
	}
	
	public void paint(Graphics g) {
		if (metrics==null) doMessageSetup();
		g.setColor(Color.black);
		for (int i=0; i<messageVector.size(); i++) {
			g.drawString(messageVector.elementAt(i).toString(), 10, 5+height*(i)+ascent);
		}
	}
}
