/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.client.ui;


import java.awt.*;
import java.awt.event.*;
import com.general.util.*;
import com.myster.menubar.MysterMenuBar;
import com.myster.util.Sayable;
import com.myster.ui.MysterFrame;
import com.myster.type.MysterType;

public class ClientWindow extends MysterFrame implements Sayable{
	GridBagLayout gblayout;
	GridBagConstraints gbconstrains;
	Button connect;
	TextField IP;
	List filetypelist;
	FileList filelist;
	FileInfoPane pane;
	String currentip;
	MysterMenuBar menubar;
	Button instant;
	
	MessageField msg;
	
	static final int XDEFAULT=600;
	static final int YDEFAULT=400;
	
	
	static final int SBXDEFAULT=72;	//send button X default
	
	
	static final int GYDEFAULT=50;		//Generic Y default
	
	private static int counter=0;
	
	private static final String WINDOW_KEEPER_KEY="Myster's Client Windows"; 
	private static com.myster.ui.WindowLocationKeeper windowKeeper=new com.myster.ui.WindowLocationKeeper(WINDOW_KEEPER_KEY);
	
	public static void initWindowLocations() {
		Rectangle[] rectangles=com.myster.ui.WindowLocationKeeper.getLastLocs(WINDOW_KEEPER_KEY);
		
		for (int i=0; i<rectangles.length; i++) {
			ClientWindow window=new ClientWindow();
			window.setBounds(rectangles[i]);
		}
	}
	
	public ClientWindow() {
		super("Direct Connection "+(++counter));
		
		makeClientWindow();
		
		windowKeeper.addFrame(this);
	}
	
	public ClientWindow(String ip) {
		super("Direct Connection "+(++counter));
		makeClientWindow();
		IP.setText(ip);
		//connect.dispatchEvent(new KeyEvent(connect, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, (char)KeyEvent.VK_ENTER)); 
		//connect.dispatchEvent(new KeyEvent(connect, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, (char)KeyEvent.VK_ENTER)); 
		connect.dispatchEvent(new ActionEvent(connect, ActionEvent.ACTION_PERFORMED,"Connect Button"));
	}
	
	private void makeClientWindow() {
		setBackground(new Color(240,240,240));
		
		
		//Do interface setup:
		gblayout=new GridBagLayout();
		setLayout(gblayout);
		gbconstrains=new GridBagConstraints();
		gbconstrains.fill=GridBagConstraints.BOTH;
		gbconstrains.insets=new Insets(1,1,1,1);
		gbconstrains.ipadx=1;
		gbconstrains.ipady=1;
		
		pane=new FileInfoPane();
		pane.setSize(XDEFAULT/3, YDEFAULT-40);
		
		
		connect=	new Button("Connect");
		connect.setSize(SBXDEFAULT, GYDEFAULT);
		
		IP=new TextField("Enter an IP here");
		IP.setEditable(true);
		
		
		filetypelist = new FileList();
		filetypelist.setSize(50, 40);
 		filelist = new FileList();

		
		msg=new MessageField("Idle...");
		msg.setEditable(false);
		msg.setSize(XDEFAULT,GYDEFAULT);
		
		instant = new Button("Instant Message");
		instant.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					com.myster.net.MysterAddress address =  new com.myster.net.MysterAddress(IP.getText());
					com.myster.message.MessageWindow window=new com.myster.message.MessageWindow(address);
					window.setVisible(true);
				} catch (java.net.UnknownHostException ex) {
					(new AnswerDialog(ClientWindow.this,"The address "+IP.getText()+" does not apear to be a valid internet address.")).answer();
				}
			}
		});

		
		//reshape(0, 0, XDEFAULT, YDEFAULT);
		
		addComponent(connect,0,0,1,1,0,0);
		addComponent(IP,0,1,2,1,0,0);
		addComponent(instant,0,3,1,1,0,0);
		addComponent(filetypelist,1,0,1,1,1,99);
		addComponent(filelist,1,1,2,1,6,99);
		addComponent(pane,1,3,1,1,5,99);
		addComponent(msg,2,0,4,1,99,0);
		
		show(true);
		setResizable(true);
		setSize(XDEFAULT,YDEFAULT);
		
		//filelisting.addActionListener(???);
		ConnectButtonEvent connectButtonEvent=new ConnectButtonEvent(this);
		connect.addActionListener(connectButtonEvent);
		IP.addActionListener(connectButtonEvent);
		
		filetypelist.addItemListener(new FileTypeSelectListener(this));
		filelist.addActionListener(new FileListAction(this));
		filelist.addItemListener(new FileStatsAction(this));
		
		addWindowListener(new StandardWindowBehavior());
		
	}
	
	
	private void addComponent(Component c, int row, int column, int width, int height, int weightx, int weighty) {
		gbconstrains.gridx=column;
		gbconstrains.gridy=row;
		
		gbconstrains.gridwidth=width;
		gbconstrains.gridheight=height;
		
		gbconstrains.weightx=weightx;
		gbconstrains.weighty=weighty;
		
		gblayout.setConstraints(c, gbconstrains);
		
		add(c);
		
	}
	
	public void addItemToTypeList(String s) {
		filetypelist.add(s);
	}	
	
	public void addItemToFileList(String s) {
		filelist.add(s);
	}
	
	public void clearFileList() {
		filelist.clear();
		pane.clear();
	}
	
	public void clearFileStats() {
		pane.clear();
	}
	
	public void refreshIP() {
		currentip=IP.getText();
	}
	
	//To be in an interface??
	public String getCurrentIP() {
		return currentip;
	}
	
	public MysterType getCurrentType() {
		try {
			return new MysterType((filetypelist.getSelectedItem()).getBytes());
		} catch (Exception ex) {
			return new MysterType(new byte[]{(byte)'M',(byte)'P',(byte)'G',(byte)'3'});
		}
	}
	
	public String getCurrentFile() {
		return filelist.getSelectedItem();
	}

	public void clearAll() {
		filetypelist.clear();
		filelist.clear();
		pane.clear();
	}
	
	public void say(String s) {
		msg.say(s);
	}
	
	public MessageField getMessageField() {
		return msg;
	}
	//end ?
	
	public void showFileStats(KeyValue k) {
		pane.display(k);
	}
}