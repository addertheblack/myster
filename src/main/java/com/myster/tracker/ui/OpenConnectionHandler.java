package com.myster.tracker.ui;

import java.util.Optional;

import javax.swing.SwingUtilities;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.client.ui.ClientWindow;
import com.myster.tracker.ui.TrackerWindow.TrackerMCListItem;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public class OpenConnectionHandler extends MCListEventAdapter {
    private final MysterFrameContext context;

    public OpenConnectionHandler(MysterFrameContext context) {
        this.context = context;
    }

    public void doubleClick(MCListEvent e) {
        String serverIp =  ((TrackerMCListItem)e.getParent().getMCListItem(e.getParent().getSelectedIndex())).getBestAddress().toString();
        MysterType type = ((TrackerWindow) SwingUtilities.getWindowAncestor(e.getParent().getPane())).getMysterType().orElse(null);
        var data = new ClientWindow.ClientWindowData(Optional.of(serverIp), Optional.ofNullable(type), Optional.empty());
        context.clientWindowProvider().getOrCreateWindow(data).show();
    }

}