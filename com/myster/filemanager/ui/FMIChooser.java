/* 

	Title:			Myster Open Source
	Author:			Andrew Trumper
	Description:	Generic Myster Code
	
	This code is under GPL

Copyright Andrew Trumper 2000-2001
*/

package com.myster.filemanager.ui;

import java.awt.*;
import java.awt.event.*;
import com.myster.util.TypeChoice;
import com.myster.pref.ui.PreferencesPanel;
import java.util.Hashtable;
import java.util.Enumeration;
import com.myster.filemanager.FileTypeListManager;
import com.myster.type.MysterType;


/**
*	The FMICHooser is the FileManagerInterfaceChooser. It's the GUI for the FileManager prefs panel. It's built to be 
*	as independent from the internal working of the FileManager, dispite having access to the sweat, sweat inners.
*/

public class FMIChooser extends PreferencesPanel {
	boolean inited=false;
	private String path;
	private FileTypeListManager manager;
	private TypeChoice choice;
	private Checkbox checkbox;
	private Button button;
	private Label textfeild;
	private Label folderl,filelistl;
	private List flist;
	private Button setAllButton;
	private Hashtable hash=new Hashtable();
	
	private final int XPAD=10;
	private final int SAB=200;
	public FMIChooser(FileTypeListManager manager) {
		this.manager=manager;
		setLayout(null);
		//if (inited==true) System.exit(0);
		inited=true;
		
		
		choice=new TypeChoice();
		choice.setLocation(5,4);
		choice.setSize(STD_XSIZE-XPAD-XPAD-SAB,20);
		choice.addItemListener(new ItemListener() {
			public synchronized void itemStateChanged (ItemEvent a) {
				restoreState();
				repaint();
			}
		});
		add(choice);
		
		setAllButton=new Button("Set all paths to this path");
		setAllButton.setLocation(STD_XSIZE-XPAD-SAB, 4);
		setAllButton.setSize(SAB, 20);
		setAllButton.addActionListener(new ActionListener() {
			public synchronized void actionPerformed(ActionEvent a) {
				String newPath=path;
				
				for (int i=0; i<choice.getItemCount(); i++) {
					
					//Figure out what the bool.. should be (hack)
					Object o=hash.get(choice.getType(i));
					boolean bool_temp;
					if (o!=null) {
						bool_temp=((SettingsStruct)(o)).shared;
					} else {
						bool_temp=FMIChooser.this.manager.isShared(choice.getType(i));
					} //end
					
					hash.put(choice.getType(i), new SettingsStruct(choice.getType(i), newPath, bool_temp));
					System.out.println(newPath);
				}
			}
		});
		add(setAllButton);
		
		path=manager.getPathFromType(choice.getType());
		
		checkbox=new Checkbox("Share this type", manager.isShared(choice.getType()));
		checkbox.setLocation(10, 55);
		checkbox.setSize(150,25);
		checkbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				hash.put(choice.getType(), new SettingsStruct(choice.getType(), path, checkbox.getState()));
			} 
		});
		add(checkbox);
		
		
		button=new Button("Set Folder");
		button.setLocation(STD_XSIZE-100-XPAD,55);
		button.setSize(100,25);
		button.addActionListener(new ActionListener() {
			public synchronized void actionPerformed(ActionEvent a) {
				FileDialog dialog=new FileDialog(new Frame(), "Choose a directory and press save", FileDialog.SAVE);
				dialog.setFile("Choose a directory and press save");
				dialog.show();		//show choose dir dialog
				String p=dialog.getDirectory();
				
				if (p==null) {	//If user canceled path will be null
					System.out.println("User cancelled the action.");
					return;
				}
				path=p;
				textfeild.setText(TIM(path));
				hash.put(choice.getType(), new SettingsStruct(choice.getType(), path, checkbox.getState()));
			}
		
		});
		add(button);
		
		folderl=new Label("Shared Folder:");
		folderl.setLocation(10,85);
		folderl.setSize(100, 20);
		add(folderl);
		
		textfeild=new Label(TIM(manager.getPathFromType(choice.getType()))); //dependency on choice being created first.
		textfeild.setLocation(100+XPAD+5,85);
		//textfeild.setEditable(false);
		textfeild.setSize(STD_XSIZE-100-2*XPAD-5,20);
		add(textfeild);
		
		filelistl=new Label("Shared Files (click \"Apply\" to see changes) :");
		filelistl.setLocation(10,110);
		filelistl.setSize(STD_XSIZE-2*XPAD, 20);
		add(filelistl);
		
		flist=new List();
		flist.setLocation(10, 135);
		flist.setSize(STD_XSIZE-2*XPAD, STD_YSIZE-150);
		add(flist);
		
		repaint();
		reset();
		restoreState();
	}
	
	public void save() {
		Enumeration enum=hash.elements();
		while (enum.hasMoreElements()) {
			SettingsStruct s=(SettingsStruct)(enum.nextElement());
			manager.setPathFromType(s.type, s.path);
			manager.setShared(s.type, s.shared);
		}
		reset();//funky.
	}
	
	private static class SettingsStruct {
		String path;
		boolean shared;
		MysterType type;
		
		public SettingsStruct(MysterType type, String path, boolean shared) {
			this.type=type;
			this.path=path;
			this.shared=shared;
		}
	}
	
	public void reset() {
		hash.clear();
		loadStateFromPrefs();
		restoreState();
	}
	
	public void loadStateFromPrefs() {
		path=manager.getPathFromType(choice.getType());
		checkbox.setState(manager.isShared(choice.getType()));
	}
	
	public String getKey() {
		return "File Manager";
	}
	
	public Dimension getPreferredSize() {
		return new Dimension(STD_XSIZE,STD_YSIZE);
	}
	
	public void paint(Graphics g)  {
		g.setColor(new Color(150,150,150));
		g.drawRect(5, 35, STD_XSIZE-10, STD_YSIZE-40);
		g.setColor(getBackground());
		g.fillRect(10, 34, 170, 3);
		g.setColor(Color.black);
		g.drawString("Setting for type: "+choice.getType(), 12, 39);
	}
	
	private void restoreState() {
		SettingsStruct ss=(SettingsStruct)(hash.get(choice.getType()));
		if (ss!=null) {
			path=ss.path;
			checkbox.setState(ss.shared);
		} else {
			loadStateFromPrefs(); //if the state hasen't been chenged then load form prefs.
		}
	
		flist.removeAll();
		flist.add("<Indexing files.... this may take a while.....>");
		textfeild.setText(TIM(path));
		String[] s=manager.getDirList(choice.getType());
		
		flist.removeAll();
		
		if (s!=null) {
			if (!checkbox.getState()) {
				flist.add("<no files are being shared, sharing is disabled>");
			} else if (s.length==0) {
				flist.add("<no files are being shared, there's no relevent files in this folder>");
			} else {
				if (s.length>150) {
					flist.add("<You are sharing "+s.length+" files (too many to list)>");
				} else {
					for (int i=0; i<s.length; i++) {
						flist.add(s[i]);
					}
				}
			}
		}
	}
	/*
	*	TIM = Trim in the Middle. This is a utility function that keepos strings under 69 characters
	*	and removes characters from the middle and adding "..."
	*/
	private String TIM(String input){
		final int MAX=69;
		if (input.length()>MAX) {
			return input.substring(0, 25)+"..."+input.substring(input.length()-(MAX-(28)), input.length());
		}
		return input;
	}
	
}