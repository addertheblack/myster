package com.general.util;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Window;
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

    private final String theString;

    private AnswerDialog(Window f, String q, String... b) {
        super(resolveOwner(f), "Alert!", ModalityType.APPLICATION_MODAL);
        theString = q;

        String[] buttons = b.clone();
        if (b.length == 0) {
            buttons = new String[] { "Ok" };
        }

        initComponents(buttons);

        setResizable(true);
    }

    /** Shows an alert owned by the active window, or centered on the cursor's monitor. */
    public static String simpleAlert(String s) {
        return (new AnswerDialog((Window) null, s)).answer();
    }

    public static String simpleAlert(Window frame, String s) {
        return (new AnswerDialog(frame, s)).answer();
    }

    public static String simpleAlert(Window frame, String s, String[] b) {
        return (new AnswerDialog(frame, s, b)).answer();
    }

    /**
     * Creates a temporary frame centered on the cursor's monitor. The caller owns its disposal.
     * Prefer passing a real window to dialogs instead.
     */
    public static Frame getCenteredFrame() {
        Frame frame = new Frame(cursorScreen());
        frame.setSize(1, 1);
        centerOnCursorScreen(frame);
        frame.setTitle("Dialog Box!");
        return frame;
    }

    private static Window resolveOwner(Window owner) {
        return owner != null ? owner
                : KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    private static GraphicsConfiguration cursorScreen() {
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer != null) {
            for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getScreenDevices()) {
                GraphicsConfiguration configuration = screen.getDefaultConfiguration();
                if (configuration.getBounds().contains(pointer.getLocation())) {
                    return configuration;
                }
            }
            return pointer.getDevice().getDefaultConfiguration();
        }
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
    }

    private static void centerOnCursorScreen(Window window) {
        GraphicsConfiguration configuration = cursorScreen();
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        window.setLocation(bounds.x + Math.max(0, (bounds.width - window.getWidth()) / 2),
                bounds.y + Math.max(0, (bounds.height - window.getHeight()) / 2));
    }

    public AnswerDialog(Window f, String q) {
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

        Window owner = getOwner();
        if (owner != null && owner.isShowing()) {
            setLocationRelativeTo(owner);
        } else {
            centerOnCursorScreen(this);
        }
    }

    public String answer() {
        setVisible(true);
        return it;
    }

    public String getIt() {
        return it;
    }
}