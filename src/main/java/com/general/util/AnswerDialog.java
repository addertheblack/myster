package com.general.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultCaret;

import com.myster.application.MysterGlobals;

public class AnswerDialog extends JDialog {
    protected static final String CANCEL_ACTION = "Cancel";

    private List<JButton> buttons;
    private String it; // just like HyperCard :-) You all know HyperCard, right?

    private final Frame parent;
    private final String theString;

    private AnswerDialog(Frame f, String q, String... b) {
        super(f, "Alert!", true);
        theString = q;
        parent = f;

        String[] buttons = b.clone();
        if (b.length == 0) {
            buttons = new String[] { "Ok" };
        }

        initComponents(buttons);

        setResizable(true);
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
        tempframe.setSize(1, 1);
        Toolkit tool = Toolkit.getDefaultToolkit();
        tempframe.setLocation(tool.getScreenSize().width / 2,
                              tool.getScreenSize().height / 2 - 150);
        tempframe.setTitle("Dialog Box!");
        // tempframe.show();
        return tempframe;
    }

    public AnswerDialog(Frame f, String q) {
        this(f, q, new String[0]);
    }

    private void initComponents(String[] buttonNames) {
        int padding = 10; // 10 pixels padding
        ((JComponent) getContentPane())
                .setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));

        if (buttonNames.length > 3) {
            throw new IllegalStateException("Can only have 3 buttons max, got "
                    + buttonNames.length);
        }

        setLayout(new BorderLayout());

        JTextArea textArea = MessagePanel.createNew(theString);
        textArea.setColumns(theString.length() > 200 ? 80 : 40);
        textArea.setCaret(new DefaultCaret() {
            @Override
            public void setVisible(boolean v) {
                super.setVisible(false); // Always keep the caret invisible
            }
        });


        pack(); // font is null if not packed
        textArea.setFont(getFont());
        textArea.setOpaque(false);
        add(textArea, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        buttons = new ArrayList<>();

        for (String buttonName : buttonNames) {
            JButton button = new JButton(buttonName);
            button.addActionListener((e) -> {
                JButton b = ((JButton) (e.getSource()));

                it = b.getText();
                dispose();
            });
            buttons.add(button);
        }
        getRootPane().setDefaultButton(buttons.get(0));

        if (MysterGlobals.ON_MAC) {
            Collections.reverse(buttons);
        }

        for (JButton b : buttons) {
            buttonPanel.add(b);
        }

        add(buttonPanel, BorderLayout.SOUTH);

        // Create an action to dispose of the dialog
        Action escapeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                it = CANCEL_ACTION;
                AnswerDialog.this.dispose();
            }
        };

        // Get the root pane's input and action maps
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // Bind the escape key to the action
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE");
        actionMap.put("ESCAPE", escapeAction);

        // need to pack twice in order to get JTextArea to lay out correctly
        pack();
        pack();

        Dimension d = parent.getSize();
        Point l = parent.getLocation();

        Dimension mysize = getSize();

        setLocation(l.x + (d.width / 2) - (mysize.width / 2), l.y);
    }

    public String answer() {
        setVisible(true);
        return it;
    }

    public String getIt() {
        return it;
    }
}