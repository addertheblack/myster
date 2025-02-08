package com.myster.type.ui;

import java.awt.Container;

import com.general.mclist.GenericMCListItem;
import com.general.mclist.MCList;
import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventListener;
import com.general.mclist.MCListFactory;
import com.general.mclist.Sortable;
import com.general.mclist.SortableBoolean;
import com.general.mclist.SortableString;
import com.general.util.AnswerDialog;
import com.general.util.MessagePanel;
import com.myster.pref.ui.PreferencesPanel;
import com.myster.type.MysterType;
import com.myster.type.TypeDescription;
import com.myster.type.TypeDescriptionList;

/**
 * The TypeManagerPreferencesGUI represents the Type Enable/Disable preferences panel.
 */

public class TypeManagerPreferencesGUI extends PreferencesPanel {
    private final MCList<MysterType> mcList;
    private final MessagePanel message;
    private final TypeDescriptionList tdList;

    private static final int HEADER_Y = 100;

	/**
	 * Constructor for the TypeManagerPreferencesGUI.
	 * @param tdList 
	 *
	 */
    public TypeManagerPreferencesGUI(TypeDescriptionList tdList) {
        this.tdList = tdList;
        setLayout(null);

        message = new MessagePanel(
                "Each file type "
                        + "that is enabled adds a constant CPU and bandwidth overhead to your Myster client, however it "
                        + "allows you to search for files of that type. Enable the file types "
                        + "you use and disable the ones you don't. Changes take affect on "
                        + "reloading the program.");
        message.setLocation(0, 0);
        message.setSize(STD_XSIZE, HEADER_Y);
        add(message);

        mcList = MCListFactory.buildMCList(2, true, this);

        mcList.setColumnName(0, "Type");
        mcList.setColumnName(1, "Enabled?");

        mcList.setColumnWidth(0, 300);
        mcList.setColumnWidth(1, 100);

        Container pane = mcList.getPane();
        pane.setLocation(0, HEADER_Y);
        pane.setSize(STD_XSIZE, STD_YSIZE - HEADER_Y);
        add(pane);

        mcList.addMCListEventListener(new MCListEventListener() {
            public void doubleClick(MCListEvent e) {
                int index = mcList.getSelectedIndex();

                if (index == -1)
                    return;

                MyMCListItem myItem = (MyMCListItem) (mcList
                        .getMCListItem(index));
                myItem.setEnabled(!myItem.getEnabled()); //I hope you
                                                         // appreciate this. I
                                                         // could've done it on
                                                         // one line :-)

                mcList.repaint();
            }

            public void selectItem(MCListEvent e) {
                // nothing
            }

            public void unselectItem(MCListEvent e) {
                // nothing
            }
        });

        setSize(STD_XSIZE, STD_YSIZE);
    }
    
    /**
     * Saves the options selected in the TypeManagerPreferencesGUI.
     */
    public void save() {
        if (!isThereAtLeastOneTypeEnabled()) {
            AnswerDialog.simpleAlert(getFrame(),
                                     "You must have at least one File Type enabled to run Myster.",
                                     new String[] { "Ok" });
            return;
        }

        for (int i = 0; i < mcList.length(); i++) {
            tdList.setEnabledType((mcList.getItem(i)),
                                  ((MyMCListItem) (mcList.getMCListItem(i))).getEnabled());
        }
    }
    
    /**
     * returns if there's any MysterTypes enabled in the GUI. Used for deciding whether to save or not.
     * 
     * @return true if there's at least 1 MysterType enabled in the GUI, false otherwise.
     */
    private boolean isThereAtLeastOneTypeEnabled() {
        for (int i = 0; i < mcList.length(); i++) {
            if (((MyMCListItem) (mcList.getMCListItem(i))).getEnabled())
                return true;
        }
        return false;
    }

    /**
     * Sets the GUI to match the action state of the default TypeDescriptionList.
     */
    public void reset() {
        mcList.clearAll();
        TypeDescription[] listOfTypes = tdList.getAllTypes();

        GenericMCListItem<MysterType>[] items = new GenericMCListItem[listOfTypes.length];

        for (int i = 0; i < listOfTypes.length; i++) {
            String typeDescription = listOfTypes[i].getDescription();

            items[i] = new MyMCListItem(listOfTypes[i].getType(),
                                        typeDescription,
                                        tdList.isTypeEnabledInPrefs(listOfTypes[i].getType()));
        }

        mcList.addItem(items);
    }
    
    public String getKey() {
        return "Types enable/disable";
    }

    private static class MyMCListItem extends GenericMCListItem<MysterType> {
        SortableString description;

        boolean enabled;

        public MyMCListItem(MysterType t, String description, boolean enabled) {
            super(new Sortable[] {}, t);

            this.description = new SortableString(description);
            this.enabled = enabled;
        }

        public Sortable<?> getValueOfColumn(int i) {
            switch (i) {
            case 0:
                return description; //slightly fast if there's no "new".
            case 1:
                return new SortableOnOff(enabled);
            default:
                return new SortableString("");
            }
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean getEnabled() {
            return enabled;
        }
    }

    private static class SortableOnOff extends SortableBoolean {

        public SortableOnOff(boolean value) {
            super(value);
        }

        public String toString() {
            return (bool ? "On" : "Off");
        }
    }

}
