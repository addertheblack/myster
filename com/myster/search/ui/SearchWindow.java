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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import com.general.util.*;
import java.util.Vector;
import com.myster.menubar.MysterMenuBar;
import com.general.util.MessageField;
import com.myster.client.stream.DownloaderThread;
import com.myster.search.SearchResultListener;
import com.myster.search.SearchResult;
import com.myster.util.Sayable;
import com.myster.util.TypeChoice;
import com.myster.ui.MysterFrame;
import com.myster.ui.WindowLocationKeeper;

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
	
	private static final String PREF_LOCATION_KEY="Search Window";
	
	private static int counter=0;

	public SearchWindow() {
		super("Search Window "+(++counter));
		
		setBackground(new Color(240,240,240));
		
		//Do interface setup:
		gblayout=new GridBagLayout();
		setLayout(gblayout);
		gbconstrains=new GridBagConstraints();
		gbconstrains.fill=GridBagConstraints.BOTH;
		gbconstrains.insets=new Insets(2,2,2,2);
		gbconstrains.ipadx=1;
		gbconstrains.ipady=1;
		
		searchbutton=new Button("Search");
		searchbutton.setSize(100, 20);
		
		textentry=new TextField("");
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
		
		
		addComponent(textentry			,0,1,2,1,1,0);
		addComponent(searchbutton		,0,0,1,1,0,0);
		addComponent(label				,1,0,1,1,0,0);
		addComponent(choice				,1,1,2,1,1,0);
		addComponent(filelist.getPane()	,2,0,3,1,1,1);
		addComponent(msg				,3,0,3,1,1,0);
		
		
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
		
		filelist.setColumnName(0, "Search Results appear here");
		filelist.setColumnWidth(0,400);
		
		keeper.addFrame(this);
		
		setVisible(true); // !
		
		textentry.setSelectionStart(0);
		textentry.setSelectionEnd(textentry.getText().length());
		
		

	}
	
	static WindowLocationKeeper keeper;//cheat to save scrolling. put at top later.
	
	public static void initWindowLocations() {
		Rectangle[] rectangles=WindowLocationKeeper.getLastLocs(PREF_LOCATION_KEY);
		
		keeper=new WindowLocationKeeper(PREF_LOCATION_KEY);
		
		for (int i=0; i<rectangles.length; i++) {
			SearchWindow window=new SearchWindow();
			window.setBounds(rectangles[i]);
		}
		
		if (rectangles.length==0) {SearchWindow window=new SearchWindow();}
		
		
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
	
	
	
	//////PREFERENCES AND WINDOW POSITIONS
	/*
	private static Hashtable searchWindowTable=new HashTable();
	private static final String prefsKey=new String("Search Window Locations");
	
	/**
	*	Class that can keep track of windows and their locations and output/input to strings.

	private static class WindowLocationHelper {
		Hashtable hashtable=new Hashtable();
		
		private Rectangle[] getOldWindowLocations(String oldValues) {
			Vector vector=new Vector();
			
			Tokenizer rects=new Tockenizer(oldValues, "|");
			
			while (rects.hasMoreTokens()) {
				String currentToken=rects.nextToken();
				
				Tokenizer positions=new Tokenizer(currentToken, ",");
				
				try {
					int x		=	Integer.parseInt(positions.nextToken());
					int y		=	Integer.parseInt(positions.nextToken());
					int width	=	Integer.parseInt(positions.nextToken());
					int heigth	=	Integer.parseInt(positions.nextToken());
					
					vector.addElement(new Rectangle(x,y,width,heigth));
				} catch (NumberFormatException ex) {
					ex.printStackTrace();
				} catch (NoMoreTokensException ex) {
					ex.printStackTrace();
				}
			}
			
			Rectangle[] rect=new Rectangle[vector.size()];
			for (int i=0; i<vector.size(); i++) {
				rect[i]=(Rectangle)(vector.elementAt(i));
			}
			
			return rect;
		} 
		
		private static void setRect(Rectangle rect, Frame window) {
			hashtable.put(window, rect);
		}
		
		private static String getStringToSave() {
			Enumeration hashEnum=hashtable.elements();
			
			StringBuffer buffer=new StringBuffer(); //fix
			while (hashEnum.hasMoreLements()) {
				Rectangle rect=(Rectangle)(hash.getElement());
				
				buffer.append(rect.getX());
				buffer.append(",");
				buffer.append(rect.getY());
				buffer.append(",");
				buffer.append(rect.getWidth());
				buffer.append(",");
				buffer.append(rect.getHeight());
				
				buffer.append("|");
			}
			
			return new String(buffer);
		}
	}
	*/
}