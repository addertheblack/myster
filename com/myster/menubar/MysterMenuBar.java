/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.menubar;

import java.awt.*;
import com.myster.menubar.event.*;
import com.general.events.EventDispatcher;
import com.general.events.SyncEventDispatcher;
import java.util.Vector;


/**
*	Is the global Myster MenuBar. NOTE: On the macintosh it's important to have a global menu bar.
*	The menu bar object has a groovy constructor that works with the MysterMenuObject
*	in order to make the taks of adding new menu items easy.
*
*/

public class MysterMenuBar extends MenuBar {
	//private Menu file, edit, myster;
	//private MysterMenuItem[] fileitems,edititems,mysteritems;
	
	private final int SIZEOFFILEMENU=5;
	private final int SIZEOFEDITMENU=10;
	private final int SIZEOFMYSTERMENU=3;
	
	private static final NullAction NULL=new NullAction();
	
	/**
	*	Adds the standar Myster MenuBar to the frame.
	*/
	
	public MysterMenuBar(Frame hostframe) {
		/*
		file	=	new Menu("File");
		edit	=	new Menu("Edit");
		myster	=	new Menu("Special");
		
		fileitems	=	new MysterMenuItem[SIZEOFFILEMENU];
		edititems	=	new MysterMenuItem[SIZEOFEDITMENU];
		mysteritems	=	new MysterMenuItem[SIZEOFMYSTERMENU];
		
		//File menu items
		fileitems				=	new MysterMenuItem[SIZEOFFILEMENU];
		fileitems[0]			=	new MysterMenuItem("New Search", 						new NewSearchWindowAction()	, java.awt.event.KeyEvent.VK_N);
		fileitems[1]			=	new MysterMenuItem("New Peer-to-Peer Connection", 		new NewClientWindowAction()	, java.awt.event.KeyEvent.VK_N, true);
		fileitems[2]			=	new MysterMenuItem("Close Window", 						new CloseWindowAction(hostframe),java.awt.event.KeyEvent.VK_W);
		fileitems[3]			=	new MysterMenuItem("-", 								NULL);
		fileitems[4]			=	new MysterMenuItem("Quit", 								new QuitMenuAction()		, java.awt.event.KeyEvent.VK_Q);
			
		//Edit menu items
		edititems				=	new MysterMenuItem[SIZEOFEDITMENU];
		edititems[0]			=	new MysterMenuItem("Undo",								NULL);
		edititems[1]			=	new MysterMenuItem("-", 								NULL);
		edititems[2]			=	new MysterMenuItem("Cut", 								NULL);
		edititems[3]			=	new MysterMenuItem("Copy (use command-c)", 				NULL);
		edititems[4]			=	new MysterMenuItem("Paste (use command-v)", 			NULL);
		edititems[5]			=	new MysterMenuItem("Clear", 							NULL);
		edititems[6]			=	new MysterMenuItem("-", 								NULL);
		edititems[7]			=	new MysterMenuItem("Select All",						NULL);
		edititems[8]			=	new MysterMenuItem("-", 								NULL);
		edititems[9]			=	new MysterMenuItem("Preferences", 						new PreferencesAction(),	java.awt.event.KeyEvent.VK_SEMICOLON);
		
		//Disable all Edit menu commands
		for (int i=0; i<edititems.length-1; i++) {
			edititems[i].enable(false);
		}
		
		//Myster menu items
		mysteritems				=	new MysterMenuItem[SIZEOFMYSTERMENU];
		mysteritems[0]			=	new MysterMenuItem("Add IP",							new AddIPMenuAction(hostframe));
		mysteritems[1]			=	new MysterMenuItem("Show Server Stats", 				new StatsWindowAction(), 	java.awt.event.KeyEvent.VK_S, true);
		mysteritems[2]			=	new MysterMenuItem("Show tracker",						new TrackerWindowAction(), 	java.awt.event.KeyEvent.VK_T);
		
		//make menus
		makeMenu(fileitems		,	file);
		makeMenu(edititems		,	edit);
		makeMenu(mysteritems	,	myster);
		
		//make menu bar
		add(file	);
		add(edit	);
		add(myster	);

		hostframe.setMenuBar(this);
		*/
	}
	
	private MysterMenuBar(MenuBarListener listener) {
		
	
		
	}
	

	/*
	private void makeMenu(MysterMenuItem[] items, Menu menu) {
		for (int i=0; i<items.length; i++) {
			if (items[i].getLabel().equals("-")) menu.addSeparator();
			else menu.add(items[i]);
		}
	}
	*/
	
	

	
	
	
	
	/** Static sub-system is below */
	static EventDispatcher dispatcher=new SyncEventDispatcher();
	private static MysterMenuBarFactory impl;
	private static Vector file, edit, special, menuBar, plugins;
	private static MysterMenuFactory pluginMenuFactory;
	
