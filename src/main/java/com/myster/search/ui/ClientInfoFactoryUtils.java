package com.myster.search.ui;

import com.myster.type.MysterType;
import com.myster.type.StandardTypes;
import com.myster.type.TypeDescriptionList;

public final class ClientInfoFactoryUtils {
    private ClientInfoFactoryUtils() {
    }


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
        if (isStandardType(tdList, type, StandardTypes.MPG3))
            return new ClientMPG3HandleObject();
        else if (isStandardType(tdList, type, StandardTypes.PICT))
            return new ClientImageHandleObject();
        else
            return new ClientGenericHandleObject();
    }

    private static boolean isStandardType(TypeDescriptionList tdList,
                                          MysterType type,
                                          StandardTypes standardType) {
        try {
            return tdList.getType(standardType).equals(type);
        } catch (IllegalStateException ex) {
            return false;
        }
    }

}
