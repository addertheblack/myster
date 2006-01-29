/* 

 Title:			Myster Open Source
 Author:			Andrew Trumper
 Description:	Generic Myster Code
 
 This code is under GPL

 Copyright Andrew Trumper 2000-2001
 */

/**
 * The IP list is a list of com.myster objects. The idea behind it is that the data type ie: Tree or
 * linked list of array can be changed without affecting the rest of the program.
 * 
 * 
 *  
 */

package com.myster.tracker;

import java.util.StringTokenizer;
import java.util.Vector;

import com.myster.net.MysterAddress;
import com.myster.pref.Preferences;
import com.myster.type.MysterType;

class IPList {
    private MysterServer[] array = new MysterServer[IPListManager.LISTSIZE];

    private MysterType type;

    private String mypath;

    private static final String PATH = "/IPLists/";

    /**
     * Takes as an argument a list of strings.. These strings are the .toString() product of
     * com.myster objects.
     */
    protected IPList(MysterType type) {
        String list[];
        mypath = PATH + type;

        if (!Preferences.getInstance().containsKey(mypath)) {
            System.out.println("Making new IP list entry.");
            Preferences.getInstance().put(mypath, " ");
        }
        String s = Preferences.getInstance().get(mypath);
        StringTokenizer ips = new StringTokenizer(s);
        int max = ips.countTokens();
        int j = 0;
        for (int i = 0; i < max; i++) {
            try {
                MysterServer temp = null;
                String workingip = ips.nextToken();
                if (MysterIPPool.getInstance().existsInPool(new MysterAddress(workingip))) {
                    try {
                        temp = MysterIPPool.getInstance().getMysterServer(
                                new MysterAddress(workingip));
                    } catch (Exception ex) {
                    }
                }//if IP doens't exist in the pool, remove it from the list!
                if (temp == null) {
                    System.out.println("Found a list bubble: " + workingip + ". Repairing.");
                    continue;
                }

                array[j] = temp;
                j++;
            } catch (Exception ex) {
                System.out.println("Failed to add an IP to an IP list: " + type);
            }
        }

        this.type = type;
        sort();
        removeCrap();
    }

    /**
     * Returns a String array of length the requested number of entries. Note: It's possible for the
     * list to have fewer entries than requested.. IN that case the rest of the array will be null.
     * 
     * This function will not return any items from the list that aren't currently "up" ie: That
     * cannot be connected to because they are down or the user isn't connected to the internet.
     */
    public synchronized MysterServer[] getTop(int x) {
        MysterServer[] temp = new MysterServer[x];

        //save();
        //io.writeIPList(getAsArray());

        int counter = 0;
        for (int i = 0; i < array.length && counter < x && array[i] != null; i++) {
            if (array[i].getStatus() && (!array[i].isUntried())) {
                temp[counter] = array[i];
                counter++;
            }
        }
        return temp;
    }

    /**
     * Returns vector of MysterAddress.
     */
    public synchronized Vector getAll() {
        Vector list = new Vector(IPListManager.LISTSIZE);
        for (int i = 0; i < array.length && array[i] != null; i++) {
            list.addElement(array[i]);
        }
        return list;
    }

    /**
     * This function adds an IP to the IP List.
     */
    protected synchronized void addIP(MysterServer ip) {
        insertionSort(ip);
    }

    public MysterType getType() {
        return type;
    }

    private synchronized void swap(MysterServer[] array, int i, int j) {
        MysterServer temp;
        temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private synchronized void removeCrap() {
        for (int i = 0; i < array.length; i++) {
            for (int j = i + 1; j < array.length && array[j] != null; j++) {
                if (array[i].getAddress().equals(array[j].getAddress()))
                    removeItem(j);
            }
        }

        for (int i = 0; i < array.length && array[i] != null; i++) {
            if (array[i].getAddress().getIP().equals("127.0.0.1"))
                removeItem(i);
        }

    }

    private synchronized void removeItem(int index) {
        for (int i = index + 1; i < array.length; i++) {
            swap(array, i - 1, i);
        }
        array[array.length - 1] = null;
    }

    /**
     * Modifies the preferences and saves the changes.
     */
    private synchronized void save() {
        removeCrap();

        StringBuffer buffer = new StringBuffer(40000); //Give lots of space!
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null)
                break;
            buffer.append("" + array[i].getAddress() + " ");
        }

        Preferences.getInstance().put(mypath, buffer.toString());
    }

    /**
     * insertionSort adds an IP to the list.. the list currently uses an array and insert into the
     * list using insertion sort. It also checks to make sure the same place isn't put in twice.
     */
    private synchronized void insertionSort(MysterServer ip) {
        if (ip == null)
            return;

        //System.out.println("Asking list "+type+" if it would like ip
        // "+ip.getName());
        //LOOKS FOR A FREE SPACE IN THE LIST AND INSERTS IN THE LIST IF IT
        // FINDS ONE...
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
                array[i] = ip;
                //System.out.println("Adding com.myster "+ip.getName()+" to
                // list "+type);
                sort(); //ya gotta sort...
                save(); //Saves the new IP.
                return;
            }
            if (array[i].getAddress().equals(ip.getAddress())) {
                //System.out.println("List "+type+" already contains
                // "+ip.getName());
                return;
            }
        }

        //THIS CODE IS KICK ASS.. IT ONLY SORTS IF THE RANK OF THE LAST ITEM IS
        // LESS THAN IT'S RANK!
        //System.out.println("Old IP: "+array[array.length-1].getRank(type)+"
        // vs new IP: "+ip.getRank(type));
        //System.out.println("Old IP: "+array[array.length-1].getIP()+" vs new
        // IP: "+ip.getIP());
        if (array[array.length - 1].getRank(type) < ip.getRank(type)) {
            //System.out.println("Adding com.myster "+ip.getAddress()+" to full
            // list "+type);
            array[array.length - 1] = ip;
            sort();
            save(); //Saves the new IP.
            return;
        }
        //System.out.println("List "+type+" refused "+ip.getName()+" ranked
        // "+(ip.getRank(type)*100));
    }

    private synchronized void sort() {
        for (int i = 1; i < array.length; i++) {
            if (array[i] == null)
                break;
            for (int j = i; j > 0 && (array[j].getRank(type) > array[j - 1].getRank(type)); j--) {
                swap(array, j, j - 1);
            }
        }
    }

}