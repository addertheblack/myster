/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.search.ui;

import com.general.mclist.*;

import java.awt.*;
import java.io.*;
import java.net.*;
import com.general.util.*;
import com.myster.menubar.MysterMenuBar;
import java.util.Vector;
import com.general.util.MessageField;
import com.myster.client.stream.DownloaderThread;
import com.myster.search.SearchResultListener;
import com.myster.search.SearchResult;
import com.myster.util.Sayable;
import com.myster.util.TypeChoice;
import com.myster.ui.MysterFrame;

public class SearchWindow extends MysterFrame implements SearchResultListener, Sayable {
	GridBagLayout gblayout;
	GridBagConstraints gbconstrains;
	
	Button searchbutton;
	MCList filelist;
	TextField textentry;
	Label label;
	TypeChoice choice;
	MessageField msg;
	//SearchResultBucket bucket;
	MysterMenuBar menubar;
	
	TextSpinner spinner=new TextSpinner();
	
	ClientHandleObject metaDateHandler;
	
	
	private final int BUCKETSIZE=100;
	private final int XDEFAULT=600;
	private final int YDEFAULT=400;
	
	private static int counter=0;

	public SearchWindow() {
		super("Search Window "+(++counter));
		
		setBackground(new Color(240,240,240));
		
		//Do interface setup:
		gblayout=new GridBagLayout();
		setLayout(gblayout);
		gbconstrains=new GridBagConstraints();
		gbconstrains.fill=GridBagConstraints.BOTH;
		gbconstrains.ipadx=1;
		gbconstrains.ipady=1;
		
		searchbutton=new Button("Search");
		searchbutton.setSize(50, 20);
		
		textentry=new TextField("Enter a search string here");
		textentry.setEditable(true);
		
		//connect.dispatchEvent(new KeyEvent(connect, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, (char)KeyEvent.VK_ENTER));
		
		label=new Label("Search Type:");
		
		choice=new TypeChoice();
		
 		filelist = new MCList(1, true, this);
 		filelist.getPane().setSize(XDEFAULT, YDEFAULT);

		msg=new MessageField("Idle...");
		msg.setEditable(false);
		msg.setSize(100,20);

		
		//reshape(0, 0, XDEFAULT, YDEFAULT);
		
		
		addComponent(textentry			,0,1,1,1,99,0);
		addComponent(searchbutton		,0,0,1,1,0,0);
		addComponent(label				,1,0,1,1,0,0);
		addComponent(choice				,1,1,2,1,99,0);
		addComponent(filelist.getPane()	,2,0,2,1,99,99);
		addComponent(msg			,3,0,2,1,99,0);
		
		
		setResizable(true);
		setSize(XDEFAULT,YDEFAULT);
		
		//setIconImage(Util.loadImage("img.jpg", this));
		
		SearchButtonEvent searchEventObject=new SearchButtonEvent(this,searchbutton);
		searchbutton.addActionListener(searchEventObject);
		/*searchbutton.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent event) { 
					System.out.println("You clicked the button");
				}
			});*/
		textentry.addActionListener(searchEventObject); //not only for buttons anymore.
		
		
		filelist.addMCListEventListener(new MCListEventAdapter(){
			public synchronized void doubleClick(MCListEvent a) {
				MCList list=a.getParent();
				downloadFile(list.getItem(list.getSelectedIndex()));
			}
		});	
			
		addWindowListener(new StandardWindowBehavior());
		
		//bucket=new SearchResultBucket(BUCKETSIZE);
		menubar=new MysterMenuBar(this);
		
		filelist.setColumnName(0, "Search Results appear here");
		filelist.setColumnWidth(0,400);
		
		setVisible(true);
		
		textentry.setSelectionStart(0);
		textentry.setSelectionEnd(textentry.getText().length());
	}

	
	public void addComponent(Component c, int row, int column, int width, int height, int weightx, int weighty) {
		gbconstrains.gridx=column;
		gbconstrains.gridy=row;
		
		gbconstrains.gridwidth=width;
		gbconstrains.gridheight=height;
		
		gbconstrains.weightx=weightx;
		gbconstrains.weighty=weighty;
		
		gblayout.setConstraints(c, gbconstrains);
		
		super.add((java.awt.Component)c);
		
	}
	
	
	public void startSearch() {
		msg.say("Clearing File List...");
		filelist.clearAll();
		recolumnize();
		//bucket=new SearchResultBucket(BUCKETSIZE);
	}
	
	public void searchOver() {
		msg.say("Search done. "+filelist.getSize()+"...");
	}
	
	public void recolumnize() {
		metaDateHandler=ClientInfoFactoryUtilities.getHandler(getType());
		int max=metaDateHandler.getNumberOfColumns();
		filelist.setNumberOfColumns(max);
		
		for (int i=0; i<max; i++) {
			filelist.setColumnName(i, metaDateHandler.getHeader(i));
			filelist.setColumnWidth(i, metaDateHandler.getHeaderSize(i));
		}
	}
	
	
	public boolean addSearchResults(SearchResult[] resultArray) {
		MCListItemInterface[] m=new MCListItemInterface[resultArray.length];
	
		for (int i=0; i<resultArray.length; i++) {
			m[i]=metaDateHandler.getMCListItem(resultArray[i]);
		}
		
		filelist.addItem(m);
		return true;
	}
	
	public void searchStats(SearchResult s) {
		//? dunno.
	}


	
	public String getSearchString() {
		return textentry.getText();
	}
	
	public String getType() {
		return choice.getType();	
	}
	
	public void downloadFile(Object s) {
		((SearchResult)(s)).download();
	}
	
	public void paint(Graphics g) {
		filelist.repaint();	//neede dbecause when an item is updated this object's repaint() methoods is called. The repaint() needs to be apssed on to the list.
	}
	
	/*
	public void openAClientWindow(String s) {
		ClientWindow w=new ClientWindow(bucket.getValue(s).getIP());
	} */
	
	public void say(String s) {
		//System.out.println(s);
		msg.say(""+s);
	}
	
}