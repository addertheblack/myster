package com.general.util;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

/**
 * An awt base Label like component that does text wrapping.
 */

public class MessagePanel extends JPanel {
    private int height;

    private int ascent;

    private FontMetrics metrics;

    private final String message;

    private final List<String> messageVector = new ArrayList<>(20);

    public MessagePanel(String message) {
        this.message = message;
    }

    public java.awt.Dimension getPreferredSize() {
        return getSize();
    }

    private void doMessageSetup() {
        metrics = getFontMetrics(getFont());

        height = metrics.getHeight();
        ascent = metrics.getAscent();

        MrWrap wrapper = new MrWrap(message, 380, metrics);
        for (int i = 0; i < wrapper.numberOfElements(); i++) {
            messageVector.add(wrapper.nextElement());
        }
    }

    public void paint(Graphics g) {
        if (metrics == null)
            doMessageSetup();
        g.setColor(Color.black);
        for (int i = 0; i < messageVector.size(); i++) {
            g.drawString(messageVector.get(i).toString(), 10, 5 + height
                    * (i) + ascent);
        }
    }
}