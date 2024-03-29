/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001

 */

package com.myster.tracker;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import com.general.thread.CallAdapter;
import com.general.util.BlockingQueue;
import com.myster.client.datagram.PingResponse;
import com.myster.client.net.MysterProtocol;
import com.myster.client.net.MysterStream;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;

/**
 * MysterIP objects are responsible for two things. 1) Saving server statistics information and 2)
 * keeping this information up to date.
 */

class MysterServerImplementation {
    private static final Logger LOGGER = Logger.getLogger("com.myster.tracker.MysterServer");
    
    private MysterAddress ip;
    private double speed;
    private int timeup;
    private int timedown;
    private NumOfFiles numberOfFiles;
    private int numberofhits;
    private boolean upordown = true;
    private String serverIdentity;
    private long uptime;
    private int lastPingTime = -1; //in millis. (not saved)

    private volatile boolean occupied = false; //used in updating...

    private long lastminiupdate = 0; //This is to keep the value of the last

    // time internalRefreshStatus() was last
    // called.

    private long timeoflastupdate = 0;

    //These are the paths in the MML peer:
    //ip = root!

    public static final String IP = "/ip_address";
    public static final String SPEED = "/speed";
    public static final String TIMESINCEUPDATE = "/timeSinceUpdate";
    public static final String TIMEUP = "/timeUp";
    public static final String TIMEDOWN = "/timeDown";
    public static final String NUMBEROFHITS = "/numberOfHits";
    public static final String NUMBEROFFILES = "/numberOfFiles";
    public static final String SERVERIDENTITY = "/serverIdentity";
    public static final String UPTIME = "/uptime";

    //These are weights.
    private static final double SPEEDCONSTANT = 0.5;
    private static final double FILESCONSTANT = 0.5;
    private static final double HITSCONSTANT = 0.25;
    private static final double UPVSDOWNCONSTANT = 5;
    private static final double STATUSCONSTANT = 1;

    private final MysterProtocol protocol;
    private final Preferences preferences;

    private static final long UPDATETIME_MS = 1000 * 60 * 60;

    private static final long MINI_UPDATE_TIME_MS = 10 * 60 * 1000;
    private static final int NUMBER_OF_UPDATER_THREADS = 1;

    MysterServerImplementation(Preferences node, String ip, MysterProtocol protocol) throws IOException {
        this.preferences = node;
        if (ip.equals("127.0.0.1"))
            throw new IOException("IP is local host.");
        
        this.protocol = protocol;
        new MysterAddress(ip); // to see if address is valid.
        createNewMysterIP(ip, 1, 50, 50, 1, 1, "", null, -1);
        if (!MysterServerImplementation.internalRefreshAll(protocol, this))
            throw new IOException("Failed to created new Myster IP");
        LOGGER.fine("A New MysterIP Object = "+getAddress());
    }

     MysterServerImplementation(Preferences node, MysterProtocol protocol) {
         this.preferences = node;
        this.protocol = protocol;
        createNewMysterIP(node.get(IP, ""),
                          Double.valueOf(node.get(SPEED, "")).doubleValue(),
                          Integer.valueOf(node.get(TIMEUP, "")).intValue(),
                          Integer.valueOf(node.get(TIMEDOWN, "")).intValue(),
                          Integer.valueOf(node.get(NUMBEROFHITS, "")).intValue(),
                          Long.valueOf(node.get(TIMESINCEUPDATE, "")).longValue(),
                          node.get(NUMBEROFFILES, ""),
                          node.get(SERVERIDENTITY, ""),
                          (node.get(UPTIME, "") == null ? -1
                                  : Long.valueOf(node.get(UPTIME, "")).longValue()));
    }

    private void createNewMysterIP(String i, double s, int tu, int td, int h, long t, String nof,
            String si, long u) {
        try {
            ip = new MysterAddress(i); //!
        } catch (UnknownHostException ex) {
            ip = null; //ho ho... this might be s source of errors later..
        }
        upordown = true;
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
            LOGGER.info("IP: has no num of files " + ip);
        }

