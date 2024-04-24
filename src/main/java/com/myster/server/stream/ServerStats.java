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
import java.util.Base64;
import java.util.function.Supplier;

import com.myster.filemanager.FileTypeListManager;
import com.myster.identity.Identity;
import com.myster.mml.MML;
import com.myster.pref.MysterPreferences;
import com.myster.server.ConnectionContext;
import com.myster.type.MysterType;

public class ServerStats extends ServerThread {
    public static final int NUMBER = 101;
    
    public static final String NUMBER_OF_FILES = "/NumberOfFiles/";
    
    public static final String MYSTER_VERSION = "/MysterVersion";
    public static final String SPEED = "/Speed";
    public static final String SERVER_NAME = "/ServerName";
    public static final String IDENTITY = "/Identity";
    public static final String UPTIME = "/Uptime";
    public static final String PORT = "/Port";
    
    private final Supplier<String> getServerName;
    private final Identity identity;
    private final Supplier<Integer> getPort;


    public ServerStats(Supplier<String> getServerName, Supplier<Integer> getPort, Identity identity) {
        this.getServerName = getServerName;
        this.getPort = getPort;
        this.identity = identity;
    }
    
    public int getSectionNumber() {
        return NUMBER;
    }

    public void section(ConnectionContext context) throws IOException {
        context.socket.out.writeUTF("" + getMMLToSend(getServerName.get(), getPort.get(), identity));
    }

    //Returns an MML that would be send as a string via a Handshake.
    public static MML getMMLToSend(String identityName, int port, Identity identity) {
        try {
            MML mml = new MML();
            MysterPreferences prefs;
            prefs = MysterPreferences.getInstance();

            String tempstring = prefs.query(com.myster.application.MysterGlobals.SPEEDPATH);
            if (!(tempstring.equals(""))) {
                mml.put(SPEED, tempstring);
            }

            mml.put(MYSTER_VERSION, "1.0");

//            tempstring = prefs.query(com.myster.application.MysterGlobals.ADDRESSPATH);
//            if (!(tempstring.equals(""))) { // If there is no value for the
//                                            // address it doesn't send this
//                                            // info.
//                mml.put(ADDRESS, tempstring); //Note: "" is no data. see qweryValue();
//            }
            
//            prefs.query(com.myster.application.MysterGlobals.DEFAULT_SERVER_PORT);

            getNumberOfFilesMML(mml); //Adds the number of files data.

            String ident = identityName;
            if (ident != null) {
                if (!ident.equals("")) {
                    mml.put(SERVER_NAME, ident);
                }
            }
            
             identity.getMainIdentity().ifPresent(pair -> {
                 var publicKey = pair.getPublic();
                 
                 mml.put(IDENTITY, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
             });
             
            

            mml.put(UPTIME, ""
                    + (System.currentTimeMillis() - com.myster.application.MysterGlobals
                            .getLaunchedTime()));
            
            mml.put(PORT, "" + port);

            return mml;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }

    private static MML getNumberOfFilesMML(MML mml) { // in-line
        FileTypeListManager filemanager = FileTypeListManager.getInstance();

        MysterType[] filetypelist = filemanager.getFileTypeListing();

        for (int i = 0; i < filetypelist.length; i++) {
            mml.put(NUMBER_OF_FILES + filetypelist[i],
                    "" + filemanager.getNumberOfFiles(filetypelist[i]));
        }

        return mml;
    }

}