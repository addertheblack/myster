package com.myster.tracker.ui;

import java.util.Vector;
import java.awt.*;
import java.awt.event.*;
import com.general.util.*;
import com.general.mclist.*;
import com.myster.tracker.*;
import com.myster.menubar.MysterMenuBar;
import Myster;
import com.myster.util.MysterThread;
import com.myster.util.TypeDescription;
import com.myster.util.OpenConnectionHandler;
import com.myster.ui.MysterFrame;

public class TrackerWindow extends MysterFrame {
	private static TrackerWindow me;// = new TrackerWindow();
	private MysterThread updater;
	
	private MCList list;
	private Choice choice;

	GridBagLayout gblayout;
	GridBagConstraints gbconstrains;
	
	private static com.myster.ui.WindowLocationKeeper keeper=new com.myster.ui.WindowLocationKeeper("Tracker");
	
	public static void initWindowLocations() {
		Rectangle[] rect=com.myster.ui.WindowLocationKeeper.getLastLocs("Tracker");
		if (rect.length>0) {
			getInstance().me.setBounds(rect[0]);
			getInstance().me.setVisible(true);
		}
	}
	
	private TrackerWindow () {
		keeper.addFrame(this); //never remove
		
		
	
		//Do interface setup:
		gblayout=new GridBagLayout();
		setLayout(gblayout);
		gbconstrains=new GridBagConstraints();
		gbconstrains.fill=GridBagConstraints.BOTH;
		gbconstrains.ipadx=1;
		gbconstrains.ipady=1;
		
		//init objects
		choice=new Choice();
		{
			//init choice
			TypeDescription[] t=TypeDescription.loadTypeAndDescriptionList(this);
			
			for (int i=0; i<t.length; i++) {
				choice.add(""+t[i].getDescription()+" ("+t[i].getType()+")");
			}
		}
		
		list=new MCList(6,true,this);
	
		//add Objects
		addComponent(choice, 		0,	0,	1,	1,	99,	0);
		addComponent(list.getPane(),1,	0,	1,	1,	99,	99);
		
		//Add Event handlers
		
		
		//other stuff
		
		list.setColumnName(0,"Server Name: ");
		list.setColumnName(1,"# Files:");
		list.setColumnName(2,"Status:");
		list.setColumnName(3,"IP Address:");
		list.setColumnName(4,"Last Ping Time:");
		list.setColumnName(5,"Rank:");
		
		list.setColumnWidth(0,150);
		list.setColumnWidth(1,70);
		list.setColumnWidth(2,70);
		list.setColumnWidth(3,150);
		list.setColumnWidth(4,70);
		list.setColumnWidth(5,70);
		//loadList();
		
		addWindowListener(new MyWindowHandler());
		list.addMCListEventListener(new OpenConnectionHandler());
		
		
		choice.addItemListener(new ChoiceListener());
		
		updater=new MyThread();
		//updater.start();
		
		
		
		setSize(600,400);
		setTitle("Tracker");
		addComponentListener(new ComponentAdapter(){
			public void componentShown(ComponentEvent e) {
				System.out.println("SHOWN!");
				loadList();
			
				updater.end();
				updater=new MyThread();
				updater.start();
			}
			
			public void componentHidden(ComponentEvent e) {
				System.out.println("HIDDEN!");
				updater.end();
			}
		});
		new MysterMenuBar(this);
	}
	
	/**
	*	Singleton
	*/
	public static synchronized TrackerWindow getInstance() {
		if (me==null) {
			me =new TrackerWindow();
		}
		
		return me;
	}

	/**
	*	Makes grid bag layout less nasty.
	*/
	public void addComponent(Component c, int row, int column, int width, int height, int weightx, int weighty) {
		gbconstrains.gridx=column;
		gbconstrains.gridy=row;
		
		gbconstrains.gridwidth=width;
		gbconstrains.gridheight=height;
		
		gbconstrains.weightx=weightx;
		gbconstrains.weighty=weighty;
		
		gblayout.setConstraints(c, gbconstrains);
		
		add(c);
		
	}

	
	/**
	*	Returns the selected type.
	*/
	
	public synchronized String getType() {
		int index=choice.getSelectedIndex();
		
		TypeDescription[] t=TypeDescription.loadTypeAndDescriptionList(this);
		return t[index].getType();
	}
	
