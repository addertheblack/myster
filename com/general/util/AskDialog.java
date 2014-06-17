package com.general.util;

import java.awt.Button;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

public class AskDialog extends Dialog {
    Button[] buttons;

    String it; //just like hypercard :-)

    Vector message = new Vector(10, 10);

    TextField messagebox;

    int height;

    FontMetrics metrics;

    Insets insets;

    private static final int XPAD = 5;

    private static final int YPAD = 5;

    private static final int XSIZE = 400;

    private static final int BUTTONY = 30;

    private static final int BUTTONX = 100;

    private static final int MSIZEX = XSIZE - XPAD - XPAD;

    private static final int MSIZEY = 35;

    Frame parent;

    String question, sample;

    public AskDialog(Frame f, String q, String s) {
        super(f, "Ask!", true);
        question = q;
        sample = s;
        parent = f;

        initComponents();

        setResizable(false);
    }

    public AskDialog(Frame f, String q) {
        this(f, q, "");
    }

    private void initComponents() {
        setLayout(null);
        pack();
        insets = getInsets();
        doMessageSetup();
        setSize(getPreferredSize());
        setLayout(null);

        messagebox = new TextField(sample);
        messagebox.setSize(MSIZEX, MSIZEY);
        messagebox.setLocation(XPAD + insets.left, message.size() * height
                + YPAD + insets.top + 10);
        add(messagebox);

        buttons = new Button[] { new Button("Cancel"), new Button("Ok") };

        for (int i = 0; i < buttons.length; i++) {
            buttons[i].addActionListener(new ActionHandler());
            buttons[i].setSize(BUTTONX, BUTTONY);
            buttons[i].setLocation(getSize().width - (120 * i + 20) - BUTTONX,
                    getSize().height - BUTTONY - 20 - insets.bottom);
            add(buttons[i]);
        }

        Dimension d = parent.getSize();
        Point l = parent.getLocation();
        Dimension mysize = getSize();
        setLocation(l.x + (d.width - mysize.width) / 2, l.y);

        setSize(getPreferredSize());

    }

    private void doMessageSetup() {
        metrics = getFontMetrics(getFont());

        height = metrics.getHeight();

        MrWrap wrapper = new MrWrap(question, XSIZE - 2 * XPAD, metrics);
        for (int i = 0; i < wrapper.numberOfElements(); i++) {
            message.addElement(wrapper.nextElement());
        }

    }

    public Dimension getPreferredSize() {
        insets = getInsets();

        return new Dimension(400 + insets.right + insets.left, message.size()
                * height + 75 + MSIZEY + 2 * YPAD + insets.top + insets.bottom);
    }

    public void paint(Graphics g) {
        g.setColor(Color.black);
        for (int i = 0; i < message.size(); i++) {
            g.drawString(message.elementAt(i).toString(), XPAD + insets.left,
                    YPAD + height * (i + 1) + insets.top);
        }

    }

    String msg;

    public String ask() {
        show();
        return msg;
    }

    public String getIt() {
        return msg;
    }

    public static String simpleAsk(String question) {
        return simpleAsk(question, "");
    }

    public static String simpleAsk(String question, String suggestedAnswer) {
        return (new AskDialog(AnswerDialog.getCenteredFrame(), question,
                suggestedAnswer)).ask();
    }

    private class ActionHandler implements ActionListener {

        public ActionHandler() {
        }

        public void actionPerformed(ActionEvent e) {
            Button b = ((Button) (e.getSource()));

            it = b.getLabel();
            if (it.equals("Ok"))
                msg = messagebox.getText();
            dispose();
        }

    }

}