package com.myster.type;

import java.io.*;
import java.util.Vector;
import java.util.Hashtable;
import java.util.StringTokenizer;

import com.myster.pref.PreferencesMML;
import com.general.events.SyncEventDispatcher;
//import Myster;

/**
* 
*/
public abstract class TypeDescriptionList {
	private static TypeDescriptionList defaultList;
	
	public static synchronized void init() {
		if (defaultList == null) defaultList = new DefaultTypeDescriptionList();
	}
	
	public static synchronized TypeDescriptionList getDefault() {
		init(); //possible pre-mature init here
		return defaultList;
	}
	
	public abstract TypeDescription[] getAllTypes() ;
	public abstract TypeDescription[] getEnabledTypes() ;
	public abstract boolean isTypeEnabled(MysterType type);
	public abstract boolean isTypeEnabledInPrefs(MysterType type);
	public abstract void addTypeListener(TypeListener l) ;
	public abstract void removeTypeListener(TypeListener l) ;
	public abstract void setEnabledType(MysterType type, boolean enable) ;
}


class DefaultTypeDescriptionList extends TypeDescriptionList {

	//Ok, so here's the situation
	//I designed this so that I could change types while the program is running
	//and have all modules auto update.. but it's too freaking long to program
	//so rather than nuke the code I've modified it to return the value that was
	//last saved. The system will still fire events but the list won't send the
	//values stored in the prefs..
	//See code for how to get back the intended behavior... (comments actually)
	TypeDescriptionElement[] types;
	TypeDescriptionElement[] oldTypes;
	TypeDescriptionElement[] workingTypes;
	
	
	
	SyncEventDispatcher dispatcher;

	public DefaultTypeDescriptionList() {
		TypeDescription[] list = loadDefaultTypeAndDescriptionList();;
		

		types = new TypeDescriptionElement[list.length];
		oldTypes = new TypeDescriptionElement[list.length];
		
		Hashtable hash = getEnabledFromPrefs();
		
		for (int i = 0; i < list.length; i++) {
			String string_bool = (String)(hash.get(list[i].getType().toString()));
			
			if (string_bool == null) {
				if (list[i].getType().toString().equals("PORN")) {
					string_bool = "FALSE";
				} else {
					string_bool = "TRUE";
				}
			}
			
			types[i] = new TypeDescriptionElement(list[i], (string_bool.equals("TRUE") ? true : false));
			oldTypes[i] = new TypeDescriptionElement(list[i], (string_bool.equals("TRUE") ? true : false));
		}
		
		workingTypes = oldTypes; //set working types to "types" variable. 
		
		dispatcher = new SyncEventDispatcher();
	}
	
	private static final String DEFAULT_LIST_KEY	= "DefaultTypeDescriptionList saved defaults";
	
	private static final String TYPE_KEY 			= "/type";
	private static final String TYPE_ENABLED 		= "/enabled";

	private Hashtable getEnabledFromPrefs() {
		com.myster.pref.Preferences pref = com.myster.pref.Preferences.getInstance();
		
		PreferencesMML mml = pref.getAsMML(DEFAULT_LIST_KEY, new PreferencesMML());
		
				mml.setTrace(true);
		
		Hashtable hash = new Hashtable();
		
		Vector list = mml.list("/");
		
		for (int i = 0; i < list.size(); i++) {
			hash.put(mml.get("/"+list.elementAt(i).toString()+TYPE_KEY), mml.get("/"+list.elementAt(i).toString()+TYPE_ENABLED));
		}
		
		return hash;
	}
	
	private void saveEverythingToDisk() {
	
		PreferencesMML mml = new PreferencesMML();
		
		for (int i = 0; i < types.length; i++) {
			String temp = "/"+i;
			
			mml.put(temp + TYPE_KEY		, types[i].getType().toString());
			mml.put(temp + TYPE_ENABLED	, (types[i].getEnabled() ? "TRUE" : "FALSE" ));
		}
		
		com.myster.pref.Preferences.getInstance().put(DEFAULT_LIST_KEY, mml);
	}

	//TypeDescription Methods
	public TypeDescription[] getAllTypes() {
		TypeDescription[] typeArray_temp = new TypeDescription[workingTypes.length];
		
		for (int i = 0; i < types.length; i++) {
			typeArray_temp[i] = workingTypes[i].getTypeDescription();
		}
		
		return typeArray_temp;
	}
	
