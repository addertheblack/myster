package com.general.mclist;

import java.awt.Component;

/**
 */
public class MCListFactory {

    public static MCList buildMCList(int numberofcolumns, boolean singleselect, Component c) {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.1") || version.startsWith("1.0")) {
            return new AWTMCList( numberofcolumns, singleselect, c);
        } else {
            return new JMCList( numberofcolumns, singleselect);
        }
    }
}