package com.myster.ui;

import java.awt.Rectangle;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ComponentListener;
import java.awt.event.ComponentEvent;
import java.awt.Toolkit;
import java.util.StringTokenizer;
import java.util.Hashtable;
import java.util.Vector;
import java.util.NoSuchElementException;
import com.myster.pref.*;

//remove below later!
import com.myster.mml.MML;

public class WindowLocationKeeper {
	private static final String PREF_KEY="Window Locations and Sizes/";
	
	private static PreferencesMML prefs=new PreferencesMML();
	private static PreferencesMML oldPrefs;

	private static boolean initFlag=false;

	public static synchronized void init() {
		if (initFlag) return; //dont init twice.
		
		initFlag=true;
		oldPrefs=new PreferencesMML(Preferences.getInstance().getAsMML(PREF_KEY, new PreferencesMML()).copyMML());
		System.out.println(""+Preferences.getInstance().getAsMML(PREF_KEY));
	}
	
	public static boolean fitsOnScreen(Rectangle rect) {
		Rectangle screenBorders=new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		return (rect.x+50<screenBorders.width && rect.y+50<screenBorders.height);
	}

	public static synchronized Rectangle[] getLastLocs(String key) {
		init();
		//System.out.println(oldPrefs.toString());
		key="/"+key+"/";
		
		Vector keyList=oldPrefs.list(key);
		
		if (keyList==null) return new Rectangle[]{}; //aka Rectangle[0];
		
		Rectangle[] rectangles=new Rectangle[keyList.size()];
		
		for (int i=0; i<keyList.size(); i++) {
			rectangles[i]=getRectangleFromString(oldPrefs.get(key+(String)(keyList.elementAt(i)), "0,0,400,400"));
			if (!fitsOnScreen(rectangles[i])) rectangles[i].setLocation(50,50);
			//System.out.println(key+(String)(keyList.elementAt(i)));
		}
		
		
		
		return rectangles;
	}
	
	private static synchronized Rectangle getRectangleFromString(String s) {
		int x,y,width,height;
		
		StringTokenizer tokenizer=new StringTokenizer(s,SEPERATOR, false);
		
		try {
			x		= Integer.parseInt(tokenizer.nextToken());
			y		= Integer.parseInt(tokenizer.nextToken());
			width	= Integer.parseInt(tokenizer.nextToken());
			height	= Integer.parseInt(tokenizer.nextToken());
		} catch (NoSuchElementException ex) {
			return new Rectangle(0,0,100,100);
		} catch (NumberFormatException ex) {
			return new Rectangle(0,0,100,100);
		}
		
		return new Rectangle(x,y,width,height);
	}

	
	private String key;
	private volatile int counter=0;

	public WindowLocationKeeper(String key) {
		init();
		if (key.indexOf("/")!=-1) throw new RuntimeException("Key cannot contain a \"/\"!");
		this.key="/"+key+"/";
	}
	
	Hashtable listenerHash=new Hashtable();
	
	public void addFrame(Frame frame) {
		final int privateID=counter++;
	
		ComponentListener cListener=new ComponentListener() {
			public void componentResized(ComponentEvent e) {
				saveLocation(((Component)(e.getSource())), privateID);
			}

			public void componentMoved(ComponentEvent e) {
				saveLocation(((Component)(e.getSource())), privateID);
			}

			public void componentShown(ComponentEvent e) {
				saveLocation(((Component)(e.getSource())), privateID);
			}

			public void componentHidden(ComponentEvent e) {
				System.out.println("AGGGHH");
				System.out.println("hello ->"+prefs.remove(key+privateID));
				Preferences.getInstance().put(PREF_KEY,prefs);
			}
		};
		
		listenerHash.put(frame, cListener);
		
		if (frame.isVisible()) {
			saveLocation(frame, privateID);
		}
		
		frame.addComponentListener(cListener);
	}
	
	public void removeFrame(Frame frame) {
		ComponentListener cListener=(ComponentListener)(listenerHash.remove(frame));
		
		if (cListener==null) return;
		
		//frame.removeComponentListener(cListener);
	}
	
	private void saveLocation(Component c, int id) {
		prefs.put(key+id, rect2String(c.getBounds()));
		//MML mml=new MML();
		//try {mml.put(key+id+"/", rect2String(c.getBounds()));} catch (Exception ex) {System.out.println(""+ex);}
		Preferences.getInstance().put(PREF_KEY,prefs);
		//System.out.println(""+key+id+"/ ->> "+rect2String(c.getBounds()));
		//System.out.println(""+prefs);
	}
	
	private static final String SEPERATOR=",";
	
	private String rect2String(Rectangle rect) { //here for code reuse
		return ""+rect.x+SEPERATOR+rect.y+SEPERATOR+rect.width+SEPERATOR+rect.height;
	}
}