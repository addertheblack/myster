package com.myster.ui;


import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ComponentEvent;
import java.awt.*;

public class MysterFrame extends Frame {	
	static int xStart=5;
	static int yStart=5;

	public MysterFrame() {
		super();//explicit good
		
		initEvents();
	}
	
	public MysterFrame(String windowName) {
		super(windowName);
		
		setTitle(windowName);
		
		initEvents();
	}
	
	public void setTitle(String windowName) {
		super.setTitle(com.myster.Myster.ON_LINUX?windowName+" - Myster":windowName);
	}
	
	private static synchronized Point getWindowStartingLocation() {
		Point location=new Point(xStart, yStart);
		xStart+=20;
		yStart+=20;
		
		if (xStart>250) {
			xStart=0;
			yStart=0;
		}
		
		return location;
	}
	
	private void initEvents() {
		setLocation(getWindowStartingLocation());
	
		addWindowListener(new WindowListener() {	//inline class
		
			public void windowOpened(WindowEvent e) {

			}

			public void windowClosing(WindowEvent e) {
				//cleanup goes here
				WindowManager.removeWindow(MysterFrame.this);
			}

			public void windowClosed(WindowEvent e) {

			}

			public void windowIconified(WindowEvent e) {
			
			}


			public void windowDeiconified(WindowEvent e) {
			
			}


			public void windowActivated(WindowEvent e) {
				WindowManager.setFrontWindow(MysterFrame.this);
			}

			public void windowDeactivated(WindowEvent e) {
				
			}
		});
		
		addComponentListener(new ComponentListener() {
		
			public void componentResized(ComponentEvent e) {
			
			}

			public void componentMoved(ComponentEvent e) {
			
			}

			public void componentShown(ComponentEvent e) {
				WindowManager.addWindow(MysterFrame.this);
			}

			public void componentHidden(ComponentEvent e) {
				WindowManager.removeWindow(MysterFrame.this);
			}
		
		});
		
		com.myster.menubar.MysterMenuBar.addMenuListener(new com.myster.menubar.event.MenuBarListener() {
			public void stateChanged(com.myster.menubar.event.MenuBarEvent e) {
				MenuBar oldMenuBar=getMenuBar();
				
				if (oldMenuBar==null) {
					setMenuBar(e.makeNewMenuBar());
				} else {
					MenuBar newMenuBar=e.makeNewMenuBar();
					int maxOldMenus=oldMenuBar.getMenuCount();
					int maxNewMenus=newMenuBar.getMenuCount();
					
					if (maxNewMenus>0) oldMenuBar.add(newMenuBar.getMenu(0));
					
					for (int i=maxOldMenus-1; i>=0; i--) {
						oldMenuBar.remove(i);
					}
					
					for (int i=1; i<maxNewMenus; i++) {
						oldMenuBar.add(newMenuBar.getMenu(0));
					}
				}
			}
		});
	
	}
	
	public void closeWindowEvent() {
		processWindowEvent(new WindowEvent(this,WindowEvent.WINDOW_CLOSING));
	}
	
	public void close() {
		closeWindowEvent();
		setVisible(false);
	}
}