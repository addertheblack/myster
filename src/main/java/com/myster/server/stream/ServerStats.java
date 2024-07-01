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

public class ServerStats extends ServerStreamHandler {
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
        MML mmlToSend;
        try {
            mmlToSend = getMMLToSend(getServerName.get(), getPort.get(), identity);
            context.socket.out.writeUTF("" + mmlToSend);
        } catch (NotInitializedException exception) {
            throw new IOException("File list not initialized");
        }
    }

    //Returns an MML that would be send as a string via a Handshake.
    public static MML getMMLToSend(String identityName, int port, Identity identity) throws NotInitializedException {
        try {
            MML mml = new MML();

            MysterPreferences prefs = MysterPreferences.getInstance();

            String tempstring = prefs.query(com.myster.application.MysterGlobals.SPEEDPATH);
            if (!(tempstring.equals(""))) {
                mml.put(SPEED, tempstring);
            }

            mml.put(MYSTER_VERSION, "1.0");

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
        } catch (NotInitializedException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalStateException(ex);
        }

    }

    private static MML getNumberOfFilesMML(MML mml) throws NotInitializedException { // in-line
        FileTypeListManager filemanager = FileTypeListManager.getInstance();

        MysterType[] filetypelist = filemanager.getFileTypeListing();

        for (int i = 0; i < filetypelist.length; i++) {
            if (!filemanager.hasInitialized(filetypelist[i])) {
                throw new NotInitializedException(filetypelist[i]);
            }
            
            mml.put(NUMBER_OF_FILES + filetypelist[i],
                    "" + filemanager.getNumberOfFiles(filetypelist[i]));
        }

        return mml;
    }

}