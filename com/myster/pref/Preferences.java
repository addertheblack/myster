/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.pref;

import java.awt.*;
import java.util.Hashtable;
import java.io.*;
import com.myster.util.MysterThread;
import com.general.util.Semaphore;
import java.util.Vector;
import com.myster.mml.MML;
import com.myster.pref.ui.PreferencesDialogBox; //bad, get rid of. no GUI stuff should be references directly here.
import com.myster.pref.ui.PreferencesPanel; //bad, get rid of. no GUI stuff should be references directly here.


/**
*	System globals are stored in;
*		"Globals/" :
*					Connection type:	"Globals/speed/"
*
*	FileManager Paths are stored in :
*		"File manager/paths/"
*
*	Myster IPs are stored in :
*		"Myster IPs/"
*					Specific IP: "/Myster IPs/<IP or domain name>/"
*					Specific Myster IP info is stored somewhere in this path.
*
*	IPList information is stored in :
*		"IPLists/<Type>/"
*					Specific IP NAME OF IP as a DIR as in: "/IPLists/<Type>/<Name of IP>/"
*							This name or IP should be identical to the one stored above..
*
*/

//USES THE SINGLETON DESIGN PATTERN!!!! READ UP ON IT!
public class Preferences {
	private Hashtable data;
	private PreferencesDialogBox prefsWindow;
	
	private static Preferences pref;
	
	private final String PATH="mysterprefs.mml";
	private final String BACKUP="mysterprefs.mml.backup";
	
	public static final int DEBUG=1;
	public static final int NORMAL=0;
	
	public static final String HEADER_STRING="New myster prefs format";
	
	private Preferences() {
		loadFile();
		savethread=new SaveThread();
		savethread.start();
		prefsWindow=new PreferencesDialogBox();
	}
	
	static final String WINDOW_KEEPER_KEY="MysterPrefsGUI";
	static com.myster.ui.WindowLocationKeeper windowKeeper=new com.myster.ui.WindowLocationKeeper(WINDOW_KEEPER_KEY);
	
