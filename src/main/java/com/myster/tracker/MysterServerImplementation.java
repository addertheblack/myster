/* 

 Title:			Myster Open Source
 Author:		Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2024

 */

package com.myster.tracker;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.general.util.Util;
import com.myster.application.MysterGlobals;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.MessagePak;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.net.stream.server.ServerStats;
import com.myster.type.MysterType;

/**
 * MysterIP objects are responsible for two things. 1) Saving server statistics information and 2)
 * keeping this information up to date.
 */

class MysterServerImplementation {
    private static final Logger log = Logger.getLogger("com.myster.tracker.MysterServer");
    
    private final MysterIdentity identity;
    
    private double speed;
    private int timeup;
    private int timedown;
    private NumOfFiles numberOfFiles;
    private int numberofhits;
    private String serverName;
    private long uptime;

    // time internalRefreshStatus() was last
    // called.

    private long timeoflastupdate = 0;

    //These are the paths in the MML peer:
    //ip = root!
    public static final String ADDRESSES = "ipAddresses";
    public static final String SPEED = "speed";
    public static final String TIMESINCEUPDATE = "timeSinceUpdate";
    public static final String TIMEUP = "timeUp";
    public static final String TIMEDOWN = "timeDown";
    public static final String NUMBEROFHITS = "numberOfHits";
    public static final String NUMBEROFFILES = "numberOfFiles";
    public static final String SERVER_NAME = "serverName";
    public static final String UPTIME = "uptime";
    
    // value pointed to by this key might be null in which case use IDENTITY_ADDRESS
    public static final String IDENTITY_PUBLIC_KEY = "identity";

    //These are weights.
    private static final double SPEEDCONSTANT = 0.5;
    private static final double FILESCONSTANT = 0.5;
    private static final double HITSCONSTANT = 0.25;
//    private static final double UPVSDOWNCONSTANT = 5;
    private static final double STATUSCONSTANT = 1;


    private final Preferences preferences;

    private final IdentityProvider identityProvider;

    MysterServerImplementation(Preferences node,
                               IdentityProvider identityProvider,
                               MysterIdentity identity) {
        this.preferences = node;
        this.identityProvider = identityProvider;
        this.identity = identity;
        createNewMysterServerImpl(Double.valueOf(node.get(SPEED, "")),
                                  Integer.valueOf(node.get(TIMEUP, "")),
                                  Integer.valueOf(node.get(TIMEDOWN, "")),
                                  Integer.valueOf(node.get(NUMBEROFHITS, "")),
                                  Long.valueOf(node.get(TIMESINCEUPDATE, "")),
                                  node.get(NUMBEROFFILES, ""),
                                  node.get(SERVER_NAME, ""),
                                  (node.get(UPTIME, "") == null ? -1
                                        : Long.valueOf(node.get(UPTIME, ""))));
    }
    
    /**
     * Refreshes all Stats (Number of files, Speed and upordown) from the IP.
     * Blocks.
     * 
     * @param address
     */
    MysterServerImplementation(Preferences prefs,
                               IdentityProvider addressProvider,
                               MessagePak serverStats,
                               MysterIdentity identity,
                               MysterAddress address) {
        preferences = prefs;
        this.identity = identity;
        this.identityProvider = addressProvider;

        refreshStats(this, serverStats, address);
    }

    static ExternalName computeNodeNameFromIdentity(MysterIdentity key) {
        return new ExternalName(Util.getMD5Hash(key.toString()));
    }
    
    void refreshStats(MessagePak serverStats, MysterAddress address) {
        refreshStats(this, serverStats, address);
    }

    public static MysterAddress extractCorrectedAddress(MessagePak serverStats,
                                                        MysterAddress addressIn) {
        int port = extractPort(serverStats);

        return port == addressIn.getPort() ? addressIn
                : new MysterAddress(addressIn.getInetAddress(), port);
    }
    