	private static synchronized MysterMenuBarFactory getFactory() {
		if (impl==null) {
			file		=	new Vector();
			edit		=	new Vector();
			special	=	new Vector();
			
			//File menu items
			file.addElement(new MysterMenuItemFactory("New Search", 						new NewSearchWindowAction(), 		java.awt.event.KeyEvent.VK_N));
			file.addElement(new MysterMenuItemFactory("New Peer-to-Peer Connection", 		new NewClientWindowAction(), 		java.awt.event.KeyEvent.VK_N, true));
			file.addElement(new MysterMenuItemFactory("Close Window", 						new CloseWindowAction(),	java.awt.event.KeyEvent.VK_W));
			file.addElement(new MysterMenuItemFactory("-", 									NULL));
			file.addElement(new MysterMenuItemFactory("Quit", 								new QuitMenuAction(),				java.awt.event.KeyEvent.VK_Q));
				
			//Edit menu items
			edit.addElement(new MysterMenuItemFactory("Undo",								NULL));
			edit.addElement(new MysterMenuItemFactory("-", 									NULL));
			edit.addElement(new MysterMenuItemFactory("Cut", 								NULL));
			edit.addElement(new MysterMenuItemFactory("Copy (use command-c)", 				NULL));
			edit.addElement(new MysterMenuItemFactory("Paste (use command-v)", 				NULL));
			edit.addElement(new MysterMenuItemFactory("Clear", 								NULL));
			edit.addElement(new MysterMenuItemFactory("-", 									NULL));
			edit.addElement(new MysterMenuItemFactory("Select All",							NULL));
			edit.addElement(new MysterMenuItemFactory("-", 									NULL));
			edit.addElement(new MysterMenuItemFactory("Preferences", 						new PreferencesAction(),			java.awt.event.KeyEvent.VK_SEMICOLON));
			
			//Disable all Edit menu commands
			for (int i=0; i<edit.size()-1; i++) {
				((MysterMenuItemFactory)(edit.elementAt(i))).setEnabled(false);
			}
			
			//Myster menu items
			special.addElement(new MysterMenuItemFactory("Add IP",							new AddIPMenuAction(new Frame())));
			special.addElement(new MysterMenuItemFactory("Show Server Stats", 				new StatsWindowAction(), 			java.awt.event.KeyEvent.VK_S, true));
			special.addElement(new MysterMenuItemFactory("Show tracker",					new TrackerWindowAction(), 			java.awt.event.KeyEvent.VK_T));
			
			//Myster plugins Menu
			plugins=new Vector();
			pluginMenuFactory=new MysterMenuFactory("Plugins", plugins);
	
			menuBar=new Vector();
			menuBar.addElement(new MysterMenuFactory("File",	file));
			menuBar.addElement(new MysterMenuFactory("Edit",	edit));
			menuBar.addElement(new MysterMenuFactory("Special",	special));
			//plugins menu is not added here.
			
			
			
			impl=new MysterMenuBarFactory(menuBar);
		}
		
		return impl;
	}
	
	public static void addMenuListener(MenuBarListener listener) {	//Not sycnhronized
		dispatcher.addListener(listener);
		listener.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
	}
	
	public static void removeMenuLister(MenuBarListener listener) { //Not synchronized
		dispatcher.removeListener(listener);
	}
	
	public static boolean removeBuiltInMenu(String menuName) {
		for (int i=0; i<menuBar.size(); i++) {
			if (((MysterMenuFactory)(menuBar.elementAt(i))).getName().equalsIgnoreCase(menuName)) {
				menuBar.removeElementAt(i);
				dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
				return true;
			}
		}
		return false;
	}
	
	public static boolean removeBuiltInMenuItem(String menuName, String menuItem) {
		if (menuItem.equals("-")) return false;
		
		if (menuName.equalsIgnoreCase("File")) {
			return removeMenuItem(file,  menuItem);
		} else if (menuName.equalsIgnoreCase("Edit")) {
			return removeMenuItem(edit,  menuItem);
		} else if (menuName.equalsIgnoreCase("Special")) {
			return removeMenuItem(special,  menuItem);
		}
		
		return false;
	}
	
	private static boolean removeMenuItem(Vector vector, String menuItem) { //ugh...
		for (int i=0; i<vector.size(); i++) {
			MysterMenuItemFactory item=(MysterMenuItemFactory)(vector.elementAt(i)); 
			if (item.getName().equalsIgnoreCase(menuItem)) {
				vector.removeElementAt(i);
				while 	((vector.size()>0) && 
						(((MysterMenuItemFactory)(vector.elementAt(vector.size()-1))).getName().equals("-"))) {
					vector.removeElementAt(vector.size()-1);
				}
				
				updateMenuBars();
				
				return true;
			}
		}
		
		return false;
	}
	
	public static void updateMenuBars() {
		dispatcher.fireEvent(new MenuBarEvent(MenuBarEvent.BAR_CHANGED, getFactory()));
	}
	
	public static void addMenu(MysterMenuFactory factory) {
		menuBar.addElement(factory);
		updateMenuBars();
	}
	
	public static boolean removeMenu(MysterMenuFactory factory) {
		boolean sucess=menuBar.removeElement(factory);
		updateMenuBars();
		return sucess;
	}
	
	public static void addMenuItem(MysterMenuItemFactory menuItemfactory) {
		if (plugins.size()==0) {
			menuBar.addElement(pluginMenuFactory);
		}
		plugins.addElement(menuItemfactory);
		updateMenuBars();
	}
	
	public static boolean removeMenuItem(MysterMenuItemFactory menuItemfactory) {
		boolean success=false;
		
		success=plugins.removeElement(menuItemfactory);
		
		if (plugins.size()==0) {
			menuBar.removeElement(pluginMenuFactory);
		}
		
		updateMenuBars();
		return success;
	}
	
	private static Vector getPreBuiltMenuBar() {
		Vector menuBarVector=new Vector();
		
		
		
		return menuBarVector;
	}
}