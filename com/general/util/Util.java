package com.general.util;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
//import Myster;

public class Util { //This code was taken from an Apple Sample Code package,

	public static Image loadImage(String filename, Component watcher)
	{
		Image image = null;
		
		if (filename != null)
		{
			URL url = watcher.getClass().getResource(filename);
			if (url == null)
			{
				System.err.println("loadImage() could not find \"" + filename + "\"");
			}
			else
			{
				image = watcher.getToolkit().getImage(url);
			    if (image == null)
			    {
					System.err.println("loadImage() getImage() failed for \"" + filename + "\"");
			    }
			    else
			    {
					MediaTracker tracker = new MediaTracker(watcher);
		
			        try
			        {
			            tracker.addImage(image, 0);
			            tracker.waitForID(0);
			        }
			        catch (InterruptedException e) { System.err.println("loadImage(): " + e); }
			        finally
			        {
				    	boolean isError = tracker.isErrorAny();
				    	if (isError)
				    	{
					    	System.err.println("loadImage() failed to load \"" + filename + "\"");
					    	int flags = tracker.statusAll(true);
		
					    	boolean loading = 0 != (flags & MediaTracker.LOADING);
					    	boolean aborted = 0 != (flags & MediaTracker.ABORTED);
					    	boolean errored = 0 != (flags & MediaTracker.ERRORED);
					    	boolean complete = 0 != (flags & MediaTracker.COMPLETE);
					    	System.err.println("loading: " + loading);
					    	System.err.println("aborted: " + aborted);
					    	System.err.println("errored: " + errored);
					    	System.err.println("complete: " + complete);
					    }
			        }
			    }
			}
		}
	
		return image;
	}
	

	
	public static String getStringFromBytes(long bytes) {
		if (bytes<1024) {
			return bytes+"bytes";
		}
		
		long kilo=bytes/1024;
		if (kilo<1024) {
			return kilo+"K";
		}
		
		double megs = (double)kilo/1024;
		if (megs<1024) {
			String temp = "" + megs;
			return temp.substring(0, temp.indexOf(".") + 2) + "MB";
		}
		
		double gigs = megs/1024;
		if (gigs<1024) {
			String temp = ""+gigs;
			return temp.substring(0, temp.indexOf(".")  + 2) + "GB";
		}
		
		double tera = gigs/1024;
		String temp = "" + tera;
		return temp.substring(0, temp.indexOf(".") + 2) + "TB";
	}
	
	public static byte[] concatenateBytes(byte[] array, byte[] array2) {
		byte[] buffer=new byte[array.length+array2.length];
		
		System.arraycopy(array,0,buffer,0,array.length);
		System.arraycopy(array2,0,buffer,array.length, array2.length);
		
		return buffer;
	}

	public static byte[] fromHexString(String hash) throws NumberFormatException {
		if ((hash.length() % 2) != 0) throw new NumberFormatException("Even number of byte pairs");
		
		byte[] bytes = new byte[hash.length()/2];
		
		for (int i = 0; i < hash.length(); i+=2) {
				bytes[i/2] = (byte)(Short.parseShort(hash.substring(i,i+2), 16)); //hopefully the compiler will convert "/2" into >> 1
		}
		
		return bytes;
	}
	
	/**
	*	Centers the frame passed on the screen. The offsets are to offset the frame from perfect center.
	*	If you want it centered call this with offsets of 0,0
	*/
	public static void centerFrame(Frame frame, int xOffset, int yOffset) {
		Toolkit tool=Toolkit.getDefaultToolkit();
		frame.setLocation(tool.getScreenSize().width/2 - frame.getSize().width/2 + xOffset, tool.getScreenSize().height/2 - frame.getSize().height/2 + yOffset);
	}
}
