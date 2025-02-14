package com.myster.search.ui;

import com.myster.type.MysterType;
import com.myster.type.StandardTypes;
import com.myster.type.TypeDescriptionList;

public class ClientInfoFactoryUtilities {

    public static ClientHandleObject getHandler(TypeDescriptionList tdList, MysterType type) {
        if (tdList.getType(StandardTypes.MPG3).equals(type))
            return new ClientMPG3HandleObject();
        else
            return new ClientGenericHandleObject();
    }

}