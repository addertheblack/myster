/* 
	Main.java

	Title:			Server Stats Window Test App
	Author:			Andrew Trumper
	Description:	An app to test the server stats window
*/

package com.myster.server.ui;


import java.awt.*;
import com.general.tab.*;
import java.awt.image.*;
import com.general.mclist.*;
import com.myster.server.event.*;
import com.myster.server.ServerFacade;
import java.util.Vector;
import com.myster.server.stream.FileSenderThread;
import com.myster.util.MysterThread;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import com.myster.client.ui.ClientWindow;


public class DownloadInfoPanel extends Panel {
	MCList list;
	Button disconnect, browse, clearAll;
	ServerEventManager server;
	ConnectionHandler chandler;
	
	public DownloadInfoPanel() {
		setBackground(new Color(240,240,240));	
		server=ServerFacade.getServerDispatcher();
		//init();
		chandler=new ConnectionHandler();
		server.addConnectionManagerListener(chandler);
		//(new JavaIsStupid()).start();
	}
	
	public void inited() {
		setLayout(null);

		list=new MCList(6, true, this);

		ScrollPane p=list.getPane();
		
		p.setSize(590,240);
		p.setLocation(5,5);

		add(p);

		list.setNumberOfColumns(5);
		
		list.setColumnName(0, "User");
		list.setColumnName(1, "File");
		list.setColumnName(2, "Size");
		list.setColumnName(3, "Rate");
		list.setColumnName(4, "Progress");
		list.setColumnName(5, "???");
		
		list.setColumnWidth(0,165);
		list.setColumnWidth(1,200);
		list.setColumnWidth(2,70);
		list.setColumnWidth(3,70);
		list.setColumnWidth(4,70);
		list.setColumnWidth(5,100);
		
		p.doLayout();
		
		
		disconnect	=new Button("Disconnect User");
		disconnect.setSize(200,35);
		disconnect.setLocation(10, 260);
		add(disconnect);
		disconnect.addActionListener(chandler.new DisconnectHandler());
		
		
		browse		=new Button("Browse User's Files");
		browse.setSize(200,35);
		browse.setLocation(10, 305);
		add(browse);
		browse.addActionListener(chandler.new ConnectHandler());
		

		clearAll	=new Button("Clear All Done");
		clearAll.setSize(200,35);
		clearAll.setLocation(220, 260);
		add(clearAll);
		clearAll.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for (int i=0; i<list.length(); i++) {
					MCListItemInterface item=list.getMCListItem(i);
					if (((DownloadMCListItem)item).isDone()){
						list.removeItem(item);
						i--;
					}
				}
			}
		
		});

		if (repaintThread!=null) {
			System.out.println("Reaint already init"); 
			//System.exit(1); //bad
		}
		(new RepaintLoop()).start();
		
	}
	private MysterThread repaintThread;
	
	private Image doubleBuffer;		//adds double buffering
	public void update(Graphics g) {
		if (doubleBuffer==null) {
			doubleBuffer=createImage(600,400);
		}
		Graphics graphics=doubleBuffer.getGraphics();
		paint(graphics);
		g.drawImage(doubleBuffer, 0, 0, this);
	}

	public void paint(Graphics g) {
	}
	
	private class ConnectionHandler implements ConnectionManagerListener {

		public void sectionEventConnect(ConnectionManagerEvent e) {
			if (e.getSection()==FileSenderThread.NUMBER) {
				ServerDownloadDispatcher d=((FileSenderThread.ServerTransfer)(e.getSectionObject())).getDispatcher();
				DownloadMCListItem i=new DownloadMCListItem(d);
				list.addItem(i);
				i.setUser(e.getIP());
			}
		}
		
		public void sectionEventDisconnect(ConnectionManagerEvent e) {
		//	if (e.getSection()==FileSenderThread.NUMBER) {
		//		DownloadMCListItem d=itemlist.getDownloadMCListItem(((ServerDownloadDispatcher)(e.getDispatcher())));
		//		itemlist.removeElement(d);
		//		d.done();
		//	}
		}
		
		public void disconnectSelected() {
			if (list.getSelectedIndex()==-1) return; //fix this so buttons are disabled.
			((ServerDownloadDispatcher)(list.getItem(list.getSelectedIndex()))).getDownloadInfo().disconnectClient(); //isn't this a great line or what?
		}
		
		public void newConnectWindow() {
		if (list.getSelectedIndex()==-1) return; //fix this so buttons are disabled.
			(new ClientWindow(""+(((DownloadMCListItem)(list.getMCListItem(list.getSelectedIndex())))).getAddress())).show();
		}
		
		
		
		public class DisconnectHandler implements ActionListener {
		
			public void actionPerformed(ActionEvent e) {
				disconnectSelected();
			}
		}
		
		public class ConnectHandler implements ActionListener {
		
			public void actionPerformed(ActionEvent e) {
				newConnectWindow();
			}
		}
		
	}
	

	
	private class JavaIsStupid extends MysterThread {
		public void run() {
			try {sleep(5000); }catch (Exception ex){}
			inited();
		}
	}
	
	private class RepaintLoop extends MysterThread {
		public void run() {
			do {
				list.repaint();
				try {sleep(500);} catch (Exception ex) {}
			} while(true);
		}
	}
	
	

//}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	// Noise maker ->>
	/*
	public class MyThread extends Thread {
	
		public void run() {
			MemoryImageSource source=null;
			int xsize=600;
			int ysize=400;
			
			if (image==null) System.out.println("AGH!");
			int[] array=TabUtilities.getPixels(image, 0,0,xsize, ysize);
			int counter=0;
			do {
			//init
	counter++;
			
				for (int i=0; i<array.length; i++) {
					array[i]=(((int)(Math.random()*2))==0?0xEEFFFFFF:0xff000000);
					//array[i]=(counter%2==0?0xEEFFFFFF:0xff000000);
					//array[i]=0xee00ff00;
				}
				
				if (source==null) {
					source = new MemoryImageSource(xsize, ysize, array, 0, xsize);
			    	source.setAnimated(true);
					image=createImage(source);				
				}
				try {
					sleep(100);
				} catch (Exception ex) {
				
				}
				source.newPixels(0,0,xsize,ysize);
				//repaint();

			//..
			} while (true);
		}
	}
	*/
}