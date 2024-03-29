package com.myster.client.ui;

import javax.swing.SwingUtilities;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.tracker.ui.TrackerWindow;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public class OpenConnectionHandler extends MCListEventAdapter {
    private final MysterFrameContext context;

    public OpenConnectionHandler(MysterFrameContext context) {
        this.context = context;
    }

    public void doubleClick(MCListEvent e) {
        String serverIp =  e.getParent().getItem(e.getParent().getSelectedIndex()).toString();
        MysterType type = ((TrackerWindow) SwingUtilities.getWindowAncestor(e.getParent().getPane())).getMysterType();
        (new ClientWindow(context, serverIp, type)).show();
    }

}