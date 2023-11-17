package com.general.util;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;

public class AnswerDialog extends JDialog {
    private JButton[] buttons;
    private String it; //just like HyperCard :-) You all know HyperCard, right?
    private int height;
    private int ascent;
    private FontMetrics metrics;

    private final static int BUTTONY = 30;
    private final static int BUTTONX = 100;

    private final List<String> messages = new ArrayList<>();
    private final Insets insets;
    private final Frame parent;
    private final String theString;

    public AnswerDialog(Frame f, String q, String ... b) {
        super(f, "Alert!", true);
        theString = q;
        parent = f;
        pack();
        insets = getInsets();

        String[] buttons = b.clone();
        if (b.length == 0) {
            buttons = new String[]{ "Ok" };
        }

        initComponents(buttons);
        
        setResizable(false);
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
        int length;

        setLayout(null);

        if (b.length < 3)
            length = b.length;
        else
            length = 3; //ha haaa!

        doMessageSetup(theString);
        setSize(400 + insets.right + insets.left, messages.size() * height
                + ascent + 5 + BUTTONY + 20 + insets.top + insets.bottom);

        buttons = new JButton[length];

        for (int i = 0; i < length; i++) {
            buttons[i] = new JButton(b[i]);
            buttons[i].addActionListener(new ActionHandler());
            buttons[i].setSize(BUTTONX, BUTTONY);
            buttons[i].setLocation(getSize().width - (120 * i + 20) - BUTTONX,
                    getSize().height - BUTTONY - 10 - insets.bottom);//-1-insets.bottom);
            add(buttons[i]);
        }

        Dimension d = parent.getSize();
        Point l = parent.getLocation();

        Dimension mysize = getSize();

        setLocation(l.x + (d.width - mysize.width) / 2, l.y);
    }

    private void doMessageSetup(String q) {
        metrics = getFontMetrics(getFont());

        height = metrics.getHeight();
        ascent = metrics.getAscent();

        MrWrap wrapper = new MrWrap(q, 380, metrics);
        for (int i = 0; i < wrapper.numberOfElements(); i++) {
            messages.add(wrapper.nextElement());
        }

        resetLocation();
    }

    private void resetLocation() {
        /*
         * return; insets=getInsets();
         * 
         * for (int i=0; i <buttons.length; i++) {
         * buttons[i].setLocation(getSize().width-(120*i+20)-BUTTONX,
         * getSize().height-BUTTONY-20-insets.bottom); }
         * 
         * setSize(400, message.size()*height+75+insets.top+insets.bottom);
         */
    }

    public void paint(Graphics g) {
        g.setColor(Color.black);
        for (int i = 0; i < messages.size(); i++) {
            g.drawString(messages.get(i).toString(), 10, 5 + height * (i)
                    + ascent + insets.top);
        }
    }

    public String answer() {
        setVisible(false);
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