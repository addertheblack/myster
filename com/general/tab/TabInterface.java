/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.general.tab;

import java.awt.*;

public interface TabInterface {
	
	public boolean isSelected();
	public Dimension getSize();
	public void paint(Graphics g);
}