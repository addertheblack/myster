package com.myster.util;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;

public class I18n {
	private static ResourceBundle resources;
	
	public static void init() {
		//Locale.setDefault(new Locale(Locale.JAPANESE.getLanguage(),Locale.JAPAN.getCountry()));
		
		try {
			resources = ResourceBundle.getBundle("com.properties.Myster");
		}
		catch (MissingResourceException e) {
			System.err.println("resources not found");
		}
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
		if (Locale.getDefault().getDisplayLanguage().equals(Locale.ENGLISH.getDisplayLanguage())) return stringToTranslate;
		
		try {
			return resources.getString(stringToTranslate);
		} catch (MissingResourceException ex) {
			System.err.println("missing translation key: \"" + stringToTranslate + "\"");
			//ex.printStackTrace();
			return stringToTranslate;
		}
	}
}