package com.myster.type.ui;

import java.awt.*;

import com.general.mclist.*;
import com.general.util.MessagePanel;

import com.myster.pref.Preferences;
import com.myster.pref.ui.PreferencesPanel;

import com.myster.type.*;

public class TypeManagerPreferencesGUI extends PreferencesPanel {
	public static void init() {
		Preferences.getInstance().addPanel(new TypeManagerPreferencesGUI());
	}
	
	private MCList mcList;
	private MessagePanel message;
	
	private static final int HEADER_Y 	= 100;
	private static final int LABEL_Y	= 25;
	
	public TypeManagerPreferencesGUI() {
		setLayout(null);
		
		message = new MessagePanel(
			"Each file type "+
			"that is enabled adds a constant CPU and bandwidth overhead to your Myster client, however it "+
			"allows you to search for files of that type. Enable the file types "+
			"you use and disable the ones you don't. Changes take affect on "+
			"reloading the program.");
			message.setLocation(0,0);
			message.setSize(STD_XSIZE, HEADER_Y);
			add(message);
	
		mcList = new MCList(2, true, this);
		
		mcList.setColumnName(0, "Type");
		mcList.setColumnName(1, "Enabled?");
		
		mcList.setColumnWidth(0, 300);
		mcList.setColumnWidth(1, 100);
		
		ScrollPane pane = mcList.getPane();
		pane.setLocation(0, HEADER_Y);
		pane.setSize(STD_XSIZE, STD_YSIZE - HEADER_Y);
		add(pane);
		
		mcList.addMCListEventListener(new MCListEventListener() {
			public void doubleClick(MCListEvent e) {
				int index = mcList.getSelectedIndex();
				
				if (index == -1) return;
				
				MyMCListItem myItem = (MyMCListItem)(mcList.getMCListItem(index));
				myItem.setEnabled(! myItem.getEnabled()); //I hope you appreciate this. I could've done it on one line :-)

				mcList.repaint();
			}
			public void selectItem(MCListEvent e) {}
			public void unselectItem(MCListEvent e) {}
		});
		
		setSize(STD_XSIZE, STD_YSIZE);
	}
	
	public void save() {
		if (! isThereAtLeastOneTypeEnabled()) {
			(new com.general.util.AnswerDialog(getFrame(), "You must have at least one File Type enabled to run Myster.", new String[]{"Ok"})).answer();
			return;
		}
		
		for (int i = 0; i < mcList.length(); i++) {
			TypeDescriptionList.getDefault().setEnabledType((MysterType)(mcList.getItem(i)), ((MyMCListItem)(mcList.getMCListItem(i))).getEnabled()); //what a fun line.
		}
	}
	
	private boolean isThereAtLeastOneTypeEnabled() {
		for (int i = 0; i< mcList.length(); i++) {
			if (((MyMCListItem)(mcList.getMCListItem(i))).getEnabled()) return true;
		}
		return false;
	}
	
	public void reset() {
		mcList.clearAll();
		TypeDescription[] listOfTypes = TypeDescriptionList.getDefault().getAllTypes();
		
		GenericMCListItem[] items = new GenericMCListItem[listOfTypes.length];
	
		for (int i = 0; i < listOfTypes.length; i++) {
			String typeDescription = listOfTypes[i].getDescription() + " ("  +listOfTypes[i].getType().toString()+")";
	
			
			items[i] = new MyMCListItem(listOfTypes[i].getType(), typeDescription, TypeDescriptionList.getDefault().isTypeEnabledInPrefs(listOfTypes[i].getType()));
		}
	
		mcList.addItem(items);
	}
	
	public String getKey() { return "Types enabled/disable"; }
	
	private static class MyMCListItem extends GenericMCListItem {
		SortableString description;
		boolean enabled;
		
		public MyMCListItem(MysterType t, String description, boolean enabled) {
			super(new Sortable[]{}, t);
			
			this.description 	= new SortableString(description); 
			this.enabled 		= enabled;
		}
		
		public Sortable getValueOfColumn(int i) {
			switch (i) {
				case 0  : return description; //slightly fast if there's no "new".
				case 1  : return new SortableBoolean(enabled);
				default : return new SortableString("");
			}
		}
		
		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
		
		public boolean getEnabled() { return enabled; }
	}
	
	
}


/*

import java.awt.*;

import com.myster.pref.Preferences;
import com.myster.pref.ui.PreferencesPanel;

import com.myster.type.*;

public class TypeManagerPreferencesGUI extends PreferencesPanel {
	public static void init() {
		Preferences.getInstance().addPanel(new TypeManagerPreferencesGUI());
	}
	
	private List enabledList, disabledList;
	private Label enabledListLabel, disabledListLabel;
	
	private static final int HEADER_Y 	= 100;
	private static final int LABEL_Y	= 25;
	
	public TypeManagerPreferencesGUI() {
		setLayout(null);
	
		enabledListLabel = new Label("Enabled Types:");
		enabledListLabel.setLocation(0, HEADER_Y);
		enabledListLabel.setSize(STD_XSIZE / 2, LABEL_Y);
		add(enabledListLabel);
		
		disabledListLabel = new Label("Disabled Types:");
		disabledListLabel.setLocation(STD_XSIZE / 2, HEADER_Y);
		disabledListLabel.setSize(STD_XSIZE / 2, LABEL_Y);
		add(disabledListLabel);
		
		enabledList = new List();
		enabledList.setLocation(0, HEADER_Y+LABEL_Y);
		enabledList.setSize(STD_XSIZE / 2, STD_YSIZE - (HEADER_Y+LABEL_Y));
		add(enabledList);
		
		disabledList = new List();
		disabledList.setLocation(STD_XSIZE / 2, HEADER_Y+LABEL_Y);
		disabledList.setSize(STD_XSIZE / 2, STD_YSIZE - (HEADER_Y+LABEL_Y));
		add(disabledList);
		
		setSize(STD_XSIZE, STD_YSIZE);
	}
	
	public void save() {
	
	}
	
	public void reset() {
		enabledList.clear();
		disabledList.clear();
	
		TypeDescription[] listOfTypes = TypeDescriptionList.getDefault().getAllTypes();
	
		for (int i = 0; i < listOfTypes.length; i++) {
			String typeDescription = listOfTypes[i].getDescription() + " ("  +listOfTypes[i].getType().toString()+")";
		
			if (TypeDescriptionList.getDefault().isTypeEnabled(listOfTypes[i].getType())) {
				enabledList.add(typeDescription);
			} else {
				disabledList.add(typeDescription);
			}
		}
	}
	
	public String getKey() { return "Types enabled/disable"; }
}

*/