	Vector itemsinlist;
	
	
	/**
	*	Remakes the MCList. This routine is called every few minutes to update the tracker window 
	*	with the status of the tracker.
	*/
	private synchronized void loadList() {
		list.clearAll();
		itemsinlist=new Vector(IPListManager.LISTSIZE);
		IPListManager manager=IPListManagerSingleton.getIPListManager();
		Vector vector=manager.getAll(getType());
		TrackerMCListItem[] m=new TrackerMCListItem[vector.size()];
		
		for(int i=0; i<vector.size(); i++) {
			m[i]=new TrackerMCListItem((MysterServer)(vector.elementAt(i)), getType());
			itemsinlist.addElement(m[i]);
		}
		list.addItem(m);
	}
	
	
	/**
	*	Refreshes the list information with new information form the tracker. 
	*/
	private synchronized void refreshTheList() {
		for (int i=0; i<itemsinlist.size(); i++) {
			((TrackerMCListItem)(itemsinlist.elementAt(i))).refresh();
		}
		list.repaint();
	}
	
	
	/**
	*	This thread is responsible for keeping the tracker window updated. It does so by polling
	*	the IPListManager repeataly. Every once in a while it reloads the information complely.
	*/
	private class MyThread extends MysterThread {
		public MyThread(){}
		
		boolean flag=true;
		
		public void run() {
			long counter=0;
			do {
				//loadList();
				counter++;
				try {
					sleep(30000);
				} catch (InterruptedException ex) {
					continue; //Should check condition and exit! assuing interrupt came from end();
				}
				//setEnabled(true);
				//setSize(300,1000);
				if (counter%10==9) {
					loadList();
					counter=0;
				} else refreshTheList();
			} while (flag);
		}
		
		public void end() {
			flag=false;
			interrupt();
			try {
				join();
			} catch (InterruptedException ex) {
				//should never happen.
			}
		}
	}
	
	private class ChoiceListener implements ItemListener {
		public ChoiceListener() {}
		
		public void itemStateChanged(ItemEvent e) {
			loadList();
		}
	}
	
	private class MyWindowHandler extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			hide();
		}
	}
	
	private static class TrackerMCListItem extends MCListItemInterface {
		MysterServer server;
		Sortable sortables[]=new Sortable[6];
		IPListManager manager=IPListManagerSingleton.getIPListManager();
		String type;
		
		public TrackerMCListItem(MysterServer s, String t) {
			server=s;
			type=t;
			refresh();
		}
		
		public Sortable getValueOfColumn(int i) {
			return sortables[i];
			
		}
		
		public void refresh() {
			server=manager.getQuickServerStats(""+server.getAddress());
			
			if (server==null) {
				sortables[0]=new SortableString(""+server.getAddress());
				sortables[1]=new SortableLong(0);
				sortables[2]=new SortableStatus(false, true);
				sortables[3]=new SortableString(""+server.getAddress());
				sortables[4]=new SortablePing(-1);
				sortables[5]=new SortableRank(-1);
			} else {
				sortables[0]=new SortableString(server.getServerIdentity());
				sortables[1]=new SortableLong(server.getNumberOfFiles(type));
				sortables[2]=new SortableStatus(server.getStatus(),server.isUntried());
				sortables[3]=new SortableString(""+server.getAddress());
				sortables[4]=new SortablePing(server.getPingTime());
				sortables[5]=new SortableRank(((long)(100*server.getRank(type))));
			}
		}
		
		public Object getObject() {
			return this;
		}
		
		public String toString() {
			return ""+server.getAddress();
		}
		
		private static class SortablePing extends SortableLong {
			public static final int UNKNOWN=1000000;
			public static final int DOWN=1000001;
		
			public SortablePing(long c) {
				super(c);
				if (c==-1) {
					number=UNKNOWN;
				} else if (c==-2) {
					number=DOWN;
				} else {
					number=c;
				}
			}
			
			public String toString() {
				switch ((int)number) {
					case UNKNOWN:
						return "-";
					case DOWN:
						return "Timeout";
					default:
						return number+"ms";
				}
			}
		}
		
		private static class SortableRank extends SortableLong {
			public SortableRank(long c) {super(c);}
			
			public String toString() {
				if (number<-1000) return "-inf";
				else return super.toString();
			}
		}
		
		private static class SortableStatus implements Sortable {
			boolean status, isUntried;
			public SortableStatus(boolean status, boolean isUntried) {
				this.isUntried=isUntried;
				this.status=status;
			}
			
	
			public boolean isLessThan(Sortable temp) {
				SortableStatus other=(SortableStatus)(temp);
				
				if (isUntried) {
					if (other.isUntried) {
						return (!status&&other.status);
					} else {
						return true;
					}
				} else {
					if (other.isUntried) {
						return false;
					} else {
						return (!status&&other.status);
					}
				}
			}
			
			public boolean isGreaterThan(Sortable temp) {
				SortableStatus other=(SortableStatus)(temp);
				
				if (isUntried) {
					if (other.isUntried) {
						return (status&&!other.status);
					} else {
						return false;
					}
				} else {
					if (other.isUntried) {
						return true;
					} else {
						return (status&&!other.status);
					}
				}
			}
			
			public boolean equals(Sortable m) {
				SortableStatus other=(SortableStatus)m;
				return (other.status==status&&other.isUntried==isUntried);
			}
			
			public Object getValue() {
				return null;//caution
			}
			
			public String toString() {
				return (isUntried?"-":(status?"up":"down"));
			}
		}
	}

}