/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.pref.ui;

import java.awt.*;
import java.awt.event.*;
import com.myster.server.ServerFacade;
import com.myster.menubar.MysterMenuBar; //!
import java.util.Hashtable;
import java.util.Enumeration;
import com.myster.ui.MysterFrame;

/**
*	This object is reponsible for the prefferences dialog box that pops up when you select "preferences" from the menu bar.
*	Pluggins can choose to register their preferences with this preferences dialog. NOTE: NOTHING in here should be called
*	from the outside. 
*/

public class PreferencesDialogBox extends MysterFrame {	//protected...!
	Insets insets;
	
	final int XDEFAULT=600;
	final int YDEFAULT=400;
	
	String[] choices={"14.4","28.8","33.6", "56k", "64", "128",
				"ADSL", "Cable modem",
				"T1", "T3", "40Mbits/sec +"};
				
	MainPanel mypanel;

	/**
	*	Builds a new Preferences Dialog
	*/
	public PreferencesDialogBox () {
		super("Preferences Browser");
		setBackground(new Color(240,240,240));
		//setLayout(null);
		MysterMenuBar m=new MysterMenuBar(this);
		mypanel=new MainPanel();

		add(mypanel);


		setResizable(false);
		
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				e.getWindow().setVisible(false);
				//mypanel.restore();
			}
		});
		
		addComponentListener(new ComponentListener() {
		
			public void componentResized(ComponentEvent e) {
				//nothing
			}

			public void componentMoved(ComponentEvent e) {
				//nothing
			}

			public void componentShown(ComponentEvent e) {
				mypanel.restore();
			}

			public void componentHidden(ComponentEvent e) {
				//nothing
			}
		
		});
		
		assertSize();
	}

	
	//Makes sure that the dialog is the correct size with the insets. This is a work around for multiple java bugs.
	private void assertSize() {
		insets=getInsets();
		if (getSize().width!=XDEFAULT+insets.right+insets.left||getSize().height!=YDEFAULT+insets.top+insets.bottom) {
			setSize(XDEFAULT+insets.right+insets.left,YDEFAULT+insets.top+insets.bottom);
			doLayout();//fucking java bug
		}
	}
	
	//Adds a panel, duh.
	//Responsible for encapsulation all book-keeping required for adding a panel.
	public void addPanel(PreferencesPanel p) {
		mypanel.addPanel(p);
	}
	
	//Removes a panel, duh.
	//see above.
	public void removePanel(String key) {
		mypanel.removePanel(key);
	}

	
	private class MainPanel extends Panel {
		List list;
		Button save;
		Button revert;
		Button apply;
		Hashtable hash=new Hashtable();
		Panel lastPanel;
		Panel showerPanel;
		
		Label header;
	
		public MainPanel() {
			setLayout(null);
			//setBackground(new Color(255,255,0));
			
			list=new List();
			list.setSize(150-5-5, YDEFAULT-50-5);
			list.setLocation(5,5);
			list.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					if (e.getStateChange()==ItemEvent.SELECTED) {
						synchronized (MainPanel.this) {
							PreferencesPanel panel=(PreferencesPanel)(hash.get(((String)(list.getSelectedItem()))));
							if (panel==null) {
								removePanel((String)(list.getSelectedItem()));
							}
							showPanel(panel);
						}
					}
				}
			});
			add(list);
			
			apply=new Button("Apply");
			apply.setSize(100, 30);
			apply.setLocation(XDEFAULT-5-100, YDEFAULT-30-7);
			apply.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					save();
				}
			});
			add(apply);
			
			save=new Button("OK");
			save.setSize(100, 30);
			save.setLocation(XDEFAULT-100-5-5-100-5-100-5, YDEFAULT-30-7);
			save.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					save();
					PreferencesDialogBox.this.setVisible(false);
				}
			});
			add(save);
			
			
			
			revert=new Button("Cancel");
			revert.setSize(100, 30);
			revert.setLocation(XDEFAULT-100-5-5-100-5, YDEFAULT-30-7);
			revert.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					restore();
					PreferencesDialogBox.this.setVisible(false);
				}
			});
			add(revert);
			
			showerPanel=new Panel();
			showerPanel.setLayout(null);
			showerPanel.setSize(PreferencesPanel.STD_XSIZE, PreferencesPanel.STD_YSIZE);
			showerPanel.setLocation(150, 50);
			add(showerPanel);
			
			header=new Label("");
			header.setLocation(150, 5);
			header.setSize(PreferencesPanel.STD_XSIZE-5, 40);
			header.setBackground(new Color(225,225,225));
			add(header);
			
			setResizable(false);
			
			setSize(XDEFAULT,YDEFAULT);
		}
		
		//Hide the currently showing panel and shows the new one.
		public synchronized void showPanel(PreferencesPanel p) {
			if (lastPanel!=null) {
				lastPanel.setVisible(false);
			}
			p.setVisible(true);
			lastPanel=p;
			header.setText(p.getKey());
		}
		
		//Sets the selection in the List to the correct value.
		public synchronized void selectKey(String panelString) {
			for (int i=0; i<list.getItemCount(); i++) {
				if (((String)(list.getItem(i))).equals(panelString)){
					list.select(i);
					break;
				}
			}
			showPanel((PreferencesPanel)(hash.get(panelString)));
		}
		
		//Draw the seperator line.
		public void paint(Graphics g) {
			g.setColor(new Color(150,150,150));
			g.drawLine(10, YDEFAULT-45, XDEFAULT-20, YDEFAULT-45);
			header.setFont(new Font(getFont().getName(), Font.BOLD, 24));
			assertSize(); //hack for java bug in most VMs. (pack before show() causes crash.)
		}
		
		//Tells *panels* to save changes
		public synchronized void save() {
			Enumeration enum=hash.elements();
			while (enum.hasMoreElements()) {
				((PreferencesPanel)(enum.nextElement())).save();
			}
		}
		
		//Tells *panels* to refresh
		public synchronized void restore() {
			Enumeration enum=hash.elements();
			while (enum.hasMoreElements()) {
				((PreferencesPanel)(enum.nextElement())).reset();
			}
		}
		
		//Adds a panel, duh.
		//Responsible for encapsulation all book-keeping required for adding a panel.
		public synchronized void addPanel(PreferencesPanel p) {
			if (hash.get(p.getKey())==null) {
				list.add(p.getKey());
				hash.put(p.getKey(), p);
				p.setVisible(true);
				p.setLocation(0,0);
				p.setSize(p.getPreferredSize());
				showerPanel.add(p);
				showerPanel.doLayout();//java VM bug.
				System.out.println("ADDING PANEL");
				selectKey((String)(p.getKey()));
			}
		}
		
		//Removes a panel, duh.
		//see above.
		public synchronized void removePanel(String type) {
			if (hash.get(type)!=null) {
				list.remove(type);
				
				PreferencesPanel pp_temp=(PreferencesPanel)(hash.get(type));
				if (pp_temp!=null) showerPanel.remove(pp_temp);
				hash.remove(type);
				if (list.countItems()!=0) {
					list.select(0);
				}
			}
		}
		
		public Dimension getMinimumSize() {
			return new Dimension(XDEFAULT, YDEFAULT);
		}
		
		public Dimension getMaximumSize() {
			return new Dimension(XDEFAULT, YDEFAULT);
		}
		
		public Dimension getPreferredSize() {
			return new Dimension(XDEFAULT, YDEFAULT);
		}

	}
}