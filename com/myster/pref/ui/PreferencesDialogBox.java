/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.pref.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.general.util.Util;

import com.myster.ui.MysterFrame;
import com.myster.ui.MysterFrameContext;

/**
 * This object is reponsible for the preferences dialog box that pops up when
 * you select "preferences" from the menu bar. Pluggins can choose to register
 * their preferences with this preferences dialog. NOTE: NOTHING in here should
 * be called from the outside.
 */

public class PreferencesDialogBox extends MysterFrame {
    private static final int XDEFAULT = 600;

    private static final int YDEFAULT = 400;

    private final MainPanel mypanel;

    /**
     * Builds a new Preferences Dialog
     */
    public PreferencesDialogBox(MysterFrameContext context) {
        super(context,"Preferences Browser");

        setBackground(new Color(240, 240, 240));
        //setLayout(null);

        mypanel = new MainPanel();

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
                pack();
            }

            public void componentHidden(ComponentEvent e) {
                //nothing
            }

        });
    }

    //Adds a panel, duh.
    //Responsible for encapsulation all book-keeping required for adding a
    // panel.
    public void addPanel(PreferencesPanel p) {
        p.addFrame(this);
        mypanel.addPanel(p);
        invalidate();
        validate();
    }

    //Removes a panel, duh.
    //see above.
    public void removePanel(String key) {
        mypanel.removePanel(key);
    }

    private class MainPanel extends JPanel {
        private JList list;

        private JButton save;

        private JButton revert;

        private JButton apply;

        private Hashtable hash = new Hashtable();
        private JPanel showerPanel;

        private JLabel header;

        private DefaultListModel listModel;

        public MainPanel() {
            setLayout(null);
            //setBackground(new Color(255,255,0));

            list = new JList();
            listModel = new DefaultListModel();
            list.setModel(listModel);
            JScrollPane listScrollPane = new JScrollPane(list);
            listScrollPane.setSize(150 - 5 - 5, YDEFAULT - 50 - 5);
            listScrollPane.setLocation(5, 5);
            list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    if (e.getValueIsAdjusting())
                        return;
                    PreferencesPanel panel = (PreferencesPanel) (hash.get(list
                            .getSelectedValue()));
                    if (panel == null) {
                        removePanel((String) list.getSelectedValue());
                    }
                    showPanel(panel);
                    
                }
            });
            add(listScrollPane);

            apply = new JButton("Apply");
            apply.setSize(100, 30);
            apply.setLocation(XDEFAULT - 5 - 100, YDEFAULT - 30 - 7);
            apply.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    save();
                }
            });
            add(apply);

            save = new JButton("OK");
            save.setSize(100, 30);
            save.setLocation(XDEFAULT - 100 - 5 - 5 - 100 - 5 - 100 - 5, YDEFAULT - 30 - 7);
            save.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    save();
                    PreferencesDialogBox.this.setVisible(false);
                }
            });
            add(save);

            revert = new JButton("Cancel");
            revert.setSize(100, 30);
            revert.setLocation(XDEFAULT - 100 - 5 - 5 - 100 - 5, YDEFAULT - 30 - 7);
            revert.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    restore();
                    PreferencesDialogBox.this.setVisible(false);
                }
            });
            add(revert);

            showerPanel = new JPanel();
            showerPanel.setLayout(null);
            showerPanel.setSize(PreferencesPanel.STD_XSIZE, PreferencesPanel.STD_YSIZE);
            showerPanel.setLocation(150, 50);
            add(showerPanel);

            header = new JLabel("");
            header.setLocation(150, 5);
            header.setSize(PreferencesPanel.STD_XSIZE - 5, 40);
            header.setBackground(new Color(225, 225, 225));
            add(header);

            setResizable(false);

            setSize(XDEFAULT, YDEFAULT);
//            setDoubleBuffered(true);
        }

        //Hide the currently showing panel and shows the new one.
        public void showPanel(PreferencesPanel p) {
            for (Iterator iter = hash.values().iterator(); iter.hasNext();) {
                PreferencesPanel preferencesPanel = (PreferencesPanel) iter.next();
                preferencesPanel.setVisible(false);
            }
            p.setVisible(true);
            header.setText(p.getKey());
        }

        //Sets the selection in the List to the correct value.
        public void selectKey(String panelString) {
            for (int i = 0; i < list.getModel().getSize(); i++) {
                if (list.getModel().getElementAt(i).equals(panelString)) {
                    list.getSelectionModel().setSelectionInterval(i, i);
                    break;
                }
            }
            showPanel((PreferencesPanel) (hash.get(panelString)));
        }

        // Draw the seperator line.
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(new Color(150, 150, 150));
            g.drawLine(10, YDEFAULT - 45, XDEFAULT - 20, YDEFAULT - 45);
            if (header.getFont().getSize() != 24) {
                header.setFont(new Font(getFont().getName(), Font.BOLD, 24));
            }
        }

        // Tells *panels* to save changes
        public  void save() {
            Enumeration enumeration = hash.elements();
            while (enumeration.hasMoreElements()) {
                ((PreferencesPanel) (enumeration.nextElement())).save();
            }
        }

        //Tells *panels* to refresh
        public void restore() {
            Enumeration enumeration = hash.elements();
            while (enumeration.hasMoreElements()) {
                ((PreferencesPanel) (enumeration.nextElement())).reset();
            }
        }

        //Adds a panel, duh.
        //Responsible for encapsulation all book-keeping required for adding a
        // panel.
        public void addPanel(PreferencesPanel p) {
            if (!Util.isEventDispatchThread())
                throw new IllegalStateException("Component not used o the event thread.");
            if (hash.get(p.getKey()) == null) {
                listModel.addElement(p.getKey());
                hash.put(p.getKey(), p);
                p.setLocation(0, 0);
                p.setSize(p.getPreferredSize());
                showerPanel.add(p);
                showerPanel.doLayout();//java VM bug.
                System.out.println("ADDING PANEL");
                selectKey(p.getKey());
            }
        }

        //Removes a panel, duh.
        //see above.
        public  void removePanel(String type) {
            if (hash.get(type) != null) {
                listModel.removeElement(type);

                PreferencesPanel pp_temp = (PreferencesPanel) (hash.get(type));
                if (pp_temp != null)
                    showerPanel.remove(pp_temp);
                hash.remove(type);
                if (list.getModel().getSize() != 0) {
                    list.getSelectionModel().setSelectionInterval(0, 0);
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