package com.myster.util;

import java.awt.event.*;
import java.net.MalformedURLException;
import java.net.URL;

import com.myster.net.web.WebLinkManager;
import com.general.util.Timer;
import com.general.util.Util;


public class FileProgressWindow extends ProgressWindow {
	public static final int BAR_1 			= 0;
	public static final int BAR_2 			= 1;

	RateTimer rateTimer;
	
	long bar1StartTime;
	long bar2StartTime;
	
	long previouslyDownloaded1;
	long previouslyDownloaded2;
	
	boolean overFlag = false;
	
	String url;
	
	public FileProgressWindow() {
		this("");
	}
	
	public FileProgressWindow(String title) {
		super(title);
		
		addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent e) {
				rateTimer = new RateTimer();
			}
			
			public void componentHidden(ComponentEvent e) {
				if (rateTimer != null) rateTimer.end();
			}
		});
		
		addAdClickListener(new AdClickHandler());
	}
	
	public void setPreviouslyDownloaded(long someValue, int bar) {
		if (bar == BAR_1) {
			previouslyDownloaded1 = someValue;
		} else if (bar == BAR_2) {
			previouslyDownloaded2 = someValue;
		}
	}
	
	public void startBlock (int bar, long min, long max) {
		if (bar == BAR_1) bar1StartTime = System.currentTimeMillis();
		else if (bar == BAR_2) bar2StartTime = System.currentTimeMillis();
	
		super.startBlock(bar, min, max);
    }
    
    public void done() {
    	overFlag = true;
    }
    
	
	private String calculateRate(int bar) {
		if (getValue(bar) < getMin(bar) || getValue(bar) > getMax(bar)) return "";
		
		if (bar == BAR_1) {
			return formatRate(bar1StartTime, getValue(bar) - previouslyDownloaded1);
		} else if (bar == BAR_2) {
			return formatRate(bar2StartTime, getValue(bar) - previouslyDownloaded2);
		} else { 
			return "";
		}
	}
	
	private long rateCalc(long startTime, long value) {
		long int_temp = ((System.currentTimeMillis() - startTime)/1000); 
	
		return (int_temp <= 0 ? 0 : value / int_temp);
	}
	
	public void setURL(String urlString) {
		url = (urlString.equals("")?null:urlString);
	}
	
	private String formatRate(long startTime, long value) {
		return Util.getStringFromBytes(rateCalc(startTime, value)) + "/s";
	}
	
	private class RateTimer implements Runnable {
		public static final int UPDATE_TIME = 100;
		Timer timer;
		private boolean endFlag = false;
		
		public RateTimer() {
			newTimer();
		}
		
		public void run() {
			if (endFlag) return;
			if (overFlag) return;
			if (getValue() == getMax()) return;
			
			setAdditionalText(calculateRate(BAR_1), BAR_1);
			setAdditionalText(calculateRate(BAR_2), BAR_2);
			
			newTimer();
		}
		
		private void newTimer() {
			timer = new Timer(this, UPDATE_TIME);
		}
		
		public void end() {
			if (timer != null) timer.cancelTimer();
			endFlag = true;
		}
	}
	
	private class AdClickHandler extends MouseAdapter {
		public void mouseReleased(MouseEvent e) {		
			if ((e.getX() > 0 && e.getX() < X_SIZE) && (e.getY() >0 && e.getY() < AD_HEIGHT)) {
				try {
					if (url!=null) WebLinkManager.openURL(new URL(url));
				} catch (MalformedURLException ex) {
					ex.printStackTrace();
				}
			}
		}
	}
}