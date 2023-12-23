
package com.myster.tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.myster.client.net.MysterProtocol;
import com.myster.net.MysterAddress;

public class MysterIPPoolImpl implements MysterIpPool {
    private static final int GC_UPPER_LIMIT = 100;

    // MysterIPPool stores all its ips
    private static final String PREF_NODE_NAME = "Tracker.MysterIPPool";

    private final Map<MysterAddress, MysterIP> cache;


    /**
     * Given a string address, it returns a com.myster object. com.myster
     * objects are like little statistics objects. You can get these objects and
     * use these objects from anywhere in the program thanks to the new garbage
     * collector based system.
     */
    private final DeadIPCache deadCache = new DeadIPCache();

    private final MysterProtocol protocol;

    private final Preferences preferences;


    public MysterIPPoolImpl(Preferences prefs, MysterProtocol mysterProtocol) {
        this.preferences = prefs.node(PREF_NODE_NAME);
        this.protocol = mysterProtocol;
        System.out.println("Loading IPPool.....");
        cache = new HashMap<>(); // You put cereal on the Hashtable. In a
                                 // bowl of course...
        
        String[] dirList;
        try {
            dirList = preferences.childrenNames();
            for (String nodeName : dirList) {
                MysterIP mysterip = new MysterIP(preferences.node(nodeName), protocol);
                cache.put(mysterip.getAddress(), mysterip);
            }
        } catch (BackingStoreException exception) {
            // ignore
        }

        System.out.println("Loaded IPPool");
    }


    public MysterServer getMysterServer(MysterAddress address) throws IOException {
        // Below is where the blacklisting code will eventually go.
        if (address.getIP().equals("") || address.getIP().equals("127.0.0.1")
                || address.getIP().equals("0.0.0.0")) {
            throw new IOException("Black listed internet address");
        }

        MysterServer temp = getCachedMysterIp(address);

        if (temp != null)
            return temp;

        if (deadCache.isDeadAddress(address))
            throw new IOException("IP is dead");

        try {
            // get the "other" name (dns) for that ip...
            return getMysterIPLevelTwo(new MysterIP(preferences.node(address.toString()),
                                                    address.toString(),
                                                    this.protocol));
        } catch (Exception ex) {
            deadCache.addDeadAddress(address);
            throw new IOException("Bad thing happened in MysterIP Pool add");
        }
    }

    public MysterServer getMysterServer(String name) throws IOException {
        return getMysterServer(new MysterAddress(name));
    }

    /**
     * In order to avoid having thread problems the two functions below are used.
     * They are required because the checking the index and getting the object
     * at that index should be atomic, hence the synchronized! and the two
     * functions (for two levels of checking
     */

    public synchronized MysterServer getCachedMysterIp(MysterAddress address) {
        MysterIP mysterip = getMysterIP(address);

        if (mysterip == null)
            return null;

        return mysterip.getInterface();
    }

    public boolean existsInPool(MysterAddress s) {
        return (getMysterIP(s) != null);
    }

    private synchronized MysterServer getMysterIPLevelTwo(MysterIP m) throws IOException {
        MysterAddress address = m.getAddress(); // possible future bugs here...
        if (existsInPool(address))
            return getCachedMysterIp(address);

        return addANewMysterObjectToThePool(m);
    }

    /**
     * this function adds a new IP to the MysterIPPool data structure.. It's
     * synchronized so it's thread safe.
     * 
     * The function double checks to make sure that there really hasen't been
     * another myster IP cached during the time it took to check and returns the
     * appropriate object.
     */

    private synchronized MysterServer addANewMysterObjectToThePool(MysterIP ip) {
        if (!existsInPool(ip.getAddress())) {
            MysterServer mysterServer = ip.getInterface();
            cache.put(ip.getAddress(), ip); // if deleteUseless went first,
                                            // the garbag collector would
                                            // get the ip we just added!
                                            // DOH!
            deleteUseless(); // Cleans up the pool, deletes useless MysterIP
                             // objects!
            ip.save();
            return mysterServer;
        } else {
            return getMysterIP(ip.getAddress()).getInterface();
        }
    }

    /**
     * This method can be invoked whenever the program feels it has too many
     * MysterIP objects. This method will only delete objects not being used by
     * the rest of the program.
     */

    private synchronized void deleteUseless() {
        if (cache.size() <= GC_UPPER_LIMIT)
            return;

        Iterator<MysterAddress> iterator = cache.keySet().iterator();
        List<MysterAddress> keysToDelete = new ArrayList<>();

        // Collect worthless....
        while (iterator.hasNext()) {
            MysterAddress workingKey = iterator.next();

            MysterIP mysterip = cache.get(workingKey);

            if ((mysterip.getMysterCount() <= 0) && (!mysterip.getStatus())) {
                keysToDelete.add(workingKey);
            }
        }

        // remove worthless...
        for (MysterAddress workingKey : keysToDelete) {
            cache.remove(workingKey); // weeee...
        }

        // brag about it...
        if (keysToDelete.size() >= 100) {
            System.out.println(keysToDelete.size()
                    + " useless MysterIP objects found. Cleaning up...");
            System.gc();
            System.out.println("Deleted " + keysToDelete.size()
                    + " useless MysterIP objects from the Myster pool.");
        }

        System.out.println("IPPool : Removed " + keysToDelete.size()
                + " object from the pool. There are now " + cache.size() + " objects in the pool");

        // signal that the changes should be saved asap...
        for (MysterAddress mysterAddress : keysToDelete) {
            try {
                preferences.node(mysterAddress.toString()).removeNode();
            } catch (BackingStoreException exception) {
                System.out.println("Could not delete MysterIP pref node for " + mysterAddress.toString());
            }
        }
    }

    private MysterIP getMysterIP(MysterAddress address) {
        return cache.get(address);
    }

//    /**
//     * Saves the state of the MysterIPPool.. Thanks to the new preferences
//     * manager, this routine can be called as often as I like.
//     */
//    private synchronized void save() {
//        Iterator<MysterIP> iterator = cache.values().iterator();
//
//        int i = 0;
//        while (iterator.hasNext()) {
//            MysterIP mysterip = (iterator.next());
//
//            if (mysterip.getMysterCount() > 0) {
//                mysterip.save(preferences.node("" + i));
//            }
//
//            i++;
//        }
//    }
}
