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


/**
*	Is the global Myster MenuBar. NOTE: On the macintosh it's important to have a global menu bar.
*	The menu bar object has a groovy constructor that works with the MysterMenuObject
*	in order to make the taks of adding new menu items easy.
*
*/

public class MysterMenuBar extends MenuBar {
	private Menu file, edit, myster;
	private MysterMenuItem[] fileitems,edititems,mysteritems;
	
	private final int SIZEOFFILEMENU=5;
	private final int SIZEOFEDITMENU=10;
	private final int SIZEOFMYSTERMENU=3;
	
	private static final NullAction NULL=new NullAction();
	
	/**
	*	Adds the standar Myster MenuBar to the frame.
	*/
	
	public MysterMenuBar(Frame hostframe) {

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
	}
	
	private MysterMenuBar() {
		
		
	}
	

	
	private void makeMenu(MysterMenuItem[] items, Menu menu) {
		for (int i=0; i<items.length; i++) {
			if (items[i].getLabel().equals("-")) menu.addSeparator();
			else menu.add(items[i]);
		}
	}
	
	
	
	
	
	
	/** Static sub-system is below */
	
	
	

}