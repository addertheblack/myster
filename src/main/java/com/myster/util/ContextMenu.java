package com.myster.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.general.mclist.JMCList;

public class ContextMenu {
    // Download
    // Download To...
    //
    // Bookmark Server
    // Connect To Server
    //
    // Open Containing Folder

    public static JMenuItem createDownloadItem(JMCList list, ActionListener l) {
        return createMenuItem(list, "Download", l);
    }

    public static JMenuItem createDownloadToItem(JMCList list, ActionListener l) {
        return createMenuItem(list, "Download To...", l);
    }


    public static JMenuItem createBookmarkServerItem(JMCList list, ActionListener l) {
        return createMenuItem(list, "Bookmark Server", l);
    }
    
    public static JMenuItem removeBookmarkServerItem(JMCList list, ActionListener l) {
        return createMenuItem(list, "Remove Bookmark", l);
    }

    public static JMenuItem createMenuItem(JMCList list, String menuName, ActionListener l) {
        Action action = new AbstractAction(menuName) {
            @Override
            public void actionPerformed(ActionEvent e) {
                l.actionPerformed(e);
            }
        };

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                action.setEnabled(list.getSelectedRow() != -1);
            }
        });

        return new JMenuItem(action);
    }


    /**
     * using this fixes the popup behaviour so that right click also selects the row
     */
    public static JPopupMenu addPopUpMenu(JMCList table, Runnable runBeforeMenusDisplay, JMenuItem... items) {
        // Create the popup menu
        JPopupMenu popup = new JPopupMenu();

        // add items to menu
        for (JMenuItem item : items) {
            if (item == null) {
                popup.addSeparator();
            } else {
                popup.add(item);
            }
        }

        // Add mouse listener to the table
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                // Only show popup if it's enabled
                if (!popup.isEnabled()) {
                    return;
                }
                
                // Select the row under the cursor
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < table.getRowCount() && !table.isSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
                
                // Show the popup
                popup.show(e.getComponent(), e.getX(), e.getY());
                
                runBeforeMenusDisplay.run();
            }
        });
        
        return popup;
    }
}
