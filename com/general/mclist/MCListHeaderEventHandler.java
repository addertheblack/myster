/* 
	main.java

	Title:			Multi Column List Package
	Author:			Andrew Trumper
	Description:	A MultiColumn List Package

Copyright Andrew Trumper 2001
*/

package com.general.mclist;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.Cursor;

public class MCListHeaderEventHandler extends MouseAdapter implements MouseMotionListener {
	 MCListHeader header;
	 
	 public MCListHeaderEventHandler(MCListHeader h) {header=h;}
	 
	 int resizeloc=-1;
	 boolean resizeflag=false;
	 int resizeColumn=-1;
	 int originalSize=-1;
	 
	 public void mousePressed(MouseEvent e){
	 	if (resizeEnabled() && header.isOnBorder(e.getX())) {
	 		resizeloc=e.getX();
	 		resizeflag=true;
	 		resizeColumn=header.getResizeColumn(e.getX());
	 		originalSize=header.getColumnWidth(resizeColumn);
	 	} else {
		 	int columnClicked=header.getColumnOfClick(e.getX(), e.getY());
		 	
		 	if (header.getSortBy() != columnClicked) {
		 		header.getMCLParent().sortBy(columnClicked);
		 	} else if (columnClicked == -1) {
		 		// do nothing.. There's not point in reversing the sort order on a non column selected
		 	} else {
		 		header.getMCLParent().reverseSortOrder();
		 	}
		 	
		 	header.repaint();
	 	}
	 }
	 
	 public void mouseDragged(MouseEvent e) {
	 	if (resizeEnabled() && resizeflag) {
	 		if (resizeloc==-1||resizeColumn==-1) {
	 			throw new RuntimeException("Garbage in Mouse dragged");
	 		}
	 		header.setColumnWidth(resizeColumn, originalSize+(e.getX()-resizeloc));
	 		header.getMCLParent().repaint();
	 	}
	 }

 	public void mouseMoved(MouseEvent e) {
 		if (resizeEnabled() && header.isOnBorder(e.getX())) {
 			header.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR ));
 		} else {
 			header.setCursor(Cursor.getDefaultCursor());
 			
 			//Mouse over effect
 			header.setMouseOver(header.getColumnOfClick(e.getX(), e.getY()));
 			//end mouse over effect
 		}
 	}
 	
 	public void mouseExited(MouseEvent e) {
 		//Mouse over effect
 		header.setMouseOver(-1);
 	}
	 
	 public void mouseReleased(MouseEvent e) {
	 	if (resizeEnabled() && resizeflag) {
	 		if (resizeloc==-1||resizeColumn==-1) {
	 			throw new RuntimeException("Garbage in Mouse dragged");
	 		}
	 		
	 		header.getMCLParent().setColumnWidth(resizeColumn, originalSize+(e.getX()-resizeloc)); //updates size as well
	 		
	 		resizeflag=false;
	 		resizeloc=-1;
	 		resizeColumn=-1;
	 	}
	 }
	 
	 private boolean resizeEnabled() {
	 	return (header.getNumberOfColumns()!=1);
	 }
}