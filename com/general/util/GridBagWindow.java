/* 
	GridBagWindow.java

	Title:			GridBagWindow
	Author:			Andrew Trumper (c) 2001
	Description:	A utility for making a quick GBwindow.
*/

package com.general.util;


import java.awt.*;
import java.awt.event.*;

public class GridBagWindow extends Frame {
	GridBagLayout gblayout;
	GridBagConstraints gbconstrains;

	public GridBagWindow(String s) {
		super(s);
		makeWindow();
	}
	
	private void makeWindow() {
		//Do interface setup:
		gblayout=new GridBagLayout();
		setLayout(gblayout);
		gbconstrains=new GridBagConstraints();
		gbconstrains.fill=GridBagConstraints.BOTH;
		gbconstrains.ipadx=1;
		gbconstrains.ipady=1;

	}
	
	
	public void addComponent(Component c, int row, int column, int width, int height, int weightx, int weighty) {
		gbconstrains.gridx=column;
		gbconstrains.gridy=row;
		
		gbconstrains.gridwidth=width;
		gbconstrains.gridheight=height;
		
		gbconstrains.weightx=weightx;
		gbconstrains.weighty=weighty;
		
		gblayout.setConstraints(c, gbconstrains);
		
		add(c);
	}
}