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
import java.net.UnknownHostException;
import java.util.Optional;

import com.general.thread.PromiseFutures;
import com.general.util.AnswerDialog;
import com.general.util.AskDialog;
import com.myster.client.ui.ClientWindow;
import com.myster.net.MysterAddress;
import com.myster.ui.MysterFrameContext;

public class NewClientWindowAction implements ActionListener {
    private final MysterFrameContext context;

    public NewClientWindowAction(MysterFrameContext context) {
        this.context = context;
    }

    public void actionPerformed(ActionEvent e) {
        String addressString = AskDialog.simpleAsk("Enter the server address to connect to:");

        if (addressString == null || addressString.trim().isEmpty()) {
            return; // User cancelled
        }

        PromiseFutures.execute(() -> MysterAddress.createMysterAddress(addressString.trim()))
                .useEdt()
                .addExceptionListener(ex -> AnswerDialog.simpleAlert("Invalid server address: " + addressString + "\n\n" + ex.getMessage()))
                .addResultListener(address -> {
                    ClientWindow.ClientWindowData data =
                            new ClientWindow.ClientWindowData(Optional.of(address.toString()),
                                    Optional.empty(),
                                    Optional.empty());
                    ClientWindow w = context.clientWindowProvider().getOrCreateWindow(data);
                    w.show();
                    w.toFrontAndUnminimize();
                });
    }

}