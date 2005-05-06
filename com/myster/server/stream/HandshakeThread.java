package com.myster.server.stream;

/*
 * 
 * Title: Myster Open Source Author: Andrew Trumper Description: Generic Myster
 * Code
 * 
 * This code is under GPL
 * 
 * Copyright Andrew Trumper 2000-2001
 */

import java.io.IOException;

import com.myster.filemanager.FileTypeListManager;
import com.myster.mml.MML;
import com.myster.pref.Preferences;
import com.myster.server.ConnectionContext;
import com.myster.server.ServerFacade;
import com.myster.type.MysterType;

public class HandshakeThread extends ServerThread {
    public static final int NUMBER = 101;

    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        context.socket.out.writeUTF("" + getMMLToSend());
    }

    //Returns an MML that would be send as a string via a Handshake.
    public static MML getMMLToSend() {
        try {
            MML mml = new MML();
            Preferences prefs;
            prefs = Preferences.getInstance();

            String tempstring = prefs.query(com.myster.MysterGlobals.SPEEDPATH);
            if (!(tempstring.equals(""))) {
                mml.put("/Speed", tempstring);
            }

            //mml.addMML(new MML(""+ftm.getNumberOfFiles(b),"NumberOfFiles"));

            mml.put("/Myster Version", "1.0");

            tempstring = prefs.query(com.myster.MysterGlobals.ADDRESSPATH);
            if (!(tempstring.equals(""))) { //If there is no value for the
                                            // address it doesn't send this
                                            // info.
                mml.put("/Address", tempstring); //Note: "" is no data. see
                                                 // qweryValue();
            }

            getNumberOfFilesMML(mml); //Adds the number of files data.

            String ident = ServerFacade.getIdentity();
            if (ident != null) {
                if (!ident.equals("")) {
                    mml.put("/ServerIdentity", ident);
                }
            }

            mml.put("/uptime", ""
                    + (System.currentTimeMillis() - com.myster.MysterGlobals
                            .getLaunchedTime()));

            return mml;
        } catch (Exception ex) {
            System.out.println("Error in getMMLtoSend()");
            ex.printStackTrace();
            return null;
        }

    }

    private static MML getNumberOfFilesMML(MML mml) { //in-line
        FileTypeListManager filemanager = FileTypeListManager.getInstance();

        MysterType[] filetypelist = filemanager.getFileTypeListing();
        String dir = "/numberOfFiles/";

        for (int i = 0; i < filetypelist.length; i++) {
            mml.put(dir + filetypelist[i], ""
                    + filemanager.getNumberOfFiles(filetypelist[i]));
        }

        return mml;
    }

}