	public static void initWindowLocations() {
		Rectangle[] rect=com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);
		if (rect.length>0) {
			getInstance().prefsWindow.setBounds(rect[0]);
			getInstance().setGUI(true);
		}
	}
	
	/**
	*	Needed in order to get an instance of the preference object. This is included
	*	Since their should only be one instance of the preferences object but
	*	The routines should not have to be static...
	*/	
	public static synchronized Preferences getInstance() {
		
		if (pref==null) {
			pref=new Preferences();
			windowKeeper.addFrame(pref.prefsWindow);
		}
		
		return pref;
	}
	
	public synchronized void setGUI(boolean b) {
		if (b) {
			prefsWindow.show();
		} else {
			prefsWindow.hide();
		}
	}
	
	
	/**
	*	Adds a preferences panel to the preferences GUI under the type and subType.
	*	inf hierarchical prefs are not supported at this time.
	*
	*	Pluggin writters are asked to avoid messing with other module's pref panels.
	*/
	public void addPanel(PreferencesPanel panel) {
		prefsWindow.addPanel(panel);
	}
	
	
	/**
	*	Gets the value for a path. returns null if path not initilized or invalid.
	*/
	public synchronized String get(String key) {
		return (String)(data.get(key));
	}
	
	/**
	*	Gets the value for a path. returns "" if path not initilized or invalid.
	*/
	public synchronized String query(String key){
		String temp=(String)(data.get(key));
		if (temp==null) return "";
		return temp;
	}
	
	public synchronized MML getAsMML(String key) {
		return (MML)(data.get(key));
	}
	
	public synchronized PreferencesMML getAsMML(String key, PreferencesMML defaultMML) {
		PreferencesMML mml=(PreferencesMML)(data.get(key));
		
		return (mml==null?defaultMML:mml);
	}
	
	/**
	*	
	*	Deletes that path and all sub paths.
	*/
	public synchronized String remove(String key) {
		String s_temp=(String)(data.remove(key));
		save();
		return s_temp;
	}
	
	/**
	*
	*	Checks if path exists.
	*/
	public synchronized boolean containsKey(String key) {
		return data.containsKey(key);
	}
	
	public synchronized Object put(String key, String value) {
		Object s_temp=data.put(key,value);
		save();
		return s_temp;
	}
	
	public synchronized Object put(String key, MML value) {
		Object mml_temp=data.put(key,value);
		save();
		return mml_temp;
	}
	
	/** private function
	*/
	private synchronized void loadFile() {
		//try FILE try BACKUP then make a new file.
		
		try {
			try {
				loadFromFile(PATH);
			} catch (Exception ex) {
			ex.printStackTrace();
				loadFromFile(BACKUP);
			}
		} catch (IOException ex) {
			System.out.println("No prefs file or prefs file is corrupt or prefs file is old. Making a new one.");
			ex.printStackTrace();
		} catch (ClassCastException ex) {
			ex.printStackTrace();
			System.out.println("Serious bad class is happening");
		} catch (ClassNotFoundException ex) {
			ex.printStackTrace();
		} finally {
			if (data==null) data=new Hashtable();
		}
	}
	
	private synchronized void loadFromFile(String path) throws IOException,ClassNotFoundException,ClassCastException {
		ObjectInputStream in=null;
		try {
			File f=new File(path);
			if (!f.exists()) {
				throw new IOException("Toss and catch!");
			}
			
			in=new ObjectInputStream(new FileInputStream(f));
			String s_temp=(String)(in.readObject());
			if (s_temp.equals(HEADER_STRING)) {
				data=(Hashtable)(in.readObject());
			}
		} finally {
			try {in.close();} catch (Exception ex){}
		}
	}
	
	/**
	*	Saves changes to the preferences. It should be caled if you have made a change.
	*	Ideally the prefferences should save themselves, however they don't always since
	*	Speed will suffer. Calling this routine makes sure the prefferences have been saved.
	*
	*	Returns true if the save has been successfull and false if it has not.
	*
	
		NOTE: The creation of the string to be saved must be synchronized with the entire prefference object.
		The actualy writing to disk just needs to be semaphored.
	*/
	
	Semaphore writeToDisk=new Semaphore (1);
	private boolean saveFile(int flag) {
		String stringToSave;
		

		
		writeToDisk.waitx(); //SEM BLOCK START!


		try {
			File finalfile	=new File(PATH);
			File backupfile	=new File(BACKUP);
			if(backupfile.exists()) backupfile.delete();	//on the mac the next line tosses an excption if file already exists.
			ObjectOutputStream out=new ObjectOutputStream(new FileOutputStream(backupfile));
			out.writeObject(HEADER_STRING);

			synchronized (this) {
				out.writeObject(data);
			}
			
			out.flush();
			out.close();
			if (!finalfile.delete()&&finalfile.exists()) throw new Exception("Delete not sucessfull! is file in use??");
			if (!backupfile.renameTo(finalfile)) throw new Exception("Rename not sucessfull! is file in use??");
		} catch (Exception ex) {
			if (flag==DEBUG) {
				ex.printStackTrace();
			}

			return false;
		} finally {
			writeToDisk.signalx();//SEM BLOCK END HERE TO!
		}
		
		System.out.println("Preferences have been saved.");
		return true;
	}
	
	/**
	*	Same as saveFile(int) however, sets the debug flag to true to return more information about
	*	an error if one occures during the save. 
	*/
	
	SaveThread savethread; //enables asychronous saving.
	private boolean saveFile()  { //should not be synchronized, shouyld return imedeately.
									//Called by saver thread.
		saveFile(DEBUG);
		return true; //programs need not know if there has been an error.
		//return saveFile(DEBUG);
	}
	
	/**
	*	Same as saveFile(); only does saving asychronously.
	*/
	public boolean save() {
		savethread.asynchronousSave();
		return true; //wow.. retarded...
	}
	
	/**
	*	Flushes the Preferences to disk
	* <p>
	*	When preferences are told to save, the save opperation might not happen
	*	imediately since saved are batched automatically to avoid acessive
	*	disk activity. Calling this function guarentees the information will be saved
	* <p>
	*	Warning: is blocking and accesses io so this opperation is slow.
	*/
	public void flush() {
		saveFile();
	}
	
	
	private class SaveThread extends MysterThread {
		private Semaphore sem=new Semaphore(0); 
		private volatile boolean needsSave=false; //is assumed opperations are synchronized.
		private static final long SAVETIME=5*1000; //1000 == 1 sec.
		
		public void run() {
			setPriority(Thread.MIN_PRIORITY);
			do {
				sem.waitx();
				if (needsSave) { //this is strictly not nessesairy! good to check assumptions.
					saveFile();
					synchronized (this) { //abs(essential). is not here bad things happen.
						needsSave=false;
					}
					try {sleep(SAVETIME);} catch (Exception ex){}
				} else {
					System.out.println("FUNDEMENTAL ASSUMPTION VIOLATED IN SAVE THREAD");
				}
			} while (true);
		}
		
		public synchronized void asynchronousSave() {
			if (!needsSave) {
				needsSave=true;
				sem.signalx();
			}
		}
	}
}