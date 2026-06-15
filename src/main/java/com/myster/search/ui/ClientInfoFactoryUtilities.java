package com.myster.search.ui;

import com.myster.type.MysterType;
import com.myster.type.StandardTypes;
import com.myster.type.TypeDescriptionList;

public class ClientInfoFactoryUtilities {

    /**
     * Returns the per-type column handler for the given type.
     * The returned handler covers only type-specific columns ("File Name", "File Size", and any
     * type extras). Search-context columns ("Server", "Ping") are not included; wrap the result
     * in {@link SearchColumnDecorator} when configuring a search window MCList.
     *
     * @param tdList the type description list used to identify known types
     * @param type   the Myster type to look up
     * @return the appropriate {@link FileTypeColumnHandler}
     */
    public static FileTypeColumnHandler getHandler(TypeDescriptionList tdList, MysterType type) {
        if (tdList.getType(StandardTypes.MPG3).equals(type))
            return new ClientMPG3HandleObject();
        else
            return new ClientGenericHandleObject();
    }

}