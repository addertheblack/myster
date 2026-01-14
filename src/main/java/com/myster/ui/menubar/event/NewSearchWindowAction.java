/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

package com.myster.ui.menubar.event;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.FocusManager;

import com.myster.search.ui.SearchWindow;
import com.myster.ui.MysterFrameContext;

/**
 * Handles "New Search" menu actions. Supports two modes:
 * <ul>
 *   <li>New Tab (Ctrl+N): Adds a new tab to an existing search window, or creates a new window if none exists</li>
 *   <li>New Window (Ctrl+Shift+N): Always creates a new search window</li>
 * </ul>
 */
public class NewSearchWindowAction implements ActionListener {
    private final MysterFrameContext context;
    private final boolean forceNewWindow;

    /**
     * Creates an action that adds a new tab to an existing window when possible.
     *
     * @param context the frame context
     */
    public NewSearchWindowAction(MysterFrameContext context) {
        this(context, false);
    }

    /**
     * Creates an action with explicit control over window creation.
     *
     * @param context the frame context
     * @param forceNewWindow if true, always creates a new window; if false, adds a tab to existing window when possible
     */
    public NewSearchWindowAction(MysterFrameContext context, boolean forceNewWindow) {
        this.context = context;
        this.forceNewWindow = forceNewWindow;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (forceNewWindow) {
            // Always create a new window
            createNewWindow();
            return;
        }

        // Try to find an existing search window to add a tab to
        List<SearchWindow> activeWindows = SearchWindow.getActiveWindows();

        if (activeWindows.isEmpty()) {
            // No windows exist, create one
            createNewWindow();
            return;
        }

        // Check if the currently focused window is a SearchWindow
        Window focusedWindow = FocusManager.getCurrentManager().getFocusedWindow();
        if (focusedWindow instanceof SearchWindow searchWindow) {
            // Add a new tab to the focused search window
            searchWindow.addNewTab();
            searchWindow.toFront();
            return;
        }

        // No search window is focused, use the first active one
        SearchWindow window = activeWindows.getFirst();
        window.addNewTab();
        window.setVisible(true);
        window.toFront();
    }

    private void createNewWindow() {
        SearchWindow window = new SearchWindow(context);
        window.setVisible(true);
    }
}
