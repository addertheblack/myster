package com.myster.server;

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


/**
*	This code is responsible for managing everything to do with banners. It is
*	quite nasty.
*/

public class BannersManager {
	
	private static final String KEY_IN_PREFS = "/Banners Preferences/";
	private static final String PATH_TO_URLS = "/URLs/";
		
	private static final String PARTIAL_PATH_TO_IMAGES 	= "i";
	private static final String PARTIAL_PATH_TO_URLS	= "u";

	private static final String IMAGE_DIRECTORY = "Images";

	private static boolean prefsHasChangedFlag = true; //to get manager to initally read the prefs
	private static Hashtable imageNamesToUrls = new Hashtable(); 
	
	private static String[] imageNames;
	private static int currentIndex = 0;

	public static void init() {
		Preferences.getInstance().addPanel(new BannersPreferences());
	}
		

	public static synchronized String getNextImageName() {
		if (prefsHasChangedFlag) updateEverything();
		
		if (imageNames == null || imageNames.length == 0) return null;
		
	 	if (currentIndex > imageNames.length-1) currentIndex = 0;
	 	
	 	return imageNames[currentIndex++]; //it's post increment
	}
	
	public static synchronized File getFileFromImageName(String imageName) {
		return new File(IMAGE_DIRECTORY + File.separator + imageName);
	}
	
	public static synchronized String getURLFromImageName(String imageName) {
		String string = (String)imageNamesToUrls.get(imageName);
		
		return (string == null?"":string);
	}
	
	private static synchronized void prefsHaveChanged() {
		prefsHasChangedFlag = true;
	}
	
	private static synchronized Hashtable getPrefsAsHash() {
		PreferencesMML mmlPrefs = new PreferencesMML(Preferences.getInstance().getAsMML(KEY_IN_PREFS, new PreferencesMML()).copyMML());
		
		mmlPrefs.setTrace(true);
		
		Vector folders = mmlPrefs.list(PATH_TO_URLS);
		Hashtable hashtable = new Hashtable();
		
		if (folders != null) {
			for (int i = 0 ; i < folders.size() ; i++) {
				String subFolder = (String)folders.elementAt(i);
				
				String imageName 	= mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_IMAGES);
				String url 			= mmlPrefs.get(PATH_TO_URLS + subFolder + "/" + PARTIAL_PATH_TO_URLS);
				
				if (imageName == null | url == null) continue; //notice how I am using | here and not ||
				
				hashtable.put(imageName, url);
			}
		}
		
		return hashtable;
	}
	
	private static synchronized void setPrefsMML(PreferencesMML mml) { //is a string -> String hash of imageNames to URLs (strongly typed language be damned!)
		Preferences.getInstance().put(KEY_IN_PREFS, mml);
		prefsHaveChanged(); //signal that this object should be re-inited from prefs.
	}
	
	private static synchronized void updateEverything() {
		prefsHasChangedFlag = false;
		
		imageNamesToUrls = getPrefsAsHash();
		
		imageNames = getImageNameList();
		
		currentIndex = 0;
	}
	
	public static synchronized String[] getImageNameList() {
		File imagesFolder = new File(IMAGE_DIRECTORY);
		
		if (imagesFolder.exists() && imagesFolder.isDirectory()) {
			String[] files = imagesFolder.list();
			
			int counter = 0;
			for (int i = 0; i < files.length ; i++) {
				if (isAnImageName(files[i])) {
					counter ++;
				}
			}
			
			String[] filteredFileList = new String[counter];
			
			counter = 0;
			for (int i = 0; i < files.length; i++) {
				if (isAnImageName(files[i])) {
					filteredFileList[counter++] = files[i];
				}
			}
			
			return filteredFileList;
		} else {
			return null; //error
		}
	}
	
	private static synchronized boolean isAnImageName(String imageName) { //code reuse
		return (imageName.toLowerCase().endsWith(".jpg") || imageName.toLowerCase().endsWith(".gif"));
	}
		
	private static class BannersPreferences extends PreferencesPanel {
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
					"you would like to tell people about. Double click on a image below to "+
					"associate it with a web address"); //mental note, put in I18n
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
				long lastDoubleClick = 0;
			
				public void actionPerformed(ActionEvent e) {
					synchronized (this) {
						if (System.currentTimeMillis() - lastDoubleClick < 1000) return ; //work around for stupid fucking bug in MacOS MRJ 1.3.X
						
						lastDoubleClick = System.currentTimeMillis();
					}
					
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
		
		public void save() {
			PreferencesMML mmlPrefs = new PreferencesMML();
			
			mmlPrefs.setTrace(true);
			
			for (int i = 0; i < list.getItemCount(); i++) {
				String url = (String)hashtable.get(list.getItem(i));
				
				if (url == null || url.equals("")) continue; //Don't bother saving this one, skip to the next one.
			
				mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_IMAGES, list.getItem(i));
				mmlPrefs.put(PATH_TO_URLS + i + "/" + PARTIAL_PATH_TO_URLS, url);
			}
			
			setPrefsMML(mmlPrefs);
		}
		
		public void reset() {
			hashtable = getPrefsAsHash();
			
			refreshImagesList();
		}
		
		private static final String IMAGE_DIRECTORY="Images/";//fix
		private void refreshImagesList() {
			list.clear();
			
			String[] directoryListing = getImageNameList();
			
			if (directoryListing == null) return;
			
			for (int i = 0; i < directoryListing.length; i++) {
				list.add(directoryListing[i]);
			}
		}
		
		public String getKey() {
			return com.myster.util.I18n.tr("Banners");
		}
	}
}

