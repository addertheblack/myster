package com.myster.server.ui;

import java.io.File;
import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;

import com.general.util.MessagePanel;

import com.myster.mml.RobustMML;
import com.myster.pref.Preferences;
import com.myster.pref.ui.PreferencesPanel;

public class BannersPreferences extends PreferencesPanel {
	public static final String KEY_IN_PREFS = "Banners Preferences/";

	private List list;
	private MessagePanel msg;
	private Button refreshButton;

	private Hashtable hashtable = new Hashtable();

	public static final int LIST_YSIZE	= 150;
	
	public static final int BUTTON_YSIZE= 25;
	
	public static final int PADDING		= 5;

	public BannersPreferences() {
		setSize(STD_XSIZE, STD_YSIZE);
		
		setLayout(null);
	
		msg = new MessagePanel(
				"This panel allows you to associate a web page with a banner image " +
				"so that people can click on a banner link and be directed to a " +
				"web page of your choice. This is usefull if you have a web site " +
				"you would like to tell people about."); //mental note, put in I18n
		msg.setLocation(PADDING, PADDING);
		msg.setSize(STD_XSIZE - 2*PADDING, STD_YSIZE - 4*PADDING - LIST_YSIZE - BUTTON_YSIZE);
		add(msg);
		
		refreshButton = new Button(com.myster.util.I18n.tr("Refresh"));
		refreshButton.setLocation(PADDING, STD_YSIZE - LIST_YSIZE - PADDING - PADDING - BUTTON_YSIZE);
		refreshButton.setSize(150, BUTTON_YSIZE);
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshImagesList();
			}
		});
		add(refreshButton);
	
		list = new List();
		list.setLocation(PADDING, STD_YSIZE - LIST_YSIZE - PADDING);
		list.setSize(STD_XSIZE - 2*PADDING, LIST_YSIZE);
		list.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				System.out.println("Event on the list.");
			}
		});
		add(list);
	}
	
	public static void init() {
		Preferences.getInstance().addPanel(new BannersPreferences());
	}
	
	public void save() {
		RobustMML mmlPrefs = new RobustMML();
	
		//Preferences.getInstance.put(mmlPrefs);
	}
	
	public void reset(){
		//RobustMML mmlPrefs = new RobustMML(Preferences.getInstance().getAsMML().copyMML());
		
		refreshImagesList();
	}
	
	private static final String IMAGE_DIRECTORY=new String("Images/");//fix
	private void refreshImagesList() {
		File imagesFolder = new File(IMAGE_DIRECTORY);
		
		String[] directoryListing = imagesFolder.list();
		
		list.clear();
		
		for (int i = 0; i < directoryListing.length; i++) {
			list.add(directoryListing[i]);
		}
	}
	
	public String getKey() {
		return com.myster.util.I18n.tr("Banners");
	}
}