        serverIdentity = si;

        toUpdateOrNotToUpdate();
    }

    MysterServer getInterface() {
        return new MysterServerReference();
    }

    private final AtomicInteger referenceCounter = new AtomicInteger(0);
    

    /**
     * This private class implements the com.myster interface and allows outside objects to get
     * vital server statistics.
     */
    private class MysterServerReference implements MysterServer {
        private static final Cleaner cleaner = Cleaner.create();
        
        public MysterServerReference() {
            referenceCounter.incrementAndGet(); //used for garbage collection.
            cleaner.register(this, () -> referenceCounter.decrementAndGet());
        }

        public boolean getStatus() {
            return MysterServerImplementation.this.getStatus();
        }

        public boolean getStatusPassive() {
            return upordown;
        }

        public MysterAddress getAddress() {
            return MysterServerImplementation.this.ip;
        }

        /**
         * Returns the Number of Files associated with this MysterIP object.
         */

        public int getNumberOfFiles(MysterType type) {
            return MysterServerImplementation.this.getNumberOfFiles(type);
        }

        /**
         * Returns the Transfer Speed associated with this MysterIP object.
         */

        public double getSpeed() {
            toUpdateOrNotToUpdate();
            return speed;
        }

        /**
         * Ranks self for "goodness" and returns the result. The Rank is for Comparison to other
         * Myster IP objects.
         */
        public double getRank(MysterType type) {
            toUpdateOrNotToUpdate();
            return (SPEEDCONSTANT * Math.log(speed) //
                    + FILESCONSTANT * Math.log(getNumberOfFiles(type)) //
                    + Math.log(HITSCONSTANT + 1) * numberofhits //
                    + UPVSDOWNCONSTANT * ((double) timeup / (double) (timeup + timedown)) //up
                    + STATUSCONSTANT * (upordown ? 4 : 0)) //
                    + (lastPingTime == -2 ? (0.1 - (double) 20000 / 2500)
                            : (lastPingTime == -1 ? (0.1 - (double) 5000 / 2500)
                                    : (0.1 - (double) lastPingTime / 2500)));
        }

        public String getServerIdentity() {
            return (serverIdentity == null ? "Unnamed" : serverIdentity);//(serverIdentity.length()>31?serverIdentity.substring(0,31):serverIdentity));
        }

        public int getPingTime() {
            return lastPingTime;
        }

        public String toString() {
            return MysterServerImplementation.this.toString();
        }

        public boolean isUntried() {
            return (lastPingTime == -1);
        }

        public long getUptime() {
            return uptime;
        }
    }

    public boolean getStatus() {
        toUpdateOrNotToUpdate();
        return upordown;
    }

    @Override
    public String toString() {
        toUpdateOrNotToUpdate();

        return "" + toMML();
    }

    public MysterAddress getAddress() {
        return ip;
    }

    protected int getMysterCount() {
        return referenceCounter.get();
    }

    @Override
    public boolean equals(Object m) {
        MysterServerImplementation mysterIp;
        try {
            mysterIp = (MysterServerImplementation) m;
        } catch (ClassCastException ex) {
            return false;
        }

        return ip.equals(mysterIp.ip);
    }
    
    
    // I don't think we use the hashCode impl
    @Override
    public int hashCode() {
        return ip.hashCode();
    }

    void save() {
        preferences.put(IP, "" + ip.toString());
        preferences.put(SPEED, "" + speed);
        preferences.put(TIMESINCEUPDATE, "" + timeoflastupdate);
        preferences.put(TIMEUP, "" + timeup);
        preferences.put(TIMEDOWN, "" + timedown);
        preferences.put(NUMBEROFHITS, "" + numberofhits);
        preferences.put(UPTIME, "" + uptime);
        if (serverIdentity != null && !serverIdentity.equals("")) {
            preferences.put(SERVERIDENTITY, "" + serverIdentity);
        }

        String s_temp = numberOfFiles.toString();
        if (!s_temp.equals("")) {
            preferences.put(NUMBEROFFILES, numberOfFiles.toString());
        }
    }

    
    /** For debugging */
    private MML toMML() {
        try {
            MML workingmml = new MML();

            // Build MML object!
            workingmml.put(IP, "" + ip.toString());
            workingmml.put(SPEED, "" + speed);
            workingmml.put(TIMESINCEUPDATE, "" + timeoflastupdate);
            workingmml.put(TIMEUP, "" + timeup);
            workingmml.put(TIMEDOWN, "" + timedown);
            workingmml.put(NUMBEROFHITS, "" + numberofhits);
            workingmml.put(UPTIME, "" + uptime);
            if (serverIdentity != null && !serverIdentity.equals(""))
                workingmml.put(SERVERIDENTITY, "" + serverIdentity);

            String s_temp = numberOfFiles.toString();
            if (!s_temp.equals(""))
                workingmml.put(NUMBEROFFILES, numberOfFiles.toString());

            return workingmml;
        } catch (Exception ex) {
            LOGGER.info("Exception while trying to go from MysterServer to MML " + ex);
            ex.printStackTrace();
        }
        return null; //NEVER HAPPENS!
    }

    private void setStatus(boolean b) {
        if (b!=upordown) LOGGER.fine(ip+" is now "+(b?"up":"down"));
        upordown = b;
        if (b)
            timeup++;
        else
            timedown++;
    }

    private int getNumberOfFiles(MysterType type) {
        toUpdateOrNotToUpdate();
        return numberOfFiles.getNumberOfFiles(type);
    }

    //START OF UPDATER SUB SYSTEM:
    //NOTICE HOW MOST EVERYTHING IS STATIC.

    /**
     * Refreshes Status non-blocking. Status is whether this IP is up or down This function doesn't
     * block which means that it won't stop your program from excecuting while it looks up the
     * Status. It also means that there is some delay before the stats are updated.
     */

    private static MysterThread[] updaterThreads;

    private static BlockingQueue<MysterServerImplementation> statusQueue = new BlockingQueue<>();

    private synchronized void toUpdateOrNotToUpdate() {
        //if an update operation is already queued, return.
        if (occupied)
            return;

        if (System.currentTimeMillis()
                - lastminiupdate < (getMysterCount() > 0 ? MINI_UPDATE_TIME_MS : UPDATETIME_MS)) {
            return;
        }

        occupied = true; //note, there is an update in progress on this
        // MysterIP

        //Make sure all threads and related crap are loaded and running.
        assertUpdaterThreads();

        // Add this myster IP object to the ones to be updated.
        protocol.getDatagram().ping(this.getAddress()).addCallListener(new MysterIpPingEventListener());
    }

    /**
     * Refreshes all Stats (Number of files, Speed and upordown) from the IP.
     * Blocks.
     */
    private static boolean internalRefreshAll(MysterProtocol  protocol, MysterServerImplementation mysterip) {
        //Do Status updated
        //Note, routine checks to see if it's required.
        //MysterIP.internalRefreshStatus(mysterip);

        long time = System.currentTimeMillis();
        
        //Do Statistics update
        try {
            //check if the update is needed.
            if (System.currentTimeMillis() - mysterip.timeoflastupdate < UPDATETIME_MS)
                throw new MinorProblemException("Too soon after last update");

            if (!(mysterip.upordown))
                throw new MinorProblemException("Can't update. Server is not up");

            mysterip.timeoflastupdate = System.currentTimeMillis();
            LOGGER.fine("Getting stats from: "+mysterip.ip);

            MysterStream m = protocol.getStream();
            RobustMML mml = m.doSection(mysterip.getAddress(), m::getServerStats);
            if (mml == null)
                throw new MinorProblemException("Can't get server stats for this server " + mysterip);
            LOGGER.fine("MML for "+mysterip.ip.toString()+ " is "+mml.toString());
            String temp = mml.get("/Speed");
            if (temp == null)
                temp = "1";
            mysterip.speed = Double.valueOf(temp).doubleValue();

            if (mml.pathExists("/ServerName")) {
                mysterip.serverIdentity = mml.get("/ServerName");
            } else {
                mysterip.serverIdentity = null;
            }

            try {
                String uptimeString = mml.get(UPTIME);
                if (uptimeString == null) {
                    mysterip.uptime = -1;
                } else {
                    mysterip.uptime = Long.valueOf(uptimeString).longValue();
                }
            } catch (NumberFormatException ex) {
                // ignore and keep going
            }

            synchronized (mysterip) {
                NumOfFiles table = new NumOfFiles();
                List<String> dirList = mml.list("/numberOfFiles/");

                try {
                    if (dirList != null) {
                        for (int i = 0; i < dirList.size(); i++) {
                            String s_temp = mml.get("/numberOfFiles/"
                                    + dirList.get(i));
                            if (s_temp == null)
                                continue; //<- weird err.

                            table.put("/" + dirList.get(i), s_temp);//<-WARNING
                            // could
                            // be a
                            // number
                        }
                    }
                } finally {
                    mysterip.numberOfFiles = table;
                }
            }

            LOGGER.fine("The stats update of " + mysterip.ip + " took "
                    + (System.currentTimeMillis() - time) + "ms");
            return true;
        } catch (IOException ex) {
            LOGGER.info("MYSTERIP: Error in refresh fuction of MysterIP on IP: " + mysterip.ip
                    + "  " + ex);
            //ex.printStackTrace();
        } catch (MinorProblemException ex) {
            LOGGER.fine("Some sort of problem stopped "+mysterip.ip+" from being refreshed." + ex);
        } catch (Exception ex) {
            LOGGER.warning("Unexpected error occured in internal refresh all " + ex);
            ex.printStackTrace();
        } finally {
            mysterip.occupied = false; //we're done.
            mysterip.save();
        }
        
        
        return false;
    }

    private void assertUpdaterThreads() {
        if (MysterServerImplementation.updaterThreads == null) { //init threads
            MysterServerImplementation.updaterThreads = new MysterThread[NUMBER_OF_UPDATER_THREADS];
            for (int i = 0; i < updaterThreads.length; i++) {
                MysterServerImplementation.updaterThreads[i] = new IPStatusUpdaterThread(protocol);
                MysterServerImplementation.updaterThreads[i].start();
            }
        }
    }

    private static class IPStatusUpdaterThread extends MysterThread {
        private final MysterProtocol protocol;
        
        public IPStatusUpdaterThread(MysterProtocol protocol) {
            super("IPStatusUpdaterThread");
            
            this.protocol = protocol;
        }

        public void run() {
            try {
                for (;;) {
                    MysterServerImplementation.internalRefreshAll(protocol, MysterServerImplementation.statusQueue.get());
                }
            } catch (InterruptedException ex) {
                //nothing.
            }
        }

        public void end() {
            throw new RuntimeException( "This function is not implemented" );
        }
    }

    private static class MinorProblemException extends Exception {
        public MinorProblemException(String s) {
            super(s);
        }
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
                LOGGER.warning("Unexcepted Error occured");
                ex.printStackTrace();
                return 0;
            }
        }
    }

    private class MysterIpPingEventListener extends CallAdapter<PingResponse> {
        public void handleResult(PingResponse pingResponse) {
            if (pingResponse.isTimeout()) {
                setStatus(false);
                lastPingTime = -2;
                occupied = false;
            } else {
                setStatus(true);
                lastPingTime = pingResponse.pingTimeMs();
                statusQueue.add(MysterServerImplementation.this); // doesn't block...
            }
            lastminiupdate = System.currentTimeMillis();
        }
    }
}