package com.general.util;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import Myster;

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
			return (""+bytes+"bytes");
		}
		
		long kilo=bytes/1024;
		if (kilo<1024) {
			return new String(""+kilo+"K");
			
		}
		
		double megs=(double)kilo/1024;
		if (megs<1024) {
			String temp=new String(""+megs);
			return new String(temp.substring(0,temp.indexOf(".")+2)+"MB");
			
		}
		
		double gigs=(double)megs/1024;
		if (gigs<1024) {
			String temp=new String(""+gigs);
			return new String(temp.substring(0,temp.indexOf(".")+2)+"GB");
		}
		
		double tera=(double)gigs/1024;
		String temp=new String(""+tera);
		return new String(temp.substring(0,temp.indexOf(".")+2)+"GB");
	}
	
}