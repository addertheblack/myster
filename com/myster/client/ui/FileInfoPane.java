/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/
package com.myster.client.ui;

import java.awt.*;
import com.general.util.KeyValue;

public class FileInfoPane extends Panel {
	KeyValue keyvalue;

	private int VOFFSET=25;
	private int HOFFSET=3;
	
	private Dimension mysize=new Dimension(200,200);
	private Dimension mind=new Dimension(50,100);

	public FileInfoPane(){keyvalue=new KeyValue();}
	
	public void display(KeyValue k){
		keyvalue=k;
		repaint();
	}
	
	//public void update(Graphics g) {}
	
	public void paint(Graphics g) {
		FontMetrics metric=getFontMetrics(getFont());
		int vertical=metric.getHeight()+3;
		g.setColor(Color.black);
		for (int i=0; i<keyvalue.length(); i++) {
			g.drawString(""+(String)(keyvalue.keyAt(i))+" : "+(String)keyvalue.valueAt(i), HOFFSET, VOFFSET+i*vertical);
		}	
		//Util.getStringFromBytes(Long.valueOf((String)keyvalue.valueAt(i)).longValue())

	}

	public KeyValue getData() {
		return keyvalue;
	}
	
	public void clear() {
		keyvalue=new KeyValue();
		repaint();
	}
		public Dimension getMinimumSize() {
		return new Dimension(0,0);
	}
	
	public Dimension getPreferredSize() {
		return getSize();
	}
	/*
	public Dimension getMinimumSize() {
		Font f=getFont();
		FontMetrics m=null;
		if (f==null) {
			mysize=mind;
		} else {
			m=getFontMetrics(f);
		}
		if (m==null||keyvalue==null) {
			mysize=mind;
		} else {
			if (keyvalue.length()>0) mysize=new Dimension(m.stringWidth(""+(String)(keyvalue.keyAt(0))+" : "+(String)keyvalue.valueAt(0)), 200);
			else mysize=mind;
		}

		
		return mysize;
	}
	
	public Dimension getPreferredSize() {
		return getMinimumSize();
	}
	*/

}