	public TypeDescription[] getEnabledTypes() {
		int counter = 0;
		for (int i = 0; i < workingTypes.length; i++) {
			if (workingTypes[i].getEnabled()) counter++;
		}
		
		TypeDescription[] typeArray_temp = new TypeDescription[counter];
		counter = 0;
		for (int i = 0; i < types.length; i++) {
			if (workingTypes[i].getEnabled()) typeArray_temp[counter++] = types[i].getTypeDescription();
		}
		
		return typeArray_temp;
	}
	
	public boolean isTypeEnabled(MysterType type) {
		int index = getIndexFromType(type);
		
		return (index != -1 ? workingTypes[index].getEnabled() : false);
	}
	
	public boolean isTypeEnabledInPrefs(MysterType type) {
		int index = getIndexFromType(type);
		
		return (index != -1 ? types[index].getEnabled() : false);
	}
	
	public void addTypeListener(TypeListener listener) {
		dispatcher.addListener(listener);
	}
	
	public void removeTypeListener(TypeListener listener) {
		dispatcher.removeListener(listener);
	}
	
	public void setEnabledType(MysterType type, boolean enable) {
		int index = getIndexFromType(type);
		
		//errs
		if (index == -1) return; //no such type
		if (types[index].getEnabled() == enable) return;
		
		types[index].setEnabled(enable);
		
		saveEverythingToDisk();
		
		dispatcher.fireEvent(new TypeDescriptionEvent((enable ? TypeDescriptionEvent.ENABLE : TypeDescriptionEvent.DISABLE) , this, type));
	}

	private synchronized int getIndexFromType(MysterType type) {
		for (int i = 0; i < types.length; i++) {
			if (types[i].getType().equals(type)) return i;
		}
		
		return -1;
	}



	//statics
	private static synchronized TypeDescription[] loadDefaultTypeAndDescriptionList() {
		try {
			InputStream in = Class.forName("com.myster.Myster").getResourceAsStream("typedescriptionlist.txt");
			if (in==null) {
				System.out.println("There is no \"typedescriptionlist.txt\" file at com.myster level. Myster needs this file. Myster will exit now.");
				System.out.println("Please get a Type Description list.");
				
				com.general.util.AnswerDialog.simpleAlert("PROGRAMER'S ERROR: There is no \"typedescriptionlist.txt\" file at com.myster level. Myster needs this file. Myster will exit now.\n\nThe Type Description List should be inside the Myster program. If you can see this message it means the developer has forgotten to merge this file into the program. You should contact the developer and tell him of his error.");
				System.exit(0);
			}
			
			Vector vector = new Vector(10,10);
			
			int count;
			for (count=0; true; count++) {
				try {
					StringTokenizer tokens = new StringTokenizer(readLine(in),"\\",false);
					vector.addElement(new TypeDescription(new MysterType(tokens.nextToken().getBytes(com.myster.Myster.DEFAULT_ENCODING)),tokens.nextToken()));
				} catch (Exception ex) {
					break;
				}
			}
			
			TypeDescription[] list=new TypeDescription[count];
			for (int i=0; i<count; i++) {
				list[i]=(TypeDescription)vector.elementAt(i);
			}
			
			if (list.length == 0) return new TypeDescription[]{new TypeDescription(new MysterType((new String("MPG3")).getBytes()), "Default type since all types are disabled")};
			
			return list;
		} catch (Exception ex) { 
			ex.printStackTrace();
		return null;}
	}

		
	private static String readLine(InputStream in) throws Exception {
		char[] buffer=new char[2000];
		int i=0;int c=-1;
		for (i=0; i<2000-2; i++) {
			
			
			try {
				c=in.read();
			} catch (Exception ex) {
				c=-1;
			}
			
			if (c=='|'||c==-1) {
				//buffer[i]=' ';
				//buffer[i-1]=0;
				break;
			}
			buffer[i]=(char)c;
		}
		
		if (c==-1)  {
			throw new Exception(); //end of file
		}
		
		return new String(buffer,0,i);
	}
	
	private static class TypeDescriptionElement {
		private TypeDescription typeDescription;
		private boolean enabled;

		public TypeDescriptionElement(TypeDescription typeDescription, boolean enabled) {
			this.typeDescription = typeDescription;
			this.enabled = enabled;
		}
		
		public boolean setEnabled(boolean enabled) {
			this.enabled = enabled;
			return enabled;
		}
		
		public boolean getEnabled() {
			return enabled;
		}
		
		public boolean isEnabled() {
			return getEnabled(); 
		}
		
		public TypeDescription getTypeDescription() {
			return typeDescription;
		}
		
		public MysterType getType() {
			return typeDescription.getType();
		}
	}


}
	 
