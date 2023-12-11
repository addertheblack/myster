package com.myster.search.ui;

import com.myster.type.MysterType;

public class ClientInfoFactoryUtilities {

    public static ClientHandleObject getHandler(MysterType type) {
        if (type.toString().equals("MPG3"))
            return new ClientMPG3HandleObject();
        else
            return new ClientGenericHandleObject();
    }

}