package com.myster.type;

import java.io.*;
import java.util.Vector;
import java.util.StringTokenizer;

import com.general.events.SyncEventDispatcher;
import Myster;

//IMUTABLE

/**
* 
*/
public abstract class TypeDescriptionList {
	private static TypeDescriptionList defaultList;
	
	public static synchronized void init() {
		if (defaultList == null) defaultList = new DefaultTypeDescriptionList();
	}
	
	public static synchronized TypeDescriptionList getDefaultList() {
		init(); //possible pre-mature init here
		return defaultList;
	}
	
	public abstract TypeDescription[] getAllTypes() ;
	public abstract TypeDescription[] getEnabledTypes() ;
	public abstract boolean isTypeEnabled(MysterType type);
	public abstract void addTypeListener(TypeListener l) ;
	public abstract void removeTypeListener(TypeListener l) ;
	public abstract void enableType(MysterType type) ;
	public abstract void disableType(MysterType type) ;
}




class DefaultTypeDescriptionList extends TypeDescriptionList {
	TypeDescriptionElement[] types;
	SyncEventDispatcher dispatcher; 

	public DefaultTypeDescriptionList() {
		TypeDescription[] list = loadDefaultTypeAndDescriptionList();
		
		types = new TypeDescriptionElement[list.length];
		
		for (int i = 0; i < list.length; i++) {
			types[i] = new TypeDescriptionElement(list[i], true);
		}
		
		dispatcher = new SyncEventDispatcher();
	}


	//TypeDescription Methods
	public TypeDescription[] getAllTypes() {
		TypeDescription[] typeArray_temp = new TypeDescription[types.length];
		
		for (int i = 0; i < types.length; i++) {
			typeArray_temp[i] = types[i].getTypeDescription();
		}
		
		return typeArray_temp;
	}
	
	public TypeDescription[] getEnabledTypes() {
		int counter = 0;
		for (int i = 0; i < types.length; i++) {
			if (types[i].getEnabled()) counter++;
		}
		
		TypeDescription[] typeArray_temp = new TypeDescription[counter];
		for (int i = 0; i < counter; i++) {
			if (types[i].getEnabled()) typeArray_temp[i] = types[i].getTypeDescription();
		}
		
		return typeArray_temp;
	}
	
	public boolean isTypeEnabled(MysterType type) {
		int index = getIndexFromType(type);
		
		return (index != -1 ? types[index].getEnabled() : false);
	}
	
	public void addTypeListener(TypeListener listener) {
		dispatcher.addListener(listener);
	}
	
	public void removeTypeListener(TypeListener listener) {
		dispatcher.removeListener(listener);
	}
	
	public void enableType(MysterType type) {
		int index = getIndexFromType(type);
		
		//errs
		if (index == -1) return; //no such type
		if (types[index].getEnabled()) return; //it's already enabled you dope.
		
		types[index].setEnabled(true);
		
		dispatcher.fireEvent(new TypeDescriptionEvent(TypeDescriptionEvent.ENABLE , this, type));
	}
	
	public void disableType(MysterType type) {
		int index = getIndexFromType(type);
		
		//errs
		if (index == -1) return; //no such type
		if (types[index].getEnabled() ==  false) //it's already disabled you dope.
		
		types[index].setEnabled(false);
		
		dispatcher.fireEvent(new TypeDescriptionEvent(TypeDescriptionEvent.DISABLE, this, type));
	}

	private int getIndexFromType(MysterType type) {
		for (int i = 0; i < types.length; i++) {
			if (types[i].getType().equals(type)) return i;
		}
		
		return -1;
	}



	//statics
	private static synchronized TypeDescription[] loadDefaultTypeAndDescriptionList() {
		try {
			InputStream in = Class.forName("Myster").getResourceAsStream("typedescriptionlist.txt");
			if (in==null) {
				System.out.println("There is no \"typedescriptionlist.txt\" file at root level. Myster needs this file. Myster will exit now.");
				System.out.println("Please get a Type Description list.");
				
				com.general.util.AnswerDialog.simpleAlert("PROGRAMER'S ERROR: There is no \"typedescriptionlist.txt\" file at root level. Myster needs this file. Myster will exit now.\n\nThe Type Description List should be inside the Myster program. If you can see this message it means the developer has forgotten to merge this file into the program. You should contact the developer and tell him of his error.");
				System.exit(0);
			}
			
			Vector vector = new Vector(10,10);
			
			int count;
			for (count=0; true; count++) {
				try {
					StringTokenizer tokens = new StringTokenizer(readLine(in),"\\",false);
					vector.addElement(new TypeDescription(new MysterType(tokens.nextToken().getBytes(Myster.DEFAULT_ENCODING)),tokens.nextToken()));
				} catch (Exception ex) {
					break;
				}
			}
			
			TypeDescription[] list=new TypeDescription[count];
			for (int i=0; i<count; i++) {
				list[i]=(TypeDescription)vector.elementAt(i);
			}
			return list;
		} catch (Exception ex) { return null;}
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
	 