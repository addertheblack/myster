/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001

 */

package com.myster.tracker;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Vector;

import com.general.util.BlockingQueue;
import com.myster.client.datagram.PingEvent;
import com.myster.client.datagram.PingEventListener;
import com.myster.client.datagram.UDPPingClient;
import com.myster.client.stream.StandardSuite;
import com.myster.mml.MML;
import com.myster.mml.MMLException;
import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;
import com.myster.type.MysterType;
import com.myster.util.MysterThread;

/**
 * MysteriP objects are responsible for two things. 1) Saving server statistics information and 2)
 * keeping this information upt to date.
 */

class MysterIP {
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
    private final double SPEEDCONSTANT = 0.5;

    private final double FILESCONSTANT = 0.5;

    private final double HITSCONSTANT = 0.25;

    private final double UPVSDOWNCONSTANT = 5;

    private final double STATUSCONSTANT = 1;

    private static final long UPDATETIME = 3600000;// 86400000==1 day,

    // 3600000==1 hour ;

    private static final long MINIUPDATETIME = 10 * 60 * 1000;

    private static final int NUMBER_OF_UPDATER_THREADS = 1;

    MysterIP(String ip) throws Exception {
        if (ip.equals("127.0.0.1"))
            throw new Exception("IP is local host.");
        MysterAddress t = new MysterAddress(ip); //to see if address is valid.
        createNewMysterIP(ip, 1, 50, 50, 1, 1, "", null, -1);
        if (!MysterIP.internalRefreshAll(this))
            throw new Exception("Failed to created new Myster IP");
        //System.out.println("A New MysterIP Object = "+getAddress());
    }

    MysterIP(MML mml) {
        createNewMysterIP(mml.get(IP), Double.valueOf(mml.get(SPEED)).doubleValue(), Integer
                .valueOf(mml.get(TIMEUP)).intValue(),
                Integer.valueOf(mml.get(TIMEDOWN)).intValue(), Integer.valueOf(
                        mml.get(NUMBEROFHITS)).intValue(), Long.valueOf(mml.get(TIMESINCEUPDATE))
                        .longValue(), mml.get(NUMBEROFFILES), mml.get(SERVERIDENTITY), (mml
                        .get(UPTIME) == null ? -1 : Long.valueOf(mml.get(UPTIME)).longValue()));
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
            System.out.println("IP: has no num of files " + ip);
        }

        serverIdentity = si;

