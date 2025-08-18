/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.pref.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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

import com.general.util.GridBagBuilder;
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

        getContentPane().add(mypanel, BorderLayout.CENTER);
        
        getRootPane().setDefaultButton(mypanel.save);

        setSize(mypanel.getPreferredSize());
        
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
            setLayout(new GridBagLayout());
            //setBackground(new Color(255,255,0));
            
            GridBagBuilder layout = new GridBagBuilder();
            layout = layout.withSize(1, 1);

            list = new JList();
          
            listModel = new DefaultListModel();
            list.setModel(listModel);
            JScrollPane listScrollPane = new JScrollPane(list);
//            listScrollPane.setSize(150 - 5 - 5, YDEFAULT - 50 - 5);
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
            add(listScrollPane,
                layout.withGridLoc(0, 0)
                        .withFill(GridBagConstraints.BOTH)
                        .withSize(1, 2)
                        .withWeight(0, 1)
                        .withInsets(new Insets(5, 5, 5, 5)));

            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new GridBagLayout());
            GridBagBuilder buttonLayout = new GridBagBuilder();
            
            apply = new JButton("Apply");
            apply.setLocation(XDEFAULT - 5 - 100, YDEFAULT - 30 - 7);
            apply.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    save();
                }
            });
            buttonPanel.add(apply, buttonLayout.withGridLoc(3, 0));

            save = new JButton("    OK    ");
            save.setLocation(XDEFAULT - 100 - 5 - 5 - 100 - 5 - 100 - 5, YDEFAULT - 30 - 7);
            save.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    save();
                    PreferencesDialogBox.this.setVisible(false);
                }
            });
            buttonPanel.add(save, buttonLayout.withGridLoc(1, 0).withInsets(new Insets(0, 0, 0, 5)));

            revert = new JButton("Cancel");
            revert.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    restore();
                    PreferencesDialogBox.this.setVisible(false);
                }
            });
            buttonPanel.add(revert, buttonLayout.withGridLoc(2, 0).withInsets(new Insets(0, 0, 0, 10)));

            buttonPanel.add(new JPanel(),
                            buttonLayout.withGridLoc(0, 0)
                                    .withFill(GridBagConstraints.HORIZONTAL)
                                    .withWeight(99, 0));

            JPanel line = new JPanel();
            line.setBackground(Color.gray);
            line.setPreferredSize(new Dimension(1, 1));
            add(line,
                layout.withGridLoc(0, 2)
                        .withFill(GridBagConstraints.HORIZONTAL)
                        .withSize(2, 1)
                        .withWeight(1, 0)
                        .withInsets(new Insets(5, 5, 5, 5)));

            add(buttonPanel,
                layout.withGridLoc(0, 3)
                        .withSize(2, 1)
                        .withFill(GridBagConstraints.HORIZONTAL)
                        .withInsets(new Insets(5, 5, 5, 5)));

            showerPanel = new JPanel();
            showerPanel.setLayout(new GridBagLayout());
            showerPanel.setPreferredSize(new Dimension(1,1));
            add(showerPanel,
                layout.withGridLoc(1, 1)
                        .withFill(GridBagConstraints.BOTH)
                        .withWeight(1, 1)
                        .withInsets(new Insets(5, 5, 5, 5)));

            header = new JLabel("") {
                public Dimension getPreferredSize() {
                    var d = super.getPreferredSize();
                    d.width = 1;
                    return d;
                }
            };
            header.setFont(header.getFont().deriveFont(24f));
            header.setBackground(new Color(225, 225, 225));
            add(header, 
                layout.withGridLoc(1, 0)
                        .withFill(GridBagConstraints.HORIZONTAL)
                        .withWeight(1, 0)
                        .withInsets(new Insets(5, 5, 5, 5)));
        }

        // Hide the currently showing panel and shows the new one.
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

        // Tells *panels* to save changes
        public void save() {
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
                showerPanel.add(p, new GridBagBuilder().withFill(GridBagConstraints.BOTH).withWeight(1, 1));
                showerPanel.doLayout();//java VM bug.
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

        public Dimension getPreferredSize() {
            return new Dimension(XDEFAULT, YDEFAULT);
        }

    }
}