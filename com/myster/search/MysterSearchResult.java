package com.myster.search;

import java.util.Vector;

import com.myster.mml.RobustMML;
import com.myster.net.MysterAddress;

public class MysterSearchResult implements SearchResult {
    RobustMML mml;

    MysterFileStub stub;

    public MysterSearchResult(MysterFileStub stub) {
        this.stub = stub;
    }

    public void setMML(RobustMML m) {
        mml = m;
    }

    //is called when the user decides to download the item
    public void download() {
        com.myster.client.stream.StandardSuite.downloadFile(stub
                .getMysterAddress(), stub);
    }

    //returns the network the search result is on.
    public String getNetwork() {
        return "Myster Network";
    }

    //gets a value for a meta data thingy
    public String getMetaData(String key) {
        return (mml == null ? null : mml.get(key));
    }

    //gets the list of known meta data types for this item.
    public String[] getKeyList() {
        if (mml == null)
            return new String[] {};

        Vector items = mml.list("/");

        Vector v_temp = new Vector(items.size());

        for (int i = 0; i < items.size(); i++) {
            String s_temp = (String) (items.elementAt(i));
            if (mml.isAFile("/" + s_temp)) {
                v_temp.addElement("/" + s_temp);
            }
        }

        String[] sa_temp = new String[v_temp.size()];

        for (int i = 0; i < v_temp.size(); i++) {
            sa_temp[i] = (String) (v_temp.elementAt(i));
        }

        return sa_temp;
    }

    //gets the Name of the search result (usualy a file name!)
    public String getName() {
        return stub.getName();
    }

    //gets the host address
    public MysterAddress getHostAddress() {
        return stub.getMysterAddress();
    }
}