    private void refreshStats(MysterServerImplementation server, MessagePak serverStats, MysterAddress addressIn) {
        int port = extractPort(serverStats);
        
        var address = extractCorrectedAddress(serverStats, addressIn);
        
        identityProvider.addIdentity(identity, address);
        
        server.timeoflastupdate = System.currentTimeMillis();
        String temp = serverStats.getString(ServerStats.SPEED).orElse("1");
        server.speed = Double.valueOf(temp).doubleValue();

        server.serverName = serverStats.getString(ServerStats.SERVER_NAME).orElse(null);

        server.uptime = serverStats.getLong(ServerStats.UPTIME).orElse(-1L);

        NumOfFiles table = new NumOfFiles();
        List<String> dirList = serverStats.list(ServerStats.NUMBER_OF_FILES);

        try {
            if (dirList != null) {
                for (int i = 0; i < dirList.size(); i++) {
                    Optional<Integer> s_temp =
                            serverStats.getInt(ServerStats.NUMBER_OF_FILES  + dirList.get(i));
                    if (s_temp.isEmpty())
                        continue; // <- weird err.

                    table.put("/" + dirList.get(i), s_temp.get() + ""); // this is stupid but meh
                }
            }
        } finally {
            server.numberOfFiles = table;
        }
        
        var addresses = server.identityProvider.getAddresses(server.identity);
        
        for (MysterAddress mysterAddress : addresses) {
            if (mysterAddress.getPort() != port) {
                log.info("Deleting this address: " + mysterAddress);
                server.identityProvider.removeIdentity(server.identity, mysterAddress);
            }
        }
        
        identityProvider.cleanUpOldAddresses(identity);
        
        server.save();
    }

    private static int extractPort(MessagePak serverStats) {
        return serverStats.getInt(ServerStats.PORT).orElse(MysterGlobals.DEFAULT_SERVER_PORT);
    }

