/* 
	MCList.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

import java.awt.event.*;
import java.util.Vector;
import java.awt.ScrollPane;
import java.awt.Dimension;
import java.awt.Point;


public class MCListEventHandler implements MouseListener, MouseMotionListener{
    //To Access "Component" features...
    private MCList callback;

    //Double click time
    private static final int DCTIME=500; //Half a second double click time.
   
    
    //Book keeping
    private long clickedtime=0;
    private int firstselect=-1;
    private int lastselect=-1;
    private boolean doubleclickflag=false;
    private boolean select=false; //select flag
    
    //Event Queue!
    private Vector vector;
    
    public MCListEventHandler(MCList c) {
    	callback=c;
    	vector=new Vector(10);
    }
    

    public void addMCListEventListener(MCListEventListener e) {
    	vector.addElement(e);
    }

    
    //MouseListener event handlers.
    public void mousePressed(MouseEvent e){
    	callback.requestFocus(); //Fucking bug n Java me thinks.
		long currenttime=System.currentTimeMillis();
		int workingselection=-1;
		workingselection=callback.getClicked(e.getX(), e.getY());
		
		if (e.isShiftDown()&&firstselect!=-1&&!callback.isSingleSelect()){
			int sign=getSign(firstselect-workingselection);
			for(int i=firstselect; i!=(workingselection-sign); i=i-sign) {
				callback.select(i);
			}
			if (firstselect!=(workingselection-sign)) selectItemEventDispatch();
			callback.repaint();	
		}
		else if (e.isMetaDown()&&firstselect!=-1&&!callback.isSingleSelect()){
			if (workingselection!=-1) callback.toggle(workingselection);
			if (callback.isSelected(workingselection)) selectItemEventDispatch();
			else unselectItemEventDispatch();
			callback.repaint();	
		}
		else {
			//doubleclick Check
			if (currenttime-clickedtime<DCTIME&&(!doubleclickflag)&&callback.isSelected(workingselection)) doubleclickflag=true;
			else {
				clickedtime=System.currentTimeMillis();
				doubleclickflag=false;	
			}
			
			if (!callback.isSelected(workingselection)||workingselection==-1) {
				firstselect=workingselection;
				callback.clearAllSelected();
				unselectItemEventDispatch();
				callback.select(workingselection);
				selectItemEventDispatch();
				callback.repaint();	
			}
		}
		
		if (e.isMetaDown()&&!callback.isSingleSelect()) {
    		if (callback.isSelected(workingselection)&&workingselection!=-1) select=true;
    		else select=false;	
    	}
	
    }
    
    public void mouseClicked(MouseEvent e){}
    
    public void mouseReleased(MouseEvent e){
    	long currenttime=System.currentTimeMillis();
    	int workingselection=-1;
    	workingselection=callback.getClicked(e.getX(), e.getY());
    	if (callback.isSelected(workingselection)&&currenttime-clickedtime<DCTIME&&doubleclickflag) {
    		doubleclickflag=false;
    		doubleClickEventDispatch();
		}
    }
    
    public void mouseEntered(MouseEvent e){}
    public void mouseExited(MouseEvent e){}
    
    //MouseMontionListener event Handlers
    public void mouseMoved( MouseEvent e ) {}
    public void mouseDragged(MouseEvent e) {
    	int workingselection=callback.getClicked(e.getX(), e.getY());
    	
		if (e.isShiftDown()&&firstselect!=-1&&workingselection!=lastselect&&!callback.isSingleSelect()){
			if (workingselection!=-1) {
				int sign=getSign(firstselect-workingselection);
				for(int i=firstselect; i!=(workingselection-sign); i=i-sign) {
					callback.select(i);
				}
				
				if (firstselect!=(workingselection-sign)) selectItemEventDispatch();
				
				callback.repaint();
			}
		}
    	else if (e.isMetaDown()&&firstselect!=-1&&!callback.isSingleSelect()){
			if (workingselection!=-1&&workingselection!=lastselect) {
				if (select&&!callback.isSelected(workingselection)) { 
					callback.select(workingselection);
					selectItemEventDispatch();
					callback.repaint();
				}
				else if (!select&&callback.isSelected(workingselection)) {
					callback.unselect(workingselection);
					unselectItemEventDispatch();
					callback.repaint();
				}
			}
		}
    	else {
	    	if (!(e.isShiftDown())&&(!callback.isSelected(workingselection)||workingselection==-1)) {
	    		firstselect=workingselection;
	    		callback.clearAllSelected();
	    		unselectItemEventDispatch();
	    		callback.select(workingselection);
	    		selectItemEventDispatch();
	    		callback.repaint();
	    	}
    	}
    	lastselect=workingselection;
    	
    	// Do auto scroll stuff:
    	// Auto scroll will move the scroll pane around whenever you drag
    	// your mouse off the imeadiate MCList area 
    	//Setup:
    	    ScrollPane pane=callback.getPane();
    		Point currentLoc=pane.getScrollPosition();
    		Dimension currentSize=pane.getViewportSize();
    		Dimension mcListSize=callback.getPreferredSize();
    	//end setup.
    	
    	// x component:
    		int xloc=e.getX();

    		//check if scrolling is possible.
    		if (mcListSize.width>currentSize.width) { //Only do this code is scrolling is possible.
    		
	    		//check if click is off to the negative:
	    		if (xloc<currentLoc.x) {
	    			//scrolling is possible
	    			
	    			int difference=(currentLoc.x-xloc)/4+1; //Amount to scroll is proportional to away from edge.
	    			if (currentLoc.x>=difference) {
	    				pane.setScrollPosition(currentLoc.x-difference, currentLoc.y);
	    			} else {
	    				pane.setScrollPosition(0, currentLoc.y);
	    			}
	    		} else if (xloc>currentLoc.x+currentSize.width) {
	    			//scrolling is possible
	    			
	    			int difference=(xloc-(currentLoc.x+currentSize.width))/4+1;
	    			if ((currentLoc.x+currentSize.width)<=(mcListSize.width-difference)) {
	    				pane.setScrollPosition(currentLoc.x+difference, currentLoc.y);
	    			} else {
	    				pane.setScrollPosition(mcListSize.width-currentSize.width, currentLoc.y);
	    			}
	    		}
    		}
    		
    		
    		// NOW Y COMPONENT (Same sort of thing)
    		int yloc=e.getY();
    		
    		//check if scrolling is possible.
    		if (mcListSize.height>currentSize.height) { //ignore if scrolling is simply not possible!
	    		//check if click is off to the negative:
	    		if (yloc<currentLoc.y) {
	    			//scrolling is possible
	    			
	    			int difference=(currentLoc.y-yloc)/4+1;
	    			if (currentLoc.y>=difference) {
	    				pane.setScrollPosition(currentLoc.x, currentLoc.y-difference);
	    			} else {
	    				pane.setScrollPosition(currentLoc.x, 0);
	    			}
	    		} else if (yloc>currentLoc.y+currentSize.height) {
	    			//scrolling is possible
	    			
	    			int difference=(yloc-(currentLoc.y+currentSize.height))/4+1;
	    			if ((currentLoc.y+currentSize.height)<=(mcListSize.height-difference)) {
	    				pane.setScrollPosition(currentLoc.x, currentLoc.y+difference);
	    			} else {
	    				pane.setScrollPosition(currentLoc.x, mcListSize.height-currentSize.height);
	    			}
	    		}
    		}
    		
    		
    	// End auto scroll
    }
    
    private int getSign(int x) {
    	if (x!=0) return x/Math.abs(x);
    	return 1;
    }
    
    
    //Event Dispatch routines
    private void doubleClickEventDispatch() {
    	MCListEvent e=buildEvent();
    	
    	for (int i=0; i<vector.size(); i++) ((MCListEventListener)(vector.elementAt(i))).doubleClick(e);
    }
    
    private void selectItemEventDispatch() {
    	MCListEvent e=buildEvent();
    	
    	if (callback.getSelectedIndex()==-1) return;
    	for (int i=0; i<vector.size(); i++) ((MCListEventListener)(vector.elementAt(i))).selectItem(e);
    }
    
    private void unselectItemEventDispatch() {
    	MCListEvent e=buildEvent();
    	
    	for (int i=0; i<vector.size(); i++) ((MCListEventListener)(vector.elementAt(i))).unselectItem(e);
    }	
    
    private MCListEvent buildEvent() {
    	return new MCListEvent(callback);
    } 
}