        toUpdateOrNotToUpdate();
    }

    MysterServer getInterface() {
        return new MysterIPInterfaceClass();
    }

    private int referenceCounter = 0;

    /**
     * This private class implements the com.myster interface and allows outside objects to get
     * vital server statistics.
     */

    private class MysterIPInterfaceClass implements MysterServer {
        public MysterIPInterfaceClass() {
            referenceCounter++; //used for garbage collection.
        }

        public boolean getStatus() {
            return MysterIP.this.getStatus();
        }

        public boolean getStatusPassive() {
            return upordown;
        }

        public MysterAddress getAddress() {
            return MysterIP.this.ip;
        }

        /**
         * Returns the Number of Files associated with this MysterIP object.
         */

        public int getNumberOfFiles(MysterType type) {
            return MysterIP.this.getNumberOfFiles(type);
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

        protected void finalize() throws Throwable {
            referenceCounter--; //used for garbage collection.
            super.finalize();
        }

        public String toString() {
            return MysterIP.this.toString();
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

    /**
     * The ever famous toString() method!!! You can't go from string to new Myster IP easily!!!
     */

    public String toString() {
        toUpdateOrNotToUpdate();

        return "" + toMML();
    }

    public MysterAddress getAddress() {
        return ip;
    }

    protected int getMysterCount() {
        return referenceCounter;
    }

    protected long getLastUpdate() {
        return timeoflastupdate;
    }

    /**
     * Tests to see wherether the names (IPs) of two Myster Objects are equal..!
     *  
     */

    public boolean equals(Object m) {
        MysterIP mysterIp;
        try {
            mysterIp = (MysterIP) m;
        } catch (ClassCastException ex) {
            return false;
        }

        return ip.equals(mysterIp.ip);
    }

    protected MML toMML() {
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
            System.out.println("" + ex);
            ex.printStackTrace();
        }
        return null; //NEVER HAPPENS!
    }

    private void setStatus(boolean b) {
        //if (b!=upordown) System.out.println(ip+" is now "+(b?"up":"down"));
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

    private static BlockingQueue statusQueue = new BlockingQueue();

    private synchronized void toUpdateOrNotToUpdate() {
        //if an update operation is already queued, return.
        if (occupied)
            return;

        //if both stats and all stats don't need updaing return.

        if ((System.currentTimeMillis() - lastminiupdate < (getMysterCount() > 0 ? MINIUPDATETIME
                : UPDATETIME))
                && (System.currentTimeMillis() - timeoflastupdate < UPDATETIME)) {
            return;
        }

        occupied = true; //note, there is an update in progress on this
        // MysterIP

        //Make sure all threads and related crap are loaded and running.
        MysterIP.assertUpdaterThreads();

        //Add this myster IP object to the ones to be updated.
        try {
            UDPPingClient.ping(this.getAddress(), new MysterIPPingEventListener(this));
        } catch (IOException ex) {
            ex.printStackTrace();
            occupied = false; //very bad things happen if it gets to here!!!
        }
    }

    /**
     * Refreshes all Stats (Number of files, Speed and upordown) from the IP. Blocks.
     */
    private static boolean internalRefreshAll(MysterIP mysterip) {

        //Do Status updated
        //Note, routine checks to see if it's required.
        //MysterIP.internalRefreshStatus(mysterip);

        //Do Statistics update
        long time;

        try {
            //check if the update is needed.
            if (System.currentTimeMillis() - mysterip.timeoflastupdate < UPDATETIME)
                throw new MassiveProblemException("");

            if (!(mysterip.upordown))
                throw new MassiveProblemException("");

            mysterip.timeoflastupdate = System.currentTimeMillis();
            //System.out.println("Getting stats from: "+mysterip.ip);
            time = System.currentTimeMillis();

            MML mml = StandardSuite.getServerStats(mysterip.ip);
            if (mml == null)
                throw new MassiveProblemException("");
            //System.out.println("MML for "+mysterip.ip.toString()+ " is
            // "+mml.toString());
            String temp = mml.get("/Speed");
            if (temp == null)
                temp = "1";
            mysterip.speed = Double.valueOf(temp).doubleValue();

            if (mml.pathExists("/ServerIdentity")) {
                mysterip.serverIdentity = mml.get("/ServerIdentity");
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
                // Number was badly formated
            }

            synchronized (mysterip) {
                NumOfFiles table = new NumOfFiles();
                Vector dirList = mml.list("/numberOfFiles/");

                try {
                    if (dirList != null) {
                        for (int i = 0; i < dirList.size(); i++) {
                            String s_temp = mml.get("/numberOfFiles/"
                                    + (String) (dirList.elementAt(i)));
                            if (s_temp == null)
                                continue; //<- weird err.

                            table.put("/" + (String) (dirList.elementAt(i)), s_temp);//<-WARNING
                            // could
                            // be a
                            // number
                        }
                    }
                } finally {
                    mysterip.numberOfFiles = table;
                }
            }

            //System.out.println("The stats update of "+mysterip.ip+" took
            // "+(System.currentTimeMillis()-time)+"ms");
            return true;
        } catch (IOException ex) {
            System.out.println("MYSTERIP: Error in refresh fuction of MysterIP on IP: "
                    + mysterip.ip + "  " + ex);
            //ex.printStackTrace();
        } catch (MassiveProblemException ex) {
            //System.out.println("Some sort of problem stopped "+mysterip.ip+"
            // from being refreshed.");
        } catch (Exception ex) {
            System.out.println("Unexpected error occured in internal refresh all");
            ex.printStackTrace();
        } finally {
            mysterip.occupied = false; //we're done.
        }
        return false;
    }

    private synchronized static void assertUpdaterThreads() {
        if (MysterIP.updaterThreads == null) { //init threads
            MysterIP.updaterThreads = new MysterThread[NUMBER_OF_UPDATER_THREADS];
            for (int i = 0; i < updaterThreads.length; i++) {
                MysterIP.updaterThreads[i] = new IPStatusUpdaterThread();
                MysterIP.updaterThreads[i].start();
            }
        }
    }

    private static class IPStatusUpdaterThread extends MysterThread {

        public IPStatusUpdaterThread() {
            super("IPStatusUpdaterThread");
        }

        public void run() {
            try {
                for (;;)
                    MysterIP.internalRefreshAll((MysterIP) (MysterIP.statusQueue.get()));
            } catch (InterruptedException ex) {
                //nothing.
            }
        }
    }

    private static class MassiveProblemException extends Exception {
        public MassiveProblemException(String s) {
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
                System.out.println("UNexcepted Error occured");
                ex.printStackTrace();
                return 0;
            }
        }
    }

    private static class MysterIPPingEventListener extends PingEventListener {
        MysterIP ip;

        public MysterIPPingEventListener(MysterIP ip) {
            this.ip = ip;
        }

        public void pingReply(PingEvent e) {
            if (e.isTimeout()) {
                ip.setStatus(false);
                ip.lastPingTime = -2;
                ip.occupied = false;
            } else {
                ip.setStatus(true);
                ip.lastPingTime = e.getPingTime();
                statusQueue.add(ip); //doesn't block...
            }
            ip.lastminiupdate = System.currentTimeMillis();

        }
    }
}