    static Optional<MysterIdentity> extractIdentity(Preferences serverPrefs, String md5HashOfIdentity) {
        var publicKeyAsString =
                serverPrefs.get(MysterServerImplementation.IDENTITY_PUBLIC_KEY, null);
        if (publicKeyAsString != null) {
            var identityPublicKey = com.myster.identity.Util.publicKeyFromString(publicKeyAsString);
            if (identityPublicKey.isEmpty()) {
                log.warning("identityPublicKey in the prefs seem to be corrupt: " + publicKeyAsString);
                
                return Optional.empty(); // sigh corruption
            }

            if (!Util.getMD5Hash(publicKeyAsString).equals(md5HashOfIdentity)) {
                log.warning("The md5 of the identity in the prefs and the identity in the server don't match. pref key:"
                        + md5HashOfIdentity + " vs in server structure: " + identityPublicKey);

                return Optional.empty();
            }
            
            return identityPublicKey.<MysterIdentity> map(PublicKeyIdentity::new);
        }
        
        String concatAddresses = serverPrefs.get(MysterServerImplementation.ADDRESSES, null);
        if (concatAddresses == null) {
            return Optional.empty();
        }

        String[] addresses = concatAddresses.split(" ");

        if (addresses.length > 0) {
            try {
                return Optional.of(new MysterAddressIdentity(MysterAddress
                        .createMysterAddress(addresses[0])));
            } catch (UnknownHostException ex) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
    
    private void createNewMysterServerImpl(double s,
                                           int tu,
                                           int td,
                                           int h,
                                           long t,
                                           String nof,
                                           String si,
                                           long u) {
        speed = s;
        timeup = tu;
        timedown = td;
        numberofhits = h;
        timeoflastupdate = t;
        uptime = u;
        
        try {
            numberOfFiles = new NumOfFiles(nof);//!
        } catch (MMLException ex) {
            numberOfFiles = new NumOfFiles();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            numberOfFiles = new NumOfFiles();
        }
        
        serverName = si;
    }

    MysterServer getInterface() {
        return new MysterServerReference();
    }

    public MysterIdentity getIdentity() {
        return identity;
    }

    public boolean getStatus() {
        return getBestAddress().map(a -> identityProvider.isUp(a)).orElse(false);
    }
    
    public Optional<MysterAddress> getBestAddress() {
        return identityProvider.getBestAddress(identity);
    }

    @Override
    public String toString() {
        return "" + toMML();
    }

    public Optional<MysterAddress> getAddress() {
        return identityProvider.getBestAddress(identity);
    }
    
    public MysterAddress[] getAddresses() {
        return identityProvider.getAddresses(identity);
    }

    @Override
    public boolean equals(Object m) {
        if (m == null) {
            return false;
        }

        if (!m.getClass().equals(MysterServerImplementation.class)) {
            return false;
        }

        MysterServerImplementation mysterIp;
        try {
            mysterIp = (MysterServerImplementation) m;
        } catch (ClassCastException ex) {
            return false;
        }

        return getIdentity().equals(mysterIp.getIdentity());
    }
    
    
    // I don't think we use the hashCode impl
    @Override
    public int hashCode() {
        return getIdentity().hashCode();
    }
    
    /**
     * This private class implements the com.myster interface and allows outside
     * objects to get vital server statistics.
     */
    private class MysterServerReference implements MysterServer {
        @Override
        public boolean getStatus() {
            return MysterServerImplementation.this.getStatus();
        }

        @Override
        public Optional<MysterAddress> getBestAddress() {
            return MysterServerImplementation.this.getBestAddress();
        }

        @Override
        public MysterAddress[] getAddresses() {
            return MysterServerImplementation.this.getAddresses();
        }

        @Override
        public MysterAddress[] getUpAddresses() {
            MysterAddress[] addresses = identityProvider.getAddresses(identity);

            return Util.filter(Arrays.asList(addresses), a -> identityProvider.isUp(a))
                    .toArray(MysterAddress[]::new);
        }

        /**
         * Returns the Number of Files associated with this MysterIP object.
         */
        @Override
        public int getNumberOfFiles(MysterType type) {
            return MysterServerImplementation.this.getNumberOfFiles(type);
        }
        
        @Override
        public boolean knowsAboutType(MysterType type) {
            return MysterServerImplementation.this.knowsAboutType(type);
        }

        @Override
        public int getTotalNumberOfFiles() {
            return 0;
        }

        /**
         * Returns the Transfer Speed associated with this MysterIP object.
         */
        @Override
        public double getSpeed() {
            return speed;
        }

        /**
         * Ranks self for "goodness" and returns the result. The Rank is for Comparison to other
         * Myster IP objects.
         */
        @Override
        public double getRank(MysterType type) {
            int pingTime = getPingTime();
            return (SPEEDCONSTANT * Math.log(speed) //
                    + FILESCONSTANT * Math.log(getNumberOfFiles(type)) //
                    + Math.log(HITSCONSTANT + 1) * numberofhits //
//                    + UPVSDOWNCONSTANT * ((double) timeup / (double) (timeup + timedown)) // up
                    + STATUSCONSTANT * (pingTime >= UNTRIED ? 4 : 0)) //
                    + (pingTime == DOWN ? (0.1
                            - (double) 20000 / 2500)
                            : (pingTime == UNTRIED ? (0.1 - (double) 5000 / 2500)
                                    : (0.1 - (double) pingTime / 2500)));
        }
        
        @Override
        public MysterIdentity getIdentity() {
            return identity;
        }

        @Override
        public String getServerName() {
            return (serverName == null ? "Unnamed" : serverName);//(serverIdentity.length()>31?serverIdentity.substring(0,31):serverIdentity));
        }

        @Override
        public int getPingTime() {
            return getBestAddress().map(a -> identityProvider.getPing(a)).orElse(UNTRIED);
        }

        @Override
        public String toString() {
            return MysterServerImplementation.this.toString();
        }

        @Override
        public boolean isUntried() {
            return (getPingTime() == UNTRIED);
        }

        @Override
        public long getUptime() {
            return uptime;
        }

        @Override
        public ExternalName getExternalName() {
            return computeNodeNameFromIdentity(identity);
        }

        @Override
        public void tryPingAgain(MysterAddress address) {
            if (isUntried() || getStatus()) {
                return; // no need to ping again if we haven't tried or if we are up
            }
            
            identityProvider.repingNow(address);
        }
    }

    void save() {
        preferences.put(SPEED, "" + speed);
        preferences.put(TIMESINCEUPDATE, "" + timeoflastupdate);
        preferences.put(TIMEUP, "" + timeup);
        preferences.put(TIMEDOWN, "" + timedown);
        preferences.put(NUMBEROFHITS, "" + numberofhits);
        preferences.put(UPTIME, "" + uptime);
        
        if (serverName != null && !serverName.isEmpty()) {
            preferences.put(SERVER_NAME, "" + serverName);
        }

        String s_temp = numberOfFiles.toString();
        if (!s_temp.isEmpty()) {
            preferences.put(NUMBEROFFILES, numberOfFiles.toString());
        }
        
        if (identity instanceof PublicKeyIdentity i) {
            preferences.put(IDENTITY_PUBLIC_KEY, i.toString());
        }

        MysterAddress[] addresses = identityProvider.getAddresses(identity);
        String concatAddresses =
                String.join(" ",
                            Util.map(Arrays.asList(addresses), (MysterAddress a) -> a.toString())
                                    .toArray(String[]::new));
        
        preferences.put(ADDRESSES, concatAddresses);
    }

    /** For debugging */
    private MML toMML() {
        try {
            MML workingmml = new MML();

            workingmml.put("/"+SPEED, "" + speed);
            workingmml.put("/"+TIMESINCEUPDATE, "" + timeoflastupdate);
            workingmml.put("/"+TIMEUP, "" + timeup);
            workingmml.put("/"+TIMEDOWN, "" + timedown);
            workingmml.put("/"+NUMBEROFHITS, "" + numberofhits);
            workingmml.put("/"+UPTIME, "" + uptime);
            
            if (identity != null) {
                workingmml.put("/"+IDENTITY_PUBLIC_KEY, ADDRESSES);
            }
            
            if (serverName != null && !serverName.isEmpty())
                workingmml.put("/"+SERVER_NAME, "" + serverName);

            String s_temp = numberOfFiles.toString();
            if (!s_temp.isEmpty())
                workingmml.put("/"+NUMBEROFFILES, numberOfFiles.toString());

            return workingmml;
        } catch (Exception ex) {
            log.info("Exception while trying to go from MysterServer to MML " + ex);
            ex.printStackTrace();
        }
        return null; //NEVER HAPPENS!
    }

    private int getNumberOfFiles(MysterType type) {
        return numberOfFiles.getNumberOfFiles(type);
    }

    private boolean knowsAboutType(MysterType type) {
        return numberOfFiles.knowsAboutType(type);
    }

    private static class NumOfFiles extends RobustMML {
        public NumOfFiles(String s) throws MMLException {
            super(s);
        }

        public NumOfFiles() {
            super();
        }

        public int getNumberOfFiles(MysterType type) {
            try {
                return Integer.parseInt(get("/" + type));
            } catch (NumberFormatException ex) {
                return 0;
            } catch (NullPointerException ex) {
                return 0;
            } catch (Exception ex) {
                log.warning("Unexcepted Error occured");
                ex.printStackTrace();
                return 0;
            }
        }

        /**
         * Checks if the server has reported this type.
         * Returns true if the type key exists in the MML structure, even if the value is 0.
         * Returns false if the type key doesn't exist (server doesn't know about this type).
         */
        public boolean knowsAboutType(MysterType type) {
            try {
                return get("/" + type) != null;
            } catch (Exception ex) {
                return false;
            }
        }
    }
}