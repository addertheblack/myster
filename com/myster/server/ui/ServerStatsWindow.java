/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.myster.server.ui;

import com.general.tab.*;
import com.myster.util.Sayable;
import com.general.util.StandardWindowBehavior;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import com.myster.menubar.MysterMenuBar;


public class ServerStatsWindow extends Frame implements Sayable {
	TabPanel tab;
	
	DownloadInfoPanel downloadPanel;
	StatsInfoPanel statsinfopanel;
	GraphInfoPanel graphinfopanel;

	
	public final static int XSIZE=600;
	public static final int YSIZE=400;

	public final static int TABYSIZE=50;

	public static ServerStatsWindow singleton=new ServerStatsWindow();



	public synchronized static ServerStatsWindow getInstance() {
		if (singleton==null) {
			//singleton=new ServerStatsWindow();
		}
		return singleton;
	}
	
	public void say(String s) {
		//System.out.println(s);
	}

	protected ServerStatsWindow() {
		super("Server Statistics");
		
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
					init();
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
	
	private void init() {
		if (inited==true) System.exit(0);
		inited=true;
		new MysterMenuBar(this);
		
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
		tab.addCustomTab("outbound.gif");
		tab.addCustomTab("serverstats.gif");
		
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
	
	//private void fuckers() { //this is here because Java crashes If I pack() before showing the window.. so I need to repositon on show every fuckig time
	//
	//}
	
	//public void tabAction(TabEvent e) {
	//
	//}
	
	private class CloseBox extends WindowAdapter  {
	
		public void windowClosing(WindowEvent e) {
			hide();
		}
	}
	
	private class TabHandler implements TabListener {
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