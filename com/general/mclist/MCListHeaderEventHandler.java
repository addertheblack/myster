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
	 int lastcolumnclicked=0;
	 
	 public MCListHeaderEventHandler(MCListHeader h) {header=h;}
	 
	 int resizeloc=-1;
	 boolean resizeflag=false;
	 int resizeColumn=-1;
	 int originalSize=-1;
	 
	 public void mousePressed(MouseEvent e){
	 	if (header.isOnBorder(e.getX())) {
	 		resizeloc=e.getX();
	 		resizeflag=true;
	 		resizeColumn=header.getResizeColumn(e.getX());
	 		originalSize=header.getColumnWidth(resizeColumn);
	 		return;
	 	}
	 
	 	int columnclicked=header.getColumnOfClick(e.getX(), e.getY());
	 	
	 	if (columnclicked==lastcolumnclicked) header.getMCLParent().reverseSortOrder();
	 	else header.getMCLParent().sortBy(columnclicked);
	 	
	 	lastcolumnclicked=columnclicked;
	 	
	 	header.repaint();
	 }
	 
	 public void mouseDragged(MouseEvent e) {
	 	if (resizeflag) {
	 		if (resizeloc==-1||resizeColumn==-1) {
	 			throw new RuntimeException("Garbage in Mouse dragged");
	 		}
	 		header.setColumnWidth(resizeColumn, originalSize+(e.getX()-resizeloc));
	 		header.getMCLParent().repaint();
	 	}
	 }

 	public void mouseMoved(MouseEvent e) {
 		if (header.isOnBorder(e.getX())) {
 			header.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR ));
 		} else {
 			header.setCursor(Cursor.getDefaultCursor());
 		}
 	} 
	 
	 public void mouseReleased(MouseEvent e) {
	 	if (resizeflag) {
	 		if (resizeloc==-1||resizeColumn==-1) {
	 			throw new RuntimeException("Garbage in Mouse dragged");
	 		}
	 		
	 		header.getMCLParent().setColumnWidth(resizeColumn, originalSize+(e.getX()-resizeloc)); //updates size as well
	 		
	 		resizeflag=false;
	 		resizeloc=-1;
	 		resizeColumn=-1;
	 	}
	 }
}