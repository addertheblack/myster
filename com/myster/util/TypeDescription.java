/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.util;

import java.util.*;
import Myster;
import java.io.InputStream;
import com.myster.util.TypeDescription;

public class TypeDescription {
	String type;
	String description;

	public TypeDescription(String t, String d) { commonInit(t,d);}
	
	
	public TypeDescription(String s) {
		StringTokenizer st=new StringTokenizer(s, "\\");
		int numberoftokens=st.countTokens();

		if (numberoftokens==2) {
			commonInit(st.nextToken(), st.nextToken());
		} else {
			commonInit("ERR!", "An Error has occured loading the File Type List Resource.");
		}
	}
	
	private void commonInit(String t, String d) {
		type=t;description=d;
	}
	
	public String getType() {
		return type;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String toString() {
		return new String(type+" "+description);
	}

	public static synchronized TypeDescription[] loadTypeAndDescriptionList(Object w) {
		try {
			Object watcher=Myster.SPEEDPATH;
			InputStream in = Class.forName("Myster").getResourceAsStream("typedescriptionlist.txt");
			if (in==null) {
				System.out.println("There is no \"typedescriptionlist.txt\" file at root level. Myster needs this file. Myster will exit now.");
				System.out.println("Please get a Type Description list.");
				
				com.general.util.AnswerDialog.simpleAlert("PROGRAMER'S ERROR: There is no \"typedescriptionlist.txt\" file at root level. Myster needs this file. Myster will exit now.\n\nThe Type Description List should be inside the Myster program. If you can see this message it means the developer has forgotten to merge this file into the program. You should contact the developer and tell him of his error.");
				System.exit(0);
			}
			
			Stack stack=new Stack();
			
			int count;
			for (count=0; true; count++) {
				try {
					stack.push(new TypeDescription(readLine(in)));
				} catch (Exception ex) {
					break;
				}
			}
			
			TypeDescription[] list=new TypeDescription[count];
			for (int i=0; i<count; i++) {
				list[i]=(TypeDescription)stack.pop();
				//System.out.println(list[i].getType());
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

}