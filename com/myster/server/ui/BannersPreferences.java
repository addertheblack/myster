package com.myster.server.ui;

import java.io.File;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;
import java.util.Hashtable;

import com.general.util.MessagePanel;
import com.general.util.AskDialog;

import com.myster.mml.RobustMML;
import com.myster.pref.Preferences;
import com.myster.pref.PreferencesMML;
import com.myster.pref.ui.PreferencesPanel;

public class BannersPreferences extends PreferencesPanel {
	public static final String KEY_IN_PREFS = "/Banners Preferences/";
	public static final String PATH_TO_URLS = "/URLs/";
	
	public static final String PARTIAL_PATH_TO_IMAGES 	= "i";
	public static final String PARTIAL_PATH_TO_URLS		= "u";

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
		list.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String currentURL = (String)hashtable.get(e.getActionCommand());
				
				if (currentURL == null) currentURL = "";

				AskDialog askDialog = new AskDialog(BannersPreferences.this.getFrame(), com.myster.util.I18n.tr("What URL would you like to link to this image?\n(Leave it blank to remove the url completely)"), currentURL);
			
				String answerString = askDialog.ask();
				
				if (answerString == null) return; //box has been canceled.
				
				hashtable.put(e.getActionCommand(), answerString);
			}
		});
		add(list);
	}
	
	public static void init() {
		Preferences.getInstance().addPanel(new BannersPreferences());
	}
	
	public void save() {
		PreferencesMML mmlPrefs = new PreferencesMML();
		
		mmlPrefs.setTrace(true);
		
		for (int i = 0; i < list.getItemCount(); i++) {
			String url = (String)hashtable.get(list.getItem(i));
			
			if (url == null || url.equals("")) continue; //Don't bother saving this one, skip to the next one.
		
			mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_IMAGES, list.getItem(i));
			mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_URLS, url);
		}
		
		Preferences.getInstance().put(KEY_IN_PREFS, mmlPrefs);
	}
	
	public void reset() {
		PreferencesMML mmlPrefs = new PreferencesMML(Preferences.getInstance().getAsMML(KEY_IN_PREFS, new PreferencesMML()).copyMML());
		
		mmlPrefs.setTrace(true);
		
		Vector folders = mmlPrefs.list(PATH_TO_URLS);
		hashtable = new Hashtable();
		
		if (folders != null) {
			for (int i = 0 ; i < folders.size() ; i++) {
				String subFolder = (String)folders.elementAt(i);
				
				String imageName 	= mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_IMAGES);
				String url 			= mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_URLS);
				
				if (imageName == null | url == null) continue; //notice how I am using | here and not ||
				
				hashtable.put(imageName, url);
			}
		}
		
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