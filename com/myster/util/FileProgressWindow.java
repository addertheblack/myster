package com.myster.util;

import java.awt.event.*;
import com.general.util.Util;
import com.general.util.Timer;


public class FileProgressWindow extends ProgressWindow {
	public static final int BAR_1 			= 0;
	public static final int BAR_2 			= 1;

	RateTimer rateTimer;
	
	long bar1StartTime;
	long bar2StartTime;
	
	long previouslyDownloaded1;
	long previouslyDownloaded2;

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
}