package com.myster.tracker.ui;

import java.util.Optional;

import javax.swing.SwingUtilities;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterIdentity;
import com.myster.tracker.PublicKeyIdentity;
import com.myster.tracker.ui.TrackerWindow.TrackerMCListItem;
import com.myster.type.MysterType;
import com.myster.ui.MysterFrameContext;

public class OpenConnectionHandler extends MCListEventAdapter {
    private final MysterFrameContext context;

    public OpenConnectionHandler(MysterFrameContext context) {
        this.context = context;
    }

    public void doubleClick(MCListEvent e) {
        TrackerMCListItem item = (TrackerMCListItem) e.getParent().getMCListItem(e.getParent().getSelectedIndex());
        MysterAddress serverIp = item.getBestAddress();
        MysterType type = ((TrackerWindow) SwingUtilities.getWindowAncestor(e.getParent().getPane())).getMysterType()
                                                                                                     .orElse(null);
        context.clientWindowProvider()
               .openWindow(serverIp,
                           MysterIdentity.extractPublicKey(item.getObject().getIdentity()).map(PublicKeyIdentity::new),
                           Optional.ofNullable(type),
                           Optional.empty());
    }

}
