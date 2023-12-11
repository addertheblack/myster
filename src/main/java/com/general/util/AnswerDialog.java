package com.general.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;

public class AnswerDialog extends JDialog {
    private JButton[] buttons;
    private String it; // just like HyperCard :-) You all know HyperCard, right?

    private final static int BUTTONY = 30;
    private final static int BUTTONX = 100;

    private final Frame parent;
    private final String theString;

    public AnswerDialog(Frame f, String q, String ... b) {
        super(f, "Alert!", true);
        theString = q;
        parent = f;

        String[] buttons = b.clone();
        if (b.length == 0) {
            buttons = new String[]{ "Ok" };
        }

        initComponents(buttons);
        
        setResizable(true);
    }

    public AnswerDialog(String q, String[] b) {
        this(getCenteredFrame(), q, b);
    }

    public static String simpleAlert(String s, String[] b) {
        return (new AnswerDialog(getCenteredFrame(), s, b)).answer();
    }

    public static String simpleAlert(String s) {
        return (new AnswerDialog(getCenteredFrame(), s)).answer();
    }

    public static String simpleAlert(Frame frame, String s) {
        return (new AnswerDialog(frame, s)).answer();
    }

    public static String simpleAlert(Frame frame, String s, String[] b) {
        return (new AnswerDialog(frame, s, b)).answer();
    }

    public static Frame getCenteredFrame() {
        Frame tempframe = new Frame();
        tempframe.setSize(0, 0);
        Toolkit tool = Toolkit.getDefaultToolkit();
        tempframe.setLocation(tool.getScreenSize().width / 2 - 200, tool
                .getScreenSize().height / 2 - 150);
        tempframe.setTitle("Dialog Box!");
        //tempframe.show();
        return tempframe;
    }
    
    public AnswerDialog(Frame f, String q) {
        this(f, q, new String[0]);
    }

    private void initComponents(String[] b) {
        setLayout(new BorderLayout());

        int length;
        if (b.length < 3)
            length = b.length;
        else
            length = 3; // ha haaa!

        JTextArea textArea = new JTextArea(theString, 0, theString.length() > 200 ? 80 : 40);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        
        pack(); // font is null if not packed
        textArea.setFont(getFont());
        textArea.setOpaque(false);
        add(textArea, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        buttons = new JButton[length];

        for (int i = 0; i < length; i++) {
            buttons[i] = new JButton(b[i]);
            buttons[i].addActionListener(new ActionHandler());
            buttons[i].setSize(BUTTONX, BUTTONY);
            buttons[i].setLocation(getSize().width - (120 * i + 20) - BUTTONX,
                    getSize().height - BUTTONY - 10 - 20 );//-1-insets.bottom);
            buttonPanel.add(buttons[i]);
            
            if (i == length -1 ) {
               getRootPane().setDefaultButton(buttons[i]);
            }
        }
        
        add(buttonPanel, BorderLayout.SOUTH);
        
        Dimension d = parent.getSize();
        Point l = parent.getLocation();

        Dimension mysize = getSize();

        setLocation(l.x + (d.width - mysize.width) / 2, l.y);
        
        // need to pack twice in order to get JTextArea to lay out correctly
        pack();
        pack();
    }

    public String answer() {
        setVisible(true);
        return it;
    }

    public String getIt() {
        return it;
    }

    private class ActionHandler implements ActionListener {
        public ActionHandler() {
        }

        public void actionPerformed(ActionEvent e) {
            JButton b = ((JButton) (e.getSource()));

            it = b.getLabel();
            dispose();
        }
    }
}