package com.myster.util;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

/**
*	This class does translation from ENGLISH KEYS to strings of any language.
*	It makes use of the Myster properties files included in the app.
*	<p>
*	These functions use the submitted string as a KEY only. They assume the
*	String itself has not meaning but they will return the KEY as the translated
*	String if a valid String for the current Locale cannot be found.
*	<p>
*	This class also does substitutions. Substitutions allow values to b inserted into strings.
*	<p>
*	For example the call : I18n.tr("I have %1 rubber ducks in my collection", ""+numberOfDucks);
*	will substitude the value of the string ""+numberOfDucks in the possition occupied by the character
*	%1.
*	<p>
*	The % trick will work with all numbers from 1 to 9 then from all letters form A - Z (uppercase).
*	The %n values correspond to which argument in the function + 1 of which offset in the array - 1
*	<p>
*	If you want to express a % symbole then type %% instead. It will be sumstitued by a single % symbole.
*	<p>
*	If an error occures, you should get a bit of extra text inserted in the place of the error instead
*	of the value.
*/

public class I18n {
	private static ResourceBundle resources;
	
	/**
	*	This function must be called before any other (ie, on startup)
	*/
	public static void init() {
		//Locale.setDefault(new Locale(Locale.JAPANESE.getLanguage(),Locale.JAPAN.getCountry()));

		//resources = ResourceBundle.getBundle("com.properties.Myster");
		//We don't want to do a release with the resource bundle on because a full transalation has not yet been done.
		//(A half saved cat is uglier than a cat without hair)
	}

	/**
	*	Find the translated key for this string
	*
	*	@param	key to the string to translate.
	*	@return	translated String.
	*/
	public static String tr(String stringToTranslate) {
		return tr(stringToTranslate, new Object[]{});
	}
	
	/**
	*	Find the translated key for this string
	*
	*	@param	key to the string to translate.
	*	@param	Objec.toString() to substitute
	*	@return	translated String.
	*/
	public static String tr(String stringToTranslate, Object o) {
		return tr(stringToTranslate, new Object[]{o});
	}
	
	/**
	*	Find the translated key for this string
	*
	*	@param	key to the string to translate.
	*	@return	translated String.
	*/
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
		
		int i;
		for (i = 0; i < sourceBuffer.length()-1; i++ ) { //the -1 is because we always assume there is a 
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
		
		if (i ==  sourceBuffer.length()-1) {
			destinationBuffer.append(sourceBuffer.charAt(sourceBuffer.length()-1));
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