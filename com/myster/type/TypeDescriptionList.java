package com.myster.type;

import java.io.*;
import java.util.Vector;
import java.util.Hashtable;
import java.util.StringTokenizer;

import com.myster.pref.PreferencesMML;
import com.myster.mml.RobustMML;
import com.general.events.SyncEventDispatcher;


/**
* The TypeDescriptionList contains some basic type information for most file based
* types on the Myster network. The TypeDescriptionList is loaded from a file
* called "TypeDescriptionList.mml"
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
	
	/**
	*	Returns a TypeDescription for that MysterType or null if no TypeDescription Exists
	*/
	public abstract TypeDescription get(MysterType type) ;
	
	/**
	*	Returns all TypeDescriptionObjects known
	*/
	public abstract TypeDescription[] getAllTypes() ;
	
	/**
	*	Returns all enabled TypeDescriptionObjects known
	*/
	public abstract TypeDescription[] getEnabledTypes() ;
	
	/**
	*	Returns all TypeDescriptionObjects enabled
	*/
	public abstract boolean isTypeEnabled(MysterType type);
	
	/**
	*	Returns if the Type is enabled in the prefs (as opposed to
	*	if the type was enabled as of the beginning of the last execution
	*	which is what the "isTypeEnabled" functions does.
	*/
	public abstract boolean isTypeEnabledInPrefs(MysterType type);
	
	/**
	*	Adds a listener to be notified if there is a change in the enabled/
	*	unenabled-ness of of type.
	*/
	public abstract void addTypeListener(TypeListener l) ;
	
	/**
	*	Adds a listener to be notified if there is a change in the enabled/
	*	unenabled-ness of of type.
	*/
	public abstract void removeTypeListener(TypeListener l) ;
	
	/**
	*	Enabled / disabled a type. Note this value comes into effect next time Myster is launched.
	*/
	public abstract void setEnabledType(MysterType type, boolean enable) ;
}


class DefaultTypeDescriptionList extends TypeDescriptionList {

	//Ok, so here's the situation
	//I designed this so that I could change types while the program is running
	//and have all modules auto update without Myster restarting.. but it's too 
	//freaking long to program
	//so rather than do all that coding I've modified it to return the value that was
	//last saved (so that values from typeDescriptionList are CONSTANT PER PROGRAM EXECUTION
	//. The system will still fire events but the list won't send the
	//values stored in the prefs only the values that were true as of
	//the beginning of the last execution.
	//See code for how to get back the dynamic behavior... (comments actually)
	TypeDescriptionElement[] types;
	TypeDescriptionElement[] workingTypes;
	
	
	
	SyncEventDispatcher dispatcher;

	public DefaultTypeDescriptionList() {
		TypeDescriptionElement[] oldTypes;
	
		TypeDescription[] list = loadDefaultTypeAndDescriptionList();
		

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
		
		workingTypes = oldTypes; //set working types to "types" variable to enable on the fly cahnges
		
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
	
	public TypeDescription get(MysterType type) {
		for (int i = 0; i < types.length; i++) {
			if (types[i].getTypeDescription().getType().equals(type)) {
				return types[i].getTypeDescription();
			}
		}
		
		return null;
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





	private static synchronized TypeDescription[] loadDefaultTypeAndDescriptionList() {
		try {
			InputStream in = Class.forName("com.myster.Myster").getResourceAsStream("typedescriptionlist.mml");
			if (in==null) {
				System.out.println("There is no \"typedescriptionlist.mml\" file at com.myster level. Myster needs this file. Myster will exit now.");
				System.out.println("Please get a Type Description list.");
				
				com.general.util.AnswerDialog.simpleAlert("PROGRAMER'S ERROR: There is no \"typedescriptionlist.mml\" file at com.myster level. Myster needs this file. Myster will exit now.\n\nThe Type Description List should be inside the Myster program. If you can see this message it means the developer has forgotten to merge this file into the program. You should contact the developer and tell him of his error.");
				System.exit(0);
			}
			
			Vector vector = new Vector(10,10);
			
			RobustMML mml = new RobustMML(new String(readResource(in)));
			/*
				<List>
					<1>
						<Type>...</Type>
						<Description>...</Description>
						<Extentions>
							<1>.exe</1>
							<2>.zip</2>
						</Extensions>
						<Archived>false</Archived>
					</1>
			*/
			
			final String 
					LIST 			= "/List/";
			
			Vector typeList = mml.list(LIST);
			for (int i = 0 ; i < typeList.size(); i++) {
				TypeDescription typeDescription = getTypeDescriptionAtPath(mml, LIST + (String)(typeList.elementAt(i))+"/");
				
				if (typeDescription != null) vector.addElement(typeDescription);
			}
			
			TypeDescription[] typeDescriptions = new TypeDescription[vector.size()];
			for (int i = 0; i < typeDescriptions.length; i++) {
				typeDescriptions[i] = (TypeDescription) vector.elementAt(i);
			}
			
			System.out.println("Type descriptions length "+typeDescriptions.length);
			
			return typeDescriptions;
		} catch (Exception ex) { 
			ex.printStackTrace();
			throw new RuntimeException(""+ex);
		}

	}


	private static TypeDescription getTypeDescriptionAtPath(RobustMML mml, String path) {
		final String 					
				TYPE 			= "Type",
				DESCRIPTION		= "Description",
				EXTENSIONS		= "Extensions/",
				ARCHIVED		= "Archived";
		
		String type 				= mml.get(path + TYPE);
		String description			= mml.get(path + DESCRIPTION);
		Vector extensionsDirList	= mml.list(path + EXTENSIONS);
		String archived				= mml.get(path + ARCHIVED);
		
		if ((type == null) & (description == null)) return null;
		
		boolean isArchived = (archived == null ? false : (archived.equalsIgnoreCase("True")));
		
		
		String[] extensions = new String[0];
		if (extensionsDirList != null) {
			Vector recovered = new Vector(10,10);
			String extensionsPath = path+EXTENSIONS;
			
			for (int i = 0; i < extensionsDirList.size(); i++) {
				String extension = mml.get(extensionsPath + (String)(extensionsDirList.elementAt(i)));
				if (extension!=null) {
					recovered.addElement(extension);
				}	
			}
			
			extensions = new String[recovered.size()];
			for (int i = 0; i < extensions.length; i++) {
				extensions[i] = (String)recovered.elementAt(i);
			}
		}
		
		try {
			return new TypeDescription(new MysterType(type.getBytes(com.myster.Myster.DEFAULT_ENCODING)), description, extensions, isArchived);
		} catch (Exception ex) {
			throw new com.general.util.UnexpectedException(ex);
		}
	}


	
	private static byte[] readResource(InputStream in) throws IOException {
		final int BUFFER_SIZE = 2000;
		int amountRead = 0;
		
		byte[] buffer = new byte[BUFFER_SIZE];
	
		ByteArrayOutputStream out = new ByteArrayOutputStream();
	

		for (;;) {
			int bytesRead = in.read(buffer);
			
			if (bytesRead == -1) break;
			
			out.write(buffer, 0, bytesRead);
		}
		
		return out.toByteArray();
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
	 
