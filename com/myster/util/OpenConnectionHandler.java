package com.myster.util;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;
import com.myster.client.ui.ClientWindow;

public class OpenConnectionHandler extends MCListEventAdapter {

    public OpenConnectionHandler() {
    }

    public void doubleClick(MCListEvent e) {
        (new ClientWindow(e.getParent().getItem(
                e.getParent().getSelectedIndex()).toString())).show();
    }

}