package com.myster.menubar;


import java.util.Vector;
import java.awt.Menu;

public class MysterMenuFactory {
	Vector mysterMenuItemFactories;
	String name;

	public MysterMenuFactory(String name, Vector mysterMenuItemFactories) {
		this.name=name;
		this.mysterMenuItemFactories=mysterMenuItemFactories;
	}
	
	public Menu makeMenu() {
		Menu menu=new Menu(name);
		
		for (int i=0; i<mysterMenuItemFactories.size(); i++) {
			menu.add(((MysterMenuItemFactory)mysterMenuItemFactories.elementAt(i)).makeMenuItem());
		}
		
		return menu;
	}
	
	public String getName() {
		return name;
	}
}