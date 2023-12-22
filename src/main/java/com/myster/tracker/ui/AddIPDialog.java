/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

package com.myster.tracker.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.UnknownHostException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;

import com.myster.net.MysterAddress;
import com.myster.tracker.IpListManager;

/**
 * Implements the addIP dialog box.
 *  
 */

public class AddIPDialog extends JDialog {
    private static final int XDEFAULT = 300;
    private static final int YDEFAULT = 100;
    
    private final GridBagLayout gblayout;
    private final GridBagConstraints gbconstrains;
    private final JLabel speed;
    private final JLabel explanation;
    private final JTextField textentry;
    private final JButton ok;
    private final IpListManager ipListManager;

    public AddIPDialog(IpListManager ipListManager) {
        super(com.myster.ui.WindowManager.getFrontMostWindow(), "Add IP", true);
        
        this.ipListManager = ipListManager;
        
        //Do interface setup:
        gblayout = new GridBagLayout();
        setLayout(gblayout);
        gbconstrains = new GridBagConstraints();
        gbconstrains.fill = GridBagConstraints.BOTH;
        gbconstrains.ipadx = 1;
        gbconstrains.ipady = 1;

        speed = new JLabel("IP to add to IP lists?");

        textentry = new JTextField("Enter an IP here");

        // suitably good Myster Server. This option is best used when using
        // Myster for the first few times. It Can be used to add a entry point
        // to the Myster network to your IP list. It can also be used on an
        // intranet to add a server to be searched in a peer-to-peer fashion.");
        explanation = new JLabel("The IP will be added to your IP list");

        ok = new JButton("OK");

        setBounds(0, 0, XDEFAULT, YDEFAULT);

        addComponent(speed, 0, 0, 1, 1, 0, 0);
        addComponent(textentry, 0, 1, 1, 1, 99, 0);
        addComponent(explanation, 1, 0, 2, 1, 99, 0);
        addComponent(ok, 2, 0, 1, 1, 0, 0);

        setResizable(false);
        setSize(XDEFAULT, YDEFAULT);

        ok.addActionListener(new AddIPAction(this));
        addWindowListener(new com.general.util.StandardWindowBehavior());
    }

    private void addComponent(Component c, int row, int column, int width,
            int height, int weightx, int weighty) {
        gbconstrains.gridx = column;
        gbconstrains.gridy = row;

        gbconstrains.gridwidth = width;
        gbconstrains.gridheight = height;

        gbconstrains.weightx = weightx;
        gbconstrains.weighty = weighty;

        gblayout.setConstraints(c, gbconstrains);

        add(c);

    }

    /**
     * the doAction routine is invoked when the user clicks the ok button. In
     * the ADDIPDislog, the routine sends the IP to the MysterIPListManager or
     * addition to ALL IPLIsts being maintained by myster.
     */
    public void doAction() {
        try {
            ipListManager.addIP(
                    new MysterAddress(textentry.getText()));
        } catch (UnknownHostException ex) {
            System.out.println("The \"Name\" : " + textentry.getText()
                    + " is not a valid domain name at all!");
        }
    }

    private static class AddIPAction implements ActionListener {
        private final AddIPDialog a;

        public AddIPAction(AddIPDialog a) {
            this.a = a;
        }

        public void actionPerformed(ActionEvent event) {
            a.setVisible(false);
            a.doAction();
        }
    }
}