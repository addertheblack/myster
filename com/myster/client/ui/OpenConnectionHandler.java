package com.myster.client.ui;

import com.general.mclist.MCListEvent;
import com.general.mclist.MCListEventAdapter;

public class OpenConnectionHandler extends MCListEventAdapter {

    public OpenConnectionHandler() {
    }

    public void doubleClick(MCListEvent e) {
        (new ClientWindow(e.getParent().getItem(
                e.getParent().getSelectedIndex()).toString())).show();
    }

}