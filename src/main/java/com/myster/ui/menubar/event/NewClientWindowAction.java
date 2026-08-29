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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.Optional;

import com.general.util.AnswerDialog;
import com.myster.client.ui.ClientWindow;
import com.myster.net.MysterAddress;
import com.myster.tracker.MysterServer;
import com.myster.tracker.ui.ServerPickerDialog;
import com.myster.ui.MysterFrameContext;

public class NewClientWindowAction implements ActionListener {
    private final MysterFrameContext context;

    public NewClientWindowAction(MysterFrameContext context) {
        this.context = context;
    }

    public void actionPerformed(ActionEvent e) {
        Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        ServerPickerDialog dialog = new ServerPickerDialog(
                owner,
                context.knownServerSource(),
                "New Peer-to-Peer Connection",
                "Connect",
                server -> server.getBestAddress().isPresent(),
                "That server did not provide a usable address.");
        Optional<MysterServer> selected = dialog.showAndWait();
        if (selected.isEmpty()) {
            return;
        }
        Optional<MysterAddress> address = selected.get().getBestAddress();
        if (address.isEmpty()) {
            AnswerDialog.simpleAlert("That server no longer has a usable address.");
            return;
        }
        ClientWindow.ClientWindowData data =
                new ClientWindow.ClientWindowData(Optional.of(address.get().toString()),
                        Optional.empty(), Optional.empty());
        ClientWindow window = context.clientWindowProvider().getOrCreateWindow(data);
        window.show();
        window.toFrontAndUnminimize();
    }

}
