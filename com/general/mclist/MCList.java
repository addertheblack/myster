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
import java.util.Vector;

//import java.awt.image.BufferedImage;//testing

public class MCList extends Panel {
	private Image im;
	
	//My Size..?
	private Dimension bounds;
	
	//GUI Flags
	private long clickedtime=0;
	private boolean doubleclickflag=true;
	private boolean select=true; 		//True: Add items False=RemoveItems
	
	
	//For image double buffer:
	private int previousimagex=-1;
	private int previousimagey=-1;
	
	//Themes support of sorts
	private MCRowThemeInterface rowtheme;
	
	//List Itself:
	MCListVector list;
	
	//I add Myself to the scroll pane so it can scrolll me around! (yeah, I know)
	ScrollPane pane;
	
	//Header
	MCListHeader header;
	
	//Event Handler
	MCListEventHandler eventhandler;
	
	//Single select flag:
	boolean singleselectboolean=false;
	
	
	public static final int PADDING=1;



	
	
	public MCList(int numberofcolumns, boolean singleselect, Component c) {
		this(numberofcolumns, singleselect,new DefaultMCRowTheme(c, PADDING));
	}	
	
	public MCList(int numberofcolumns, boolean singleselect, MCRowThemeInterface theme) {
		
		this.rowtheme=theme;
		eventhandler=new MCListEventHandler(this);

		singleselectboolean=singleselect;
		list=new MCListVector();
		
		pane=new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
		setSize(1024,1024);
		setLayout(null);
		pane.add(this);
		header=new MCListHeader(this, numberofcolumns);
		
		add(header);
		addMouseListener(eventhandler);
		addMouseMotionListener(eventhandler);
		
		Adjustable horizontal = pane.getHAdjustable();
		horizontal.setUnitIncrement(10);
		Adjustable vertical = pane.getVAdjustable();
		vertical.setUnitIncrement(10);
		vertical.addAdjustmentListener(new AdjustmentListener() {
			public void adjustmentValueChanged(AdjustmentEvent e) {
				header.setLocation(0, e.getValue());
			}
		});
		
		setBackground(theme.getBackground());
		
	}
	
	public void setNumberOfColumns(int c) {
		header.setNumberOfColumns(c);
		listChangedSize(); //blah.
		
	}
	
	public void setColumnName(int columnnumber, String name) {
		header.setColumnName(columnnumber, name);
		listChangedSize();
	}
	
	public void setColumnWidth(int index, int size) {
		header.setColumnWidth(index, size);
		listChangedSize();
	}
	
	public synchronized void sortBy(int column) {
		list.setSortBy(column);
		header.sortBy(column);
		repaint();
	}
	
	public ScrollPane getPane() {return pane;}
	
	public void addItem(MCListItemInterface m) {
		list.addElement(m);
		listChangedSize();
	}
	
	public void addItem(MCListItemInterface[] m) { //oh for templates...
		list.addElement(m);
		listChangedSize();
	}
	
	//Important for canvas
	public synchronized Dimension getPreferredSize() {
	 	RowStats rowstats=header.getRowStats();
		
		int ysize=list.size()*rowtheme.getHeight()-1+header.getHeight();
		
		int xsize=rowstats.getTotalLength();
		
		if (pane.getViewportSize().width>xsize) xsize=pane.getViewportSize().width;
		
		if (pane.getViewportSize().height>ysize) ysize=pane.getViewportSize().height;
		
		return new Dimension(xsize, ysize);
	}
	
	public void update(Graphics g) {	//done.
		paint(g);
	}
    
    
    private void updatey(Graphics g) {	//done.
		if (pane.getViewportSize().height-header.getHeight()<0) return;
       	if (previousimagex!=pane.getViewportSize().width||	//makes sure the image buffer is up to date!
       			previousimagey!=pane.getViewportSize().height){
       		im=createImage(pane.getViewportSize().width, pane.getViewportSize().height-header.getHeight());//new BufferedImage(pane.getViewportSize().width,pane.getViewportSize().height-header.getHeight(),BufferedImage.TYPE_INT_BGR);//
       		previousimagex=pane.getViewportSize().width;
       		previousimagey=pane.getViewportSize().height;
       	}
       	paint(im.getGraphics(), pane.getScrollPosition().x, pane.getScrollPosition().x+pane.getViewportSize().width ,pane.getScrollPosition().y, pane.getScrollPosition().y+pane.getViewportSize().height);
       	g.setClip(pane.getScrollPosition().x, pane.getScrollPosition().y ,pane.getViewportSize().width,pane.getViewportSize().height);
       	g.drawImage(im, pane.getScrollPosition().x , pane.getScrollPosition().y+header.getHeight(), this);
    }
    
