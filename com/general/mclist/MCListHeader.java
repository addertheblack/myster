/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

import java.awt.*;
import java.awt.event.*;

public class MCListHeader extends Panel {
	private MCList callback;
	
	private static final int HEIGHT=17;
	
	//Book Keeping
	private int numberofcolumns;
	private String[] columnarray;
	private int[] columnWidthArray;	// A value of -1 means value should default to width of header string.
	private int sortby=0;
	
	private static final int CLICK_LATITUDE=5;
	private static final int MIN_COLUMN_WIDTH=15;

	public MCListHeader(MCList c, int numberofcolumns) {
		callback=c;
		this.numberofcolumns=numberofcolumns;
		columnarray=new String[numberofcolumns];
		for(int i=0; i<numberofcolumns; i++) columnarray[i] = "unnamed";
		
		initColumnWidthArray();
		repaint();
		
		MCListHeaderEventHandler temp=new MCListHeaderEventHandler(this);
		addMouseListener(temp);
		addMouseMotionListener(temp);
	}
	
	public void initColumnWidthArray() {
		int[] oldArray={};
		if (columnWidthArray!=null) oldArray=columnWidthArray; //line to take care of case where columnWidthArray is not inited. See constructor.
		int i;
		columnWidthArray=new int[numberofcolumns];
		for (i=0; i<oldArray.length&&i<columnWidthArray.length; i++) {
			columnWidthArray[i]=oldArray[i];
		}
		
		//If the column width array is larger than the previous array then init those values
		for ( ; i<columnWidthArray.length; i++) {
			columnWidthArray[i]=-1;
		}
	}
	
	Image im;
	int lastwidth=-1;
	public void update2(Graphics g) {
		if (getSize().width!=lastwidth) {
			im=null;
			lastwidth=getSize().width;
		}
       	if (im==null){
       		im=createImage(getSize().width, getSize().height);
       	}
       	paint2(im.getGraphics());
       	g.drawImage(im, 0, 0, getSize().width, getSize().height, this);
    }
	
	public void update(Graphics g) {paint(g);}
	public void paint(Graphics g) {update2(g);}
	
	public void paint2(Graphics g) {
	
		RowStats rowstats=getRowStats();
		int padding=rowstats.getPadding();
		
		FontMetrics tempfont = callback.getFontMetrics(callback.getFont());	//uses default font
		int height = tempfont.getHeight();
		int ascent = tempfont.getAscent();
		int descent= tempfont.getDescent();	
		
		g.setColor(getBackground());
		g.fillRect(0,0,getSize().width, getHeight());
		
		g.setColor(new Color(200,200,255));
		g.fillRect(0,0,getRowStats().getTotalLength(), getHeight());
		
		
		int hozoffset=0;
		for (int i=0; i<columnarray.length; i++) {
			if (i==sortby) {
				g.setColor(new Color(200,200,255));
			} else {
				g.setColor(new Color(215,215,215));
			}
			g.fillRect(padding+hozoffset, padding, rowstats.getWidthOfColumn(i), HEIGHT);
			
			g.setColor(new Color(235,235,235));
			g.drawLine(padding+hozoffset, padding, padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding);
			g.drawLine(padding+hozoffset, padding, padding+hozoffset, padding+HEIGHT-1);
			g.setColor(new Color(175,175,175));
			g.drawLine(padding+hozoffset, padding+HEIGHT-1, padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding+HEIGHT-1);
			g.drawLine(padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding, padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding+HEIGHT-1);
			
			
			g.setColor(new Color(255,255,255)); g.fillRect( padding+hozoffset, padding,1,1);
			g.setColor(new Color(215,215,215)); g.fillRect(padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding,1,1);
			g.setColor(new Color(215,215,215)); g.fillRect( padding+hozoffset, padding+HEIGHT-1,1,1);
			g.setColor(new Color(140,140,140)); g.fillRect( padding+hozoffset+rowstats.getWidthOfColumn(i)-1, padding+HEIGHT-1,1,1);
			
			hozoffset+=padding+rowstats.getWidthOfColumn(i);
		}
		
		g.setColor(Color.black);
		hozoffset=0;
		int yplace=padding+ascent;
		for (int i=0; i<numberofcolumns;i++) {
			g.drawString(makeFit(columnarray[i], rowstats.getWidthOfColumn(i)), hozoffset+padding+2, yplace);
			hozoffset+=padding+rowstats.getWidthOfColumn(i);
		}
	}
	
