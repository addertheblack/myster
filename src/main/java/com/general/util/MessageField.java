/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.general.util;

import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.util.Optional;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * A status-bar label with two display modes: normal ({@link #say}) and error ({@link #sayError}).
 *
 * <p>Error mode sets the foreground to the FlatLaf semantic colour {@code "Actions.Red"} and
 * prepends a warning icon sized to the current font height (no layout reflow). The next call to
 * {@link #say} resets to the default theme foreground and removes the icon.
 */
public class MessageField extends JLabel {

    public MessageField(String s) {
        say(s);
    }

    /**
     * Displays a normal status message. Resets the foreground colour to the theme default
     * ({@code "Label.foreground"}) and clears any error icon set by a previous
     * {@link #sayError} call.
     *
     * @param s the status text to display
     */
    public void say(String s) {
        setForeground(UIManager.getColor("Label.foreground"));
        setIcon(null);
        setText("Status: " + s);
    }

    /**
     * Displays an error status message with a warning icon.
     *
     * <p>Foreground is set to {@code UIManager.getColor("Actions.Red")} — the FlatLaf semantic
     * key for the action-error red, automatically adjusted for the active theme (light, dark,
     * high-contrast). Falls back to {@code #DB5860} on non-FlatLaf look-and-feels.
     *
     * <p>A warning icon is loaded from the bundled {@code warning-icon.svg} (same package) and
     * sized to the current font height so no preferred-size change or layout reflow occurs.
     * FlatLaf automatically substitutes the magic hex {@code #DB5860} in the SVG with the
     * correct theme colour, keeping icon and text visually consistent.
     *
     * <p>Resets to normal appearance on the next call to {@link #say(String)}.
     *
     * @param s the error text to display
     */
    public void sayError(String s) {
        setForeground(Optional.ofNullable(UIManager.getColor("Actions.Red"))
                              .orElse(new Color(0xDB, 0x58, 0x60)));
        int h = getFontMetrics(getFont()).getHeight();
        try {
            FlatSVGIcon icon = IconLoader.loadSvg(MessageField.class, "warning-icon", h);
            setIcon(icon);
        } catch (Exception ignored) {
            setIcon(null);
        }
        setText(s);
    }
}