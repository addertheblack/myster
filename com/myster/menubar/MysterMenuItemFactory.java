package com.myster.menubar;

import java.awt.event.ActionListener;
import java.awt.MenuItem; 
import java.awt.MenuShortcut;

public class MysterMenuItemFactory {
	ActionListener action;
	String name;
	int shortcut;
	boolean useShift;
	boolean isDisabled=false;

	public MysterMenuItemFactory() {
		this("-");
	}

	public MysterMenuItemFactory(String s) {
		this(s,null);
	}

	public MysterMenuItemFactory(String s, ActionListener a) {
		this(s,a,-1);
	}
	
	public MysterMenuItemFactory(String s, ActionListener a, int shortcut ) {
		this(s,a,shortcut,false);
	}
	
	public MysterMenuItemFactory(String s, ActionListener a, int shortcut, boolean useShift ) {
		this.action=a;
		this.name=s;
		this.shortcut=shortcut;
		this.useShift=useShift;
	}
	
	public MenuItem makeMenuItem() {
		MenuItem menuItem;
		
		if (shortcut!=-1) {
			menuItem=new MenuItem(com.myster.util.I18n.tr(name), new MenuShortcut(shortcut,useShift));
		} else {
			menuItem=new MenuItem(com.myster.util.I18n.tr(name));
		}
		
		if (action!=null) {
			menuItem.addActionListener(action);
		}
		
		menuItem.setEnabled(!isDisabled);
		
		return menuItem;
	}
	
	public void setEnabled(boolean b) {
		isDisabled=!b;
	}
	
	public String getName() {
		return name;
	}
}