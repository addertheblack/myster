package com.myster.util;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

public class I18n {
	private static ResourceBundle resources;
	
	public static void init() {
		//Locale.setDefault(new Locale(Locale.JAPANESE.getLanguage(),Locale.JAPAN.getCountry()));

		resources = ResourceBundle.getBundle("com.properties.Myster");
	}

	public static String tr(String stringToTranslate) {
		return tr(stringToTranslate, new Object[]{});
	}
	
	public static String tr(String stringToTranslate, Object o) {
		return tr(stringToTranslate, new Object[]{o});
	}
	
	public static String tr(String stringToTranslate, Object o, Object o1) {
		return tr(stringToTranslate, new Object[]{o,o1});
	}
	
	public static String tr(String stringToTranslate, Object[] objectsToAdd) {
		if (Locale.getDefault().getDisplayLanguage().equals(Locale.ENGLISH.getDisplayLanguage()) || (resources == null)) return findAndReplace(stringToTranslate, objectsToAdd);
		
		try {
			return findAndReplace(resources.getString(stringToTranslate), objectsToAdd);
		} catch (MissingResourceException ex) {
			System.err.println("missing translation key: \"" + stringToTranslate + "\"");
			
			return findAndReplace(stringToTranslate, objectsToAdd);
		}
	}
	
	private static char[] substitutionArray = 
			{'1','2','3','4','5','6','7','8','9',
			'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 
			'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 
			'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};
			
	private static final char SUB_CHAR = '%';
	
	private static String findAndReplace(String stringToReplace, Object[] objectsToAdd) {
		StringBuffer sourceBuffer = new StringBuffer(stringToReplace);
		StringBuffer destinationBuffer = new StringBuffer(sourceBuffer.length()*2); // allocate som extra space
		
		for (int i = 0; i < sourceBuffer.length()-1; i++ ) { //the -1 is because we always assume there is a 
			if (sourceBuffer.charAt(i) == SUB_CHAR) {
				i++;
				
				int index = getIndex(sourceBuffer.charAt(i));
				
				if ((index == -2) || (index >= objectsToAdd.length)) {
					destinationBuffer.append("<Error, single "  + SUB_CHAR + " with no matching argument.>");
				} else if (index == -1) {
					destinationBuffer.append(SUB_CHAR);
				} else {
					destinationBuffer.append(objectsToAdd[index].toString());
				}
			} else {
				destinationBuffer.append(sourceBuffer.charAt(i));
			}
		}
		
		return new String(destinationBuffer);
	}
	
	private static int getIndex(char c) {
		if (c == SUB_CHAR) return -1;
	
		for (int i = 0; i < substitutionArray.length; i++) {
			if (substitutionArray[i] == c) {
				return i;
			}
		}
		
		return -2;
	}
}