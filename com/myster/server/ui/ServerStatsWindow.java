/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.myster.server.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import com.general.tab.*;

import com.myster.util.Sayable;
import com.myster.ui.MysterFrame;


public class ServerStatsWindow extends MysterFrame implements Sayable {
	TabPanel tab;
	
	DownloadInfoPanel downloadPanel;
	StatsInfoPanel statsinfopanel;
	//GraphInfoPanel graphinfopanel;

	
	public final static int XSIZE=600;
	public static final int YSIZE=400;

	public final static int TABYSIZE=50;

	private static ServerStatsWindow singleton;//=new ServerStatsWindow();

	private static com.myster.ui.WindowLocationKeeper keeper;//=new com.myster.ui.WindowLocationKeeper("Server Stats");

	public synchronized static ServerStatsWindow getInstance() {
		if (singleton==null) {
			singleton=new ServerStatsWindow();
		}
		return singleton;
	}
	
	public static void initWindowLocations() {
		Rectangle[] rect=com.myster.ui.WindowLocationKeeper.getLastLocs("Server Stats");
		if (rect.length>0) {
			Dimension d=singleton.getSize();
			singleton.setBounds(rect[0]);
			singleton.setSize(d);
			singleton.setVisible(true);
		}
	}
	
	public void say(String s) {
		//System.out.println(s);
	}

	protected ServerStatsWindow() {
		super("Server Statistics");
		
		keeper=new com.myster.ui.WindowLocationKeeper("Server Stats");
		keeper.addFrame(this); //never remove.
		
		setResizable(false);
		
		
		
		//load objects:
		setLayout(null);
		
		tab=new TabPanel();

		downloadPanel=new DownloadInfoPanel();

		
		statsinfopanel=new StatsInfoPanel();
		
		//init();if (true==true) return;
		addComponentListener(new ComponentAdapter() {
			public void componentShown(ComponentEvent e) {
				if (!inited) {
					initSelf();
				}
				setSize(XSIZE+getInsets().right+getInsets().left, YSIZE+getInsets().top+getInsets().bottom);
			}
		
		});
	}
	boolean inited=false;
	
	//private boolean setupFlag=false;
	//public void show() {
	//	if (!setupFlag) {
	//		super.show();
	//		init();
	//		setupFlag=true;
	//	} else {
	//		super.show();
	//	}
	//}
	
	private void initSelf() {
		if (inited) return; //this should NEVER happen
		inited=true;
		
		//pack();
		setSize(XSIZE+50, YSIZE+50);
		setLayout(null);
		setSize(XSIZE+getInsets().right+getInsets().left, YSIZE+getInsets().top+getInsets().bottom);
		
		//tab=new TabPanel();
		tab.setLocation(getInsets().right,getInsets().top);
		tab.setSize(XSIZE,TABYSIZE);
		
		/*
		tab.setOverlap(10);
		tab.addTab("Tab 1");
		tab.addTab("Tab 2");
		tab.addTab("Tab 4");
		tab.addTab("Monkey bonus tab");
		*/
		
		///*
		tab.addCustomTab("outbound.jpg");
		tab.addCustomTab("serverstats.jpg");
		
		//tab.addTab("Outbound");
		//tab.addTab("Server Stats");
		//tab.addCustomTab("graphs.gif");
		//*/
		
		//if you want generic tabs use the thing below.
		//tab.addTab(new ServerTab(tab, null));
		//tab.addTab(new ServerTab(tab, null));
		//tab.addTab(new ServerTab(tab, null));
		
		add(tab);
		
		//downloadPanel=new DownloadInfoPanel();
		downloadPanel.setLocation(0+getInsets().right, TABYSIZE+getInsets().top);
		tab.addTabListener(new TabHandler(0, downloadPanel));
		downloadPanel.setSize(XSIZE, YSIZE-TABYSIZE);
		add(downloadPanel);
		
		downloadPanel.inited();
		
		//statsinfopanel=new StatsInfoPanel();
		statsinfopanel.setLocation(0+getInsets().right, TABYSIZE+getInsets().top);
		statsinfopanel.setSize(XSIZE, YSIZE-TABYSIZE);
		add(statsinfopanel);
		statsinfopanel.setVisible(false);
		//statsinfopanel.init();
		tab.addTabListener(new TabHandler(1, statsinfopanel));
		
		/*
		graphinfopanel=new GraphInfoPanel();
		graphinfopanel.setLocation(0+getInsets().right, TABYSIZE+getInsets().top);
		graphinfopanel.setSize(XSIZE, YSIZE-TABYSIZE);
		add(graphinfopanel);
		graphinfopanel.init();
		graphinfopanel.setVisible(false);
		tab.addTabListener(new TabHandler(2, graphinfopanel));
		*/
		
		
		addWindowListener(new CloseBox());
		//hide();
	}
	
	
	private class CloseBox extends WindowAdapter  {
	
		public void windowClosing(WindowEvent e) {
			hide();
		}
	}
	
	private static class TabHandler implements TabListener {
		int tabNumber;
		Panel panel;
	
		public TabHandler(int i, Panel p) {
			tabNumber=i;
			panel=p;
		}
	
		public void tabAction(TabEvent e) {
			int id=e.getTabID();
			if (id==tabNumber) panel.setVisible(true);
			else panel.setVisible(false);
		}
	}


}