	public void setNumberOfColumns(int numberofcolumns) {
		this.numberofcolumns=numberofcolumns;
		columnarray=new String[numberofcolumns];
		for(int i=0; i<numberofcolumns; i++) columnarray[i] = "unnamed";
		initColumnWidthArray();
		invalidate();
		repaint();
	}
	
	public int getNumberOfColumns() {
		return numberofcolumns;
	}
	
	public void setColumnWidth(int index, int size) {
		if (index>-1&&index<getNumberOfColumns()&&size>-1) {
			if (size<MIN_COLUMN_WIDTH) size=MIN_COLUMN_WIDTH;
			columnWidthArray[index]=size;
			repaint();
		}
	}
	
	public String[] getColumnArray() {
		String[] temp=new String[columnarray.length];
		System.arraycopy(columnarray, 0, temp, 0, columnarray.length);
		return temp;
	}
	
	public RowStats getRowStats() {
		return new RowStats(getColumnWidthArray(), callback.PADDING);
	}
	
	public Dimension getMinimumSize() {
		return new Dimension(100,100);
		
		//return getPreferredSize();
	}
	
	public Dimension getPreferredSize() {
		int plength=getRowStats().getTotalLength();
		int olength=callback.getSize().width+250; 	//not, this is done to avoid a flickering effect that occures when
												// a panel is resized (and repainted);
												//The idea is to avoid resizing by making the panel JUST big enought 
												//to not flicker most of the time.
												//The flickering stll occures, however if a list is grown more than 250
												//pixels over it's current size. oh well..
												//This effect does not happen in MacOS X, because all windows are double buffered.
		
		return new Dimension((plength>olength?plength:olength),getHeight());
	}
	
	public void setColumnName(int columnnumber, String name) {
		if (columnnumber>=numberofcolumns) return;
		columnarray[columnnumber]=name;
		repaint();
	}
	public int getColumnWidth(int i) {
		return getColumnWidthArray()[i];
	}
	
	public int[] getColumnWidthArray() {
		int[] columnWidthArray=new int[numberofcolumns];
		
		for (int i=0; i<numberofcolumns; i++) {
			if (this.columnWidthArray[i]==-1) columnWidthArray[i]=getFontMetrics(getFont()).stringWidth(columnarray[i]+1)+callback.PADDING;
			else columnWidthArray[i]=this.columnWidthArray[i];
		}
		return columnWidthArray;
	
	}
	
	public int getHeight() {
		return 2*callback.PADDING+HEIGHT;
	}
	
	public String makeFit(String s, int size) {
	
		FontMetrics tempfont = callback.getFontMetrics(callback.getFont());	//uses default font
		
		
		int i=0;
		if (tempfont.stringWidth(s)>size-8) {
			for (i=s.length(); ((tempfont.stringWidth(s.substring(0,i)+"...")>size-8)&&i>0); i--)
				;
			return s.substring(0, i)+"...";
		}
		return s;
	}
	
	public int getColumnOfClick(int x, int y) {
		//Y is useless here BTW.
		RowStats rowstats=getRowStats();

		int xcounter=0;
		for (int i=0; i<numberofcolumns; i++) {
			if (x>=xcounter&&x<(rowstats.getTotalWidthOfColunm(i)+xcounter)) return i;
			xcounter+=rowstats.getTotalWidthOfColunm(i);
		}
		return numberofcolumns-1; //if click is bigger then last column, return last column
	}
	
	public void sortBy(int x) {
		sortby=x;
		repaint();
	}
	
	public MCList getMCLParent() {
		return callback;
	}
	
	public boolean isOnBorder(int x) {
		return (getResizeColumn(x)!=-1);
	}
	
	public int getResizeColumn(int x) {
		int counter=0;
		
		RowStats rowstats=getRowStats();
		for (int i=0; i<numberofcolumns; i++) {
			counter+=rowstats.getTotalWidthOfColunm(i);
			if (x<counter+CLICK_LATITUDE&&x>counter-CLICK_LATITUDE) {
				return i;
			}
		}
		return -1;
	}
}