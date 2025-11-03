package com.myster.net.stream.server;

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
import com.myster.mml.MessagePack;
import com.myster.net.server.ConnectionContext;
import com.myster.pref.MysterPreferences;
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
        MessagePack messagePackToSend;
        try {
            messagePackToSend = getServerStatsMessagePack(getServerName.get(), getPort.get(), identity, context.fileManager());
            context.socket().out.writeMessagePack(messagePackToSend);
        } catch (NotInitializedException _) {
            throw new IOException("File list not initialized");
        }
    }

    //Returns a MessagePack that would be sent as bytes via a connection.
    public static MessagePack getServerStatsMessagePack(String serverName, int port, Identity identity, FileTypeListManager fileManager) throws NotInitializedException {
        try {
            MessagePack serverStats = MessagePack.newEmpty();
            

            MysterPreferences prefs = MysterPreferences.getInstance();

            String tempstring = prefs.query(com.myster.application.MysterGlobals.SPEEDPATH);
            if (!(tempstring.equals(""))) {
                serverStats.putString(SPEED, tempstring);
            }

            serverStats.putString(MYSTER_VERSION, "1.0");

            getNumberOfFilesMessagePack(serverStats, fileManager); //Adds the number of files data.

            String ident = serverName;
            if (ident != null) {
                if (!ident.equals("")) {
                    serverStats.putString(SERVER_NAME, ident);
                }
            }
            
             identity.getMainIdentity().ifPresent(pair -> {
                 var publicKey = pair.getPublic();
                 
                 serverStats.putByteArray(IDENTITY, publicKey.getEncoded());
             });
             
            

            serverStats.putLong(UPTIME,  (System.currentTimeMillis() - com.myster.application.MysterGlobals
                            .getLaunchedTime()));

            serverStats.putInt(PORT, port);

            return serverStats;
        } catch (NotInitializedException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new IllegalStateException(ex);
        }

    }

    private static MessagePack getNumberOfFilesMessagePack(MessagePack numOfFileStats, FileTypeListManager fileManager) throws NotInitializedException { // in-line
        MysterType[] filetypelist = fileManager.getFileTypeListing();

        for (int i = 0; i < filetypelist.length; i++) {
            if (!fileManager.hasInitialized(filetypelist[i])) {
                throw new NotInitializedException("File list not inited", filetypelist[i]);
            }

            numOfFileStats.putInt(NUMBER_OF_FILES + filetypelist[i],
                       fileManager.getNumberOfFiles(filetypelist[i]));
        }

        return numOfFileStats;
    }

}