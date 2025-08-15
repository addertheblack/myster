package com.general.util;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultCaret;

import com.myster.application.MysterGlobals;

public class AskDialog extends JDialog {
    private static final String CANCEL_ACTION = "Cancel";
    private static final String OK_ACTION = "Ok";

    private List<JButton> buttons;
    private String it;
    private JTextField messagebox;
    private String msg;

    private final String question, sample;

    private AskDialog(Frame f, String q, String s) {
        super(f, "Ask Dialog", true);
        question = q;
        sample = s;

        initComponents();

        setResizable(false);
    }

    private void initComponents() {
        int padding = 10; // 10 pixels padding
        ((JComponent) getContentPane())
                .setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));


        setLayout(new BorderLayout());

        JTextArea textArea = MessagePanel.createNew(question);
        textArea.setColumns(question.length() > 200 ? 80 : 40);
        textArea.setCaret(new DefaultCaret() {
            @Override
            public void setVisible(boolean v) {
                super.setVisible(false); // Always keep the caret invisible
            }
        });

        pack();
        textArea.setFont(getFont());

        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        add(textArea, BorderLayout.NORTH);

        JPanel southPanel = new JPanel();

        southPanel.setLayout(new BorderLayout());

        messagebox = new JTextField(sample);
        JPanel justForLayout = new JPanel();
        justForLayout.setLayout(new BorderLayout());
        justForLayout.add(messagebox, BorderLayout.CENTER);
        justForLayout.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
        southPanel.add(justForLayout, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        buttons = new ArrayList<>(Arrays
                .asList(new JButton[] { new JButton(OK_ACTION), new JButton(CANCEL_ACTION) }));

        for (JButton b : buttons) {
            b.addActionListener(new ActionHandler());
        }
        getRootPane().setDefaultButton(buttons.get(0));

        if (MysterGlobals.ON_MAC) {
            Collections.reverse(buttons);
        }

        for (JButton b : buttons) {
            buttonPanel.add(b);
        }

        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);

        // Create an action to dispose of the dialog
        Action escapeAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                it = CANCEL_ACTION;
                AskDialog.this.dispose();
            }
        };

        // Get the root pane's input and action maps
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // Bind the escape key to the action
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE");
        actionMap.put("ESCAPE", escapeAction);

        pack();
        pack();

        Util.centerFrame(this, 0, 0);

        messagebox.requestFocus();
    }

    public String ask() {
        setVisible(true);
        return msg;
    }

    public String getIt() {
        return it;
    }

    public static String simpleAsk(String question) {
        return simpleAsk(question, "");
    }

    public static String simpleAsk(String question, String suggestedAnswer) {
        return (new AskDialog(AnswerDialog.getCenteredFrame(), question, suggestedAnswer)).ask();
    }

    public static String simpleAsk(Frame frame, String question, String suggestedAnswer) {
        return (new AskDialog(frame, question, suggestedAnswer)).ask();
    }

    private class ActionHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JButton b = ((JButton) (e.getSource()));

            it = b.getText();
            if (it.equals("Ok"))
                msg = messagebox.getText();
            dispose();
        }
    }


}