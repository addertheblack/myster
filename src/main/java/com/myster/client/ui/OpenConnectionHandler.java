package com.myster.client.ui;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.ui.MysterFrameContext;

public class OpenConnectionHandler extends MCListEventAdapter {
    private final MysterFrameContext context;

    public OpenConnectionHandler(MysterFrameContext context) {
        this.context = context;
    }

    public void doubleClick(MCListEvent e) {
        (new ClientWindow(context, e.getParent().getItem(
                e.getParent().getSelectedIndex()).toString())).show();
    }

}