   // private void updatey(Graphics g) {	//not double buffered.
		//if (pane.getViewportSize().height-header.getHeight()<0) return;
       //	if (previousimagex!=pane.getViewportSize().width||	//makes sure the image buffer is up to date!
       	//		previousimagey!=pane.getViewportSize().height){
       //		im=createImage(pane.getViewportSize().width, pane.getViewportSize().height-header.getHeight());
       //		previousimagex=pane.getViewportSize().width;
       //		previousimagey=pane.getViewportSize().height;
     //  	}
    //   	paint(g, pane.getScrollPosition().x, pane.getScrollPosition().x+pane.getViewportSize().width ,pane.getScrollPosition().y, pane.getScrollPosition().y+pane.getViewportSize().height);
       	//g.setClip(pane.getScrollPosition().x, pane.getScrollPosition().y ,pane.getViewportSize().width,pane.getViewportSize().height);
       	//g.drawImage(im, pane.getScrollPosition().x , pane.getScrollPosition().y+header.getHeight(), this);
    //}

	
	public void paint(Graphics g) {		//done.
		header.setLocation(0, pane.getScrollPosition().y);
		header.setSize(header.getPreferredSize());
		updatey(g);
		//paint(g, pane.getScrollPosition().y, pane.getViewportSize().height+pane.getScrollPosition().y);	//imporant.
	}	
	
	public void paint(Graphics g, int x1, int x2, int y1, int y2) { //uppper and lower bounds to draw
		//if (true==true) return ;
		g.setColor(getBackground());
		g.fillRect(0,0,im.getWidth(this),im.getHeight(this));
		//g.fillRect(0,0,y2-y1,x2-x1);
		if (list.size()==0) return;
		y1+=header.getHeight(); //why bother over drawing like this?
		int c1=getClicked(1, y1);
		int c2=getClicked(1, y2);
		if (c2==-1) c2=list.size()-1; 
		if (c1==-1) c2=-1; //If c1=-1 the means the scroll pane is outside any visible area so draw nothing.
		
		int offsetcounter=getYFromClicked(c1)-y1;		//rounding routine (get the offset properly. Gtes initial offset.
		
		RowStats rowstats=header.getRowStats();
		
		for (int i=c1; i<=c2; i++) {
			rowtheme.paint(g, list.getElement(i), rowstats, offsetcounter, x1, i );
			offsetcounter+=rowtheme.getHeight();
		}
		g.dispose();
	}
	
	public boolean isSelected(int i) {
		if (!(i>=0&&i<list.size())) return false;
		return list.getElement(i).isSelected();
	}
	
	public void select(int i) {
		if (!(i>=0&&i<list.size())) return;
		list.getElement(i).setSelected(true);
	}
	
	public void unselect(int i) {
		if (!(i>=0&&i<list.size())) return;
		list.getElement(i).setSelected(false);
	}
	
	public synchronized void clearAllSelected() {
		synchronized (list) {
			for (int i=0; i<list.size(); i++) {
				list.getElement(i).setSelected(false);
			}
		}
	}
	
	public void toggle(int i) {
		synchronized (list) {
			if (!(i>=0&&i<list.size())) return;
			if (list.getElement(i).isSelected()) list.getElement(i).setSelected(false);
			else list.getElement(i).setSelected(true);
		}
	}
	
	public synchronized int getClicked(int x, int y) {
		y-=0; //hahahahhaha
		y-=header.getHeight();
		int temp=y/rowtheme.getHeight();
		if (temp<list.size()) return temp;
		return -1;
    }
    
    public int[] getSelectedIndexes() {
    	return list.getSelectedIndexes();
    }
    
    /**
    *	Returns the selected index
    *	Returns -1 is there is none selected or if more than one item is selected.
    */
    
    public int getSelectedIndex() {
    	return list.getSelectedIndex();
    }
    
    /**
    *	If set the list will only allow one item o be selected at one time.
    */
	public void setSingleSelect(boolean b) {
		singleselectboolean=b;
	}
	
	public boolean isSingleSelect() {
		return singleselectboolean;
	}
	
	public synchronized void addMCListEventListener(MCListEventListener e) {
		eventhandler.addMCListEventListener(e);
	}
	
	public void clearAll() {
		list.removeAllElements();
		listChangedSize();
		pane.repaint();
	}
	
	public void removeItem(int i) {
		list.removeElement(i);
		listChangedSize();
	}
	
	public void removeItem(MCListItemInterface o) {
		list.removeElement(o);
		listChangedSize();
	}
	
	public void removeItem(int[] indexes) {
		list.removeIndexes(indexes);
		listChangedSize();
	}
	
	/**
		Return that indexe's Item's Object
	*/
	public Object getItem(int i) {
		return getMCListItem(i).getObject();
	}
	
	/**
		Return the MCListItemInterface for this index... A bit confusing, yes...
	*/
	public MCListItemInterface getMCListItem(int i) {
		return list.getElement(i);
	}	
	
	public void reverseSortOrder() {
		list.reverseSortOrder();
		repaint();
	}
	
	private int getYFromClicked(int c) {
		int spacingindex=0;
		spacingindex=c*rowtheme.getHeight()+header.getHeight();
		return spacingindex;
		
	}
	
	private void listChangedSize() { //if it is synchronized, the it wil break under MacOS X.
		try {		
			pane.invalidate();	//invalidate current layout.
			pane.validate();	//update scroll pane to possible changes in size.
			//pane.doLayout();
		} catch (Exception ex){}
		repaint();
	}
	
	public int length() {
		return list.size();
	}
	
	public Font getFont() {
		Font font=super.getFont();
		if (super.getFont()==null) {
			return new Font("Courier", 0, 12);
		}
		return font;
	}
}