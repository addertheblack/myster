package com.general.util;

import java.awt.Color;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

/**
 * An awt base Label like component that does text wrapping.
 */

public class MessagePanel extends JTextArea {
    public static JTextArea createNew(String message) {
        var msg = new JTextArea(message) {
          @Override
        public void updateUI() {
            super.updateUI();
            setFont(new JTable().getFont());
        }  
            
        };
        msg.setWrapStyleWord(true);
        msg.setLineWrap(true);
        msg.setEditable(false);
        msg.setOpaque(false);
        msg.setBackground(new Color(0,0,0,0));
        msg.setBorder(new EmptyBorder(0, 0, 0, 0));
        msg.setCaret(new DefaultCaret() {
            @Override
            public void setVisible(boolean v) {
                super.setVisible(false); // Always keep the caret invisible
            }
        });
        